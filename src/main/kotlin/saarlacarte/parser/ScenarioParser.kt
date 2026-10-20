package saarlacarte.parser

import org.json.JSONArray
import org.json.JSONObject
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.StaffType
import saarlacarte.enums.TableType
import saarlacarte.food.Recipe
import saarlacarte.incidents.Incident
import saarlacarte.incidents.PackagingIncident
import saarlacarte.incidents.RecipeIncident
import saarlacarte.incidents.StaffIncident
import saarlacarte.incidents.UnavailableIncident
import saarlacarte.parser.SchemaCheck.fits
import saarlacarte.simulation.Simulation
import saarlacarte.simulation.Supplier
import java.io.File
import kotlin.math.ceil

private const val SCENARIO_SCHEMA = "classpath://schema/scenario.schema"
private const val CUSTOMER_SCHEMA = "classpath://schema/customerGroup.schema"
private const val INCIDENT_SCHEMA = "classpath://schema/incident.schema"

private const val KEY_ID = "id"
private const val KEY_TYPE = "type"
private const val KEY_SIZE = "size"
private const val KEY_INGREDIENT = "ingredient"

private const val TYPE_REGULAR = "REGULAR"
private const val TYPE_CASUAL = "CASUAL"
private const val TYPE_EVENT = "EVENT"
private const val TYPE_STAFF = "STAFF"
private const val TYPE_RECIPE = "RECIPE"
private const val TYPE_PACKAGING = "PACKAGING"
private const val TYPE_UNAVAILABLE = "UNAVAILABLE"

private const val MAX_STANDARD_GROUP_SIZE = 10
private const val MIN_EVENT_GROUP_SIZE = 4
private const val MAX_EVENT_GROUP_SIZE = 100
private const val MAX_VISITING_TICK = 21
private const val DELIVERY_TICKS_PER_DISTANCE = 5.0

/**
 * Responsible for parsing customers and incidents.
 */
class ScenarioParser {

    /**
     * Parses customers and incidents from [filePath] into [simulation].
     * @param recipes used for cross-validation
     */
    fun parse(filePath: String, simulation: Simulation, recipes: List<Recipe>): Boolean {
        val file = File(filePath)
        if (!file.isFile) return false
        val scenarioContent = file.readText()
        if (!fits(scenarioContent, SCENARIO_SCHEMA)) return false
        val jsonObj = JSONObject(scenarioContent)
        val customerGroupsJSON = jsonObj.getJSONArray("customerGroups")
        val customers = parseCustomers(customerGroupsJSON)
        if (customers == null ||
            !validateCustomerGroup(customers) ||
            !crossValidateCustomers(customers, simulation, recipes)
        ) {
            return false
        } else {
            simulation.customers = customers
        }
        val incidentsJSON = jsonObj.getJSONArray("incidents")
        val incidents = parseIncidents(incidentsJSON, simulation, recipes)
        if (incidents == null ||
            !validateIncident(incidents) ||
            !crossValidateIncidents(incidents, simulation)
        ) {
            return false
        } else {
            simulation.incidents = incidents
            return true
        }
    }

