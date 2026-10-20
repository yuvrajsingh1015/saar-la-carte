package saarlacarte.parser

import org.json.JSONArray
import org.json.JSONObject
import saarlacarte.enums.CookType
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Recipe
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.Supplier
import java.io.File

private const val SCHEMA = "classpath://schema/restaurants.schema"

private const val KEY_RESTAURANTS = "restaurants"
private const val KEY_ID = "id"
private const val KEY_NAME = "name"
private const val KEY_TYPE = "type"
private const val KEY_OPENING_START = "openingTickStart"
private const val KEY_OPENING_END = "openingTickEnd"
private const val KEY_DELIVERY_DRIVERS = "deliveryDrivers"
private const val KEY_EVENT = "event"
private const val KEY_POSITIVE_RATINGS = "positiveRatings"
private const val KEY_NEGATIVE_RATINGS = "negativeRatings"
private const val KEY_RECIPES = "recipes"
private const val KEY_KITCHEN_STAFF = "kitchenStaff"
private const val KEY_WAITSTAFF = "waitstaff"
private const val KEY_TABLES = "tables"
private const val KEY_SIZE = "size"

// raw table entry, before validation
private data class ParsedTable(val id: Int, val type: TableType, val size: Int)

// raw restaurant entry, before validation
private data class ParsedRestaurant(
    val id: Int,
    val name: String,
    val type: RestaurantType,
    val openingTickStart: Int,
    val openingTickEnd: Int,
    val deliveryDrivers: Int,
    val event: Boolean,
    val positiveRatings: Int,
    val negativeRatings: Int,
    val recipeIds: List<Int>,
    val cookCounts: Map<CookType, Int>,
    val waitstaff: Int,
    val tables: List<ParsedTable>
)

/**
parse and validate restaurants
 */
class RestaurantParser {

    /** parses and returns the restaurants, or null if the file is invalid */
    fun parse(filePath: String, supplier: Supplier, recipes: List<Recipe>): List<Restaurant>? {
        val file = File(filePath)
        if (!file.isFile) return null
        val text = file.readText()
        if (!SchemaCheck.fits(text, SCHEMA)) return null

        val parsed = parseRestaurants(JSONObject(text))
        // food parser guarantees unique ids
        val recipesById = mutableMapOf<Int, Recipe>()
        for (recipe in recipes) {
            recipesById[recipe.id] = recipe
        }

        if (!validateRestaurants(parsed)) return null
        if (!crossValidateRestaurants(parsed, recipesById)) return null

        val restaurants = mutableListOf<Restaurant>()
        for (restaurant in parsed) {
            restaurants.add(build(restaurant, supplier, recipesById))
        }
        return restaurants
    }

    private fun parseRestaurants(root: JSONObject): List<ParsedRestaurant> {
        val array = root.getJSONArray(KEY_RESTAURANTS)
        val restaurants = mutableListOf<ParsedRestaurant>()
        for (i in 0 until array.length()) {
            restaurants.add(parseRestaurant(array.getJSONObject(i)))
        }
        return restaurants
    }

    private fun parseRestaurant(obj: JSONObject): ParsedRestaurant {
        val kitchenStaff = obj.getJSONObject(KEY_KITCHEN_STAFF)

        val recipeIdsJson = obj.getJSONArray(KEY_RECIPES)
        val recipeIds = mutableListOf<Int>()
        for (i in 0 until recipeIdsJson.length()) {
            recipeIds.add(recipeIdsJson.getInt(i))
        }

        val cookCounts = mutableMapOf<CookType, Int>()
        for (cookType in CookType.entries) {
            cookCounts[cookType] = kitchenStaff.getInt(cookType.name)
        }

        return ParsedRestaurant(
            id = obj.getInt(KEY_ID),
            name = obj.getString(KEY_NAME),
            type = RestaurantType.valueOf(obj.getString(KEY_TYPE)),
            openingTickStart = obj.getInt(KEY_OPENING_START),
            openingTickEnd = obj.getInt(KEY_OPENING_END),
            deliveryDrivers = obj.getInt(KEY_DELIVERY_DRIVERS),
            event = obj.getBoolean(KEY_EVENT),
            positiveRatings = obj.getInt(KEY_POSITIVE_RATINGS),
            negativeRatings = obj.getInt(KEY_NEGATIVE_RATINGS),
            recipeIds = recipeIds,
            cookCounts = cookCounts,
            waitstaff = obj.getInt(KEY_WAITSTAFF),
            tables = parseTables(obj.getJSONArray(KEY_TABLES))
        )
    }

    private fun parseTables(array: JSONArray): List<ParsedTable> {
        val tables = mutableListOf<ParsedTable>()
        for (i in 0 until array.length()) {
            val table = array.getJSONObject(i)
            tables.add(
                ParsedTable(
                    id = table.getInt(KEY_ID),
                    type = TableType.valueOf(table.getString(KEY_TYPE)),
                    size = table.getInt(KEY_SIZE)
                )
            )
        }
        return tables
    }