    /**
     * Parses the food preferences of one customer group.
     */
    private fun parseFoodPreferences(jsonArray: JSONArray): List<FoodPreferences> {
        val prefs = mutableListOf<FoodPreferences>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val size = obj.getInt(KEY_SIZE)
            val excluded = obj.optJSONArray("excludedIngredients")?.map { it.toString() } ?: emptyList()
            val preferred = obj.optJSONArray("preferredIngredients")?.map { it.toString() } ?: emptyList()
            val favoriteDishes = obj.optJSONArray("favoriteDishes")?.map { it.toString() } ?: emptyList()
            prefs.add(FoodPreferences(size, excluded, preferred, favoriteDishes))
        }
        return prefs
    }

    /**
     * Parses all customer groups.
     */
    private fun parseCustomers(jsonArray: JSONArray): List<CustomerGroup>? {
        val customerList = mutableListOf<CustomerGroup>()
        if (!fits(jsonArray.toString(), CUSTOMER_SCHEMA)) return null
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i)
            val group = parseCustomerGroup(obj) ?: return null
            customerList.add(group)
        }
        return customerList
    }

    /**
     * Parses one customer group.
     */
    private fun parseCustomerGroup(obj: JSONObject): CustomerGroup? {
        val id = obj.getInt(KEY_ID)
        val type = obj.getString(KEY_TYPE)
        val size = obj.getInt(KEY_SIZE)
        val visitingTick = obj.getInt("visitingTick")
        val tableType = TableType.valueOf(obj.optString("tableType", "COMMON"))
        val foodPreferences = parseFoodPreferences(obj.getJSONArray("foodPreferences"))
        return when (type) {
            TYPE_REGULAR -> {
                RegularCustomerGroup(
                    id = id,
                    size = size,
                    visitingTick = visitingTick,
                    customerPreferences = foodPreferences,
                    preferredTableType = tableType,
                    visitingStart = obj.getInt("visitingStart"),
                    visitingPeriod = obj.getInt("visitingPeriod"),
                    restaurant = obj.getInt("restaurant")
                )
            }

            TYPE_CASUAL -> {
                parseCasualCustomerGroup(obj, id, size, visitingTick, tableType, foodPreferences)
            }

            TYPE_EVENT -> {
                parseEventCustomerGroup(obj, id, size, visitingTick, tableType, foodPreferences)
            }

            else -> {
                null
            }
        }
    }

    private fun parseEventCustomerGroup(
        obj: JSONObject,
        id: Int,
        size: Int,
        visitingTick: Int,
        tableType: TableType,
        foodPreferences: List<FoodPreferences>
    ): EventCustomerGroup {
        val restaurantTypes = obj.getJSONArray("restaurantTypes").map {
            RestaurantType.valueOf(it.toString())
        }
        val favObj = obj.getJSONObject("favoriteDishes")
        val favoriteDishes = mutableMapOf<RestaurantType, String>()
        for (key in favObj.keys()) {
            favoriteDishes[RestaurantType.valueOf(key)] = favObj.getString(key)
        }
        return EventCustomerGroup(
            id = id,
            size = size,
            visitingTick = visitingTick,
            customerPreferences = foodPreferences,
            preferredTableType = tableType,
            restaurantTypes = restaurantTypes,
            eventEvening = obj.getInt("eventEvening"),
            favoriteBasicDish = favoriteDishes
        )
    }

    private fun parseCasualCustomerGroup(
        obj: JSONObject,
        id: Int,
        size: Int,
        visitingTick: Int,
        tableType: TableType,
        foodPreferences: List<FoodPreferences>
    ): CustomerGroup? {
        val restaurantTypes = obj.getJSONArray("restaurantTypes").map {
            RestaurantType.valueOf(it.toString())
        }
        val visitingEvenings = obj.getJSONArray("visitingEvenings").map { evening ->
            evening as? Int ?: return null
        }
        val ratingLikelihood = RatingLikelihood.valueOf(obj.getString("ratingLikelihood"))
        val distance = obj.optInt("deliveryDistance")
        return if (distance > 0) {
            DeliveryCustomerGroup(
                id = id,
                size = size,
                visitingTick = visitingTick,
                customerPreferences = foodPreferences,
                preferredTableType = null,
                restaurantTypes = restaurantTypes,
                visitingEvenings = visitingEvenings,
                ratingLikelihood = ratingLikelihood,
                deliveryDistance = distance
            )
        } else {
            CasualCustomerGroup(
                id = id,
                size = size,
                visitingTick = visitingTick,
                customerPreferences = foodPreferences,
                preferredTableType = tableType,
                restaurantTypes = restaurantTypes,
                visitingEvenings = visitingEvenings,
                ratingLikelihood = ratingLikelihood
            )
        }
    }

    private fun validateCustomerGroup(groups: List<CustomerGroup>): Boolean {
        val uniqueIds = groups.map { it.id }.toSet()
        return uniqueIds.size == groups.size && groups.all { isValidCustomerGroup(it) }
    }

    private fun isValidCustomerGroup(group: CustomerGroup): Boolean {
        if (!hasValidSize(group)) {
            return false
        }
        if (group.visitingTick !in 1..MAX_VISITING_TICK) {
            return false
        }
        val prefSize = group.customerPreferences.sumOf { it.size }
        return prefSize <= group.size && isValidDeliveryTiming(group)
    }

    private fun hasValidSize(group: CustomerGroup): Boolean {
        return if (group is EventCustomerGroup) {
            group.size in MIN_EVENT_GROUP_SIZE..MAX_EVENT_GROUP_SIZE
        } else {
            group.size in 1..MAX_STANDARD_GROUP_SIZE
        }
    }

    private fun isValidDeliveryTiming(group: CustomerGroup): Boolean {
        if (group !is DeliveryCustomerGroup) {
            return true
        }
        val travelTicks = ceil(group.deliveryDistance / DELIVERY_TICKS_PER_DISTANCE).toInt()
        return group.visitingTick - 3 - travelTicks > 0
    }

    private fun crossValidateCustomers(
        customers: List<CustomerGroup>,
        sim: Simulation,
        recipes: List<Recipe>
    ): Boolean {
        val supplier = getSupplierFromSimulation(sim) ?: return false
        val validIngredientNames = supplier.ingredients.map { it.getName() }.toSet()
        val validDishNames = recipes.map { it.dishName }.toSet()
        return customers.all { group ->
            isValidRegularVisit(group, sim) &&
                group.customerPreferences.all { isValidPreference(it, validIngredientNames, validDishNames) } &&
                hasRequiredEventFavorites(group)
        }
    }

    private fun isValidRegularVisit(group: CustomerGroup, sim: Simulation): Boolean {
        if (group !is RegularCustomerGroup) {
            return true
        }
        val restaurant = sim.restaurants.find { it.id == group.getRestaurant() } ?: return false
        return group.visitingTick >= restaurant.openingTickStart &&
            group.visitingTick <= restaurant.openingTickEnd - 3
    }

    private fun isValidPreference(
        pref: FoodPreferences,
        validIngredientNames: Set<String>,
        validDishNames: Set<String>
    ): Boolean {
        val excluded = pref.excludedIngredients?.toSet() ?: emptySet()
        val preferred = pref.preferredIngredients?.toSet() ?: emptySet()
        val favs = pref.favoriteDishes?.toSet() ?: emptySet()
        val namesKnown = validIngredientNames.containsAll(excluded) &&
            validIngredientNames.containsAll(preferred) &&
            validDishNames.containsAll(favs)
        val notEntireSet = validIngredientNames != excluded &&
            validIngredientNames != preferred &&
            validDishNames != favs
        val noOverlap = excluded.intersect(preferred).isEmpty()
        return namesKnown && notEntireSet && noOverlap
    }

    private fun hasRequiredEventFavorites(group: CustomerGroup): Boolean {
        if (group !is EventCustomerGroup) {
            return true
        }
        return group.favoriteBasicDish.keys.containsAll(group.restaurantTypes)
    }

    private fun parseIncidents(json: JSONArray, sim: Simulation, recipes: List<Recipe>): MutableList<Incident>? {
        if (!fits(json.toString(), INCIDENT_SCHEMA)) return null
        val incidents = mutableListOf<Incident>()
        val supplier = getSupplierFromSimulation(sim) ?: return null
        for (incident in json) {
            val parsed = parseOneIncident(incident, sim, recipes, supplier) ?: return null
            incidents.add(parsed)
        }
        return incidents
    }

    private fun parseOneIncident(
        incident: Any,
        sim: Simulation,
        recipes: List<Recipe>,
        supplier: Supplier
    ): Incident? {
        if (incident !is JSONObject) {
            return null
        }
        val id = incident.getInt(KEY_ID)
        val evening = incident.getInt("evening")
        return when (incident.getString(KEY_TYPE)) {
            TYPE_STAFF -> {
                parseStaffIncident(incident, sim, id, evening)
            }
            TYPE_RECIPE -> {
                RecipeIncident(
                    id = id,
                    evening = evening,
                    allRecipes = recipes,
                    ingredientName = incident.getString(KEY_INGREDIENT),
                    adaptation = incident.getInt("adaptation")
                )
            }
            TYPE_PACKAGING -> {
                PackagingIncident(
                    id = id,
                    evening = evening,
                    supplier = supplier,
                    ingredientName = incident.getString(KEY_INGREDIENT),
                    packagingVolume = incident.getInt("packagingVolume")
                )
            }

            TYPE_UNAVAILABLE -> {
                UnavailableIncident(
                    id = id,
                    evening = evening,
                    supplier = supplier,
                    ingredientName = incident.getString(KEY_INGREDIENT),
                    duration = incident.getInt("duration")
                )
            }

            else -> {
                null
            }
        }
    }

    private fun parseStaffIncident(incident: JSONObject, sim: Simulation, id: Int, evening: Int): StaffIncident? {
        val staffTypeString = incident.getString("staffType")
        val staffType = when (staffTypeString) {
            "COOK" -> StaffType.COOK
            "WAITSTAFF" -> StaffType.WAITSTAFF
            "DRIVER" -> StaffType.DRIVER
            else -> return null
        }
        var cookType: CookType? = null
        if (staffType == StaffType.COOK) {
            cookType = when (incident.getString("cookType")) {
                "SOUS" -> CookType.SOUS
                "TOURNANT" -> CookType.TOURNANT
                "SAUCE" -> CookType.SAUCE
                "FISH" -> CookType.FISH
                "ROAST" -> CookType.ROAST
                "VEGETABLE" -> CookType.VEGETABLE
                "PASTRY" -> CookType.PASTRY
                else -> return null
            }
        }
        val number = incident.getInt("number")
        val restaurantId = incident.getInt("restaurant")
        val restaurant = sim.restaurants.find { it.id == restaurantId }
        if (restaurant == null) {
            return null
        }
        return StaffIncident(id, evening, number, staffType, restaurant.staff, cookType)
    }

    private fun validateIncident(incidents: MutableList<Incident>): Boolean {
        val unavailableIncidents = incidents.filterIsInstance<UnavailableIncident>()
            .groupBy { it.getIngredientName() }
        for (list in unavailableIncidents.values) {
            val sortedList = list.sortedBy { it.evening }
            for (i in 0..<sortedList.size - 1) {
                val currentIncident = sortedList[i]
                val nextIncident = sortedList[i + 1]
                if (currentIncident.evening + currentIncident.getDuration() > nextIncident.evening) {
                    return false
                }
            }
        }
        val uniqueIds = incidents.distinctBy { it.id }.size
        return uniqueIds >= incidents.size
    }

    private fun crossValidateIncidents(
        incidents: List<Incident>,
        simulation: Simulation
    ): Boolean {
        val supplier = getSupplierFromSimulation(simulation) ?: return false
        val validIngredientNames = supplier.ingredients.map { ingredient -> ingredient.getName() }
        for (incident in incidents) {
            val ingredientName = when (incident) {
                is RecipeIncident -> incident.getIngredientName()
                is PackagingIncident -> incident.getIngredientName()
                is UnavailableIncident -> incident.getIngredientName()
                else -> null
            }
            if (ingredientName != null && !validIngredientNames.contains(ingredientName)) {
                return false
            }
        }
        return true
    }

    private fun getSupplierFromSimulation(simulation: Simulation): Supplier? {
        return simulation.restaurants.firstOrNull()?.kitchen?.menu?.pantry?.getSupplier()
    }
}