    // checks the rules the json schema can't express on its own

    private fun validateRestaurants(restaurants: List<ParsedRestaurant>): Boolean {
        if (restaurants.isEmpty()) return false // redundant: schema restaurants.minItems = 1

        val ids = mutableListOf<Int>()
        val names = mutableListOf<String>()
        for (restaurant in restaurants) {
            ids.add(restaurant.id)
            names.add(restaurant.name)
        }
        if (!allUnique(ids) || !allUnique(names)) return false

        for (restaurant in restaurants) {
            if (!validateRestaurant(restaurant)) return false
        }
        return true
    }

    private fun validateRestaurant(restaurant: ParsedRestaurant): Boolean {
        if (restaurant.openingTickStart >= restaurant.openingTickEnd) return false

        var cookTotal = 0
        for (count in restaurant.cookCounts.values) {
            cookTotal += count
        }
        if (cookTotal < 1) return false

        val tableIds = mutableListOf<Int>()
        for (table in restaurant.tables) {
            tableIds.add(table.id)
        }
        return allUnique(tableIds)
    }

    // needs the recipe list from the food parser, so it runs after validateRestaurants

    private fun crossValidateRestaurants(
        restaurants: List<ParsedRestaurant>,
        recipesById: Map<Int, Recipe>
    ): Boolean {
        if (!allRecipeIdsExist(restaurants, recipesById)) return false

        for (restaurant in restaurants) {
            if (!uniqueDishNames(restaurant, recipesById)) return false
        }
        return basicDishForEveryPresentType(restaurants, recipesById)
    }

    private fun allRecipeIdsExist(restaurants: List<ParsedRestaurant>, recipesById: Map<Int, Recipe>): Boolean {
        for (restaurant in restaurants) {
            for (recipeId in restaurant.recipeIds) {
                if (recipeId !in recipesById) return false
            }
        }
        return true
    }

    private fun uniqueDishNames(restaurant: ParsedRestaurant, recipesById: Map<Int, Recipe>): Boolean {
        val dishNames = mutableListOf<String>()
        for (recipeId in restaurant.recipeIds) {
            val dishName = recipesById[recipeId]?.dishName
            if (dishName != null) {
                dishNames.add(dishName)
            }
        }
        return allUnique(dishNames)
    }

    private fun basicDishForEveryPresentType(
        restaurants: List<ParsedRestaurant>,
        recipesById: Map<Int, Recipe>
    ): Boolean {
        val presentTypes = mutableSetOf<RestaurantType>()
        for (restaurant in restaurants) {
            presentTypes.add(restaurant.type)
        }
        val coveredTypes = mutableSetOf<RestaurantType>()
        for (recipe in recipesById.values) {
            val basicDishFor = recipe.basicDishFor
            if (basicDishFor != null) {
                coveredTypes.add(basicDishFor)
            }
        }
        return coveredTypes.containsAll(presentTypes)
    }

    private fun <T> allUnique(values: List<T>): Boolean {
        val seen = mutableSetOf<T>()
        for (value in values) {
            if (!seen.add(value)) {
                return false
            }
        }
        return true
    }

    // builds the real domain objects once everything above is confirmed valid

    private fun build(
        restaurant: ParsedRestaurant,
        supplier: Supplier,
        recipesById: Map<Int, Recipe>
    ): Restaurant {
        val staff = Staff(restaurant.waitstaff, restaurant.cookCounts, restaurant.deliveryDrivers)
        val ownedRecipes = mutableListOf<Recipe>()
        for (recipeId in restaurant.recipeIds) {
            val recipe = recipesById[recipeId]
            if (recipe != null) {
                ownedRecipes.add(recipe)
            }
        }
        for (recipe in recipesById.values) {
            if (recipe.basicDishFor != restaurant.type) continue
            if (ownedRecipes.any { it.dishName == recipe.dishName }) continue
            ownedRecipes.add(recipe)
        }
        val menu = Menu(supplier, ownedRecipes, restaurant.type)
        val kitchen = Kitchen(staff.getCooks(), menu)
        // allowing a restaurant with no drivers to have delivery service
//        val driverList = if (restaurant.deliveryDrivers > 0) {
//            DriverList(staff.getDrivers())
//        } else {
//            null
//        }
        val tables = mutableListOf<Table>()
        for (table in restaurant.tables) {
            tables.add(Table(table.id, table.type, table.size))
        }

        val frontOfHouse = FrontOfHouse(tables, staff.getWaiters(), staff.getDrivers(), menu)

        return Restaurant(
            id = restaurant.id,
            name = restaurant.name,
            type = restaurant.type,
            openingTickStart = restaurant.openingTickStart,
            openingTickEnd = restaurant.openingTickEnd,
            canHostEvent = restaurant.event,
            staff = staff,
            positiveRatings = restaurant.positiveRatings,
            negativeRatings = restaurant.negativeRatings,
            frontOfHouse = frontOfHouse,
            kitchen = kitchen,
            deliveryDriverCount = restaurant.deliveryDrivers
        )
    }
}
