package general.parserTests

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.LogLevel
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.SimulationLogger
import saarlacarte.parser.ScenarioParser
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.Simulation
import saarlacarte.simulation.Supplier
import java.io.File

class CustomerGroupParserTest {

    @TempDir
    lateinit var tmp: File

    private val parser = ScenarioParser()

    private lateinit var supplier: Supplier
    private lateinit var recipes: List<Recipe>
    private lateinit var simulation: Simulation

    @BeforeEach
    fun setUp() {
        val potato = Ingredient("Potato", MeasuringUnit.G, 5)
        val tomato = Ingredient("Tomato", MeasuringUnit.X, 4)
        val rice = Ingredient("Rice", MeasuringUnit.G, 10)
        val chicken = Ingredient("Chicken", MeasuringUnit.G, 3)
        val beef = Ingredient("Beef", MeasuringUnit.G, 3)
        supplier = Supplier(
            listOf(potato, tomato, rice, chicken, beef),
            mutableMapOf(
                "Potato" to 1000,
                "Tomato" to 10,
                "Rice" to 500,
                "Chicken" to 400,
                "Beef" to 300
            )
        )
        val pizzaRecipe = Recipe(
            1,
            "Pizza",
            15,
            mutableMapOf(tomato to 2, potato to 100),
            listOf(CookType.EXEC, CookType.ROAST),
            RestaurantType.EUROPEAN
        )
        val sushiRecipe = Recipe(
            2,
            "Sushi",
            10,
            mutableMapOf(rice to 150),
            listOf(CookType.EXEC, CookType.FISH),
            RestaurantType.ASIAN
        )
        val burgerRecipe = Recipe(
            3,
            "Burger",
            12,
            mutableMapOf(beef to 150),
            listOf(CookType.ROAST, CookType.TOURNANT),
            RestaurantType.AMERICAN
        )
        recipes = listOf(pizzaRecipe, sushiRecipe, burgerRecipe)
        val restaurant1 = createRestaurant(
            id = 1,
            name = "Asian Garden",
            type = RestaurantType.ASIAN,
            openingTickStart = 1,
            openingTickEnd = 24
        )
        val restaurant2 = createRestaurant(
            id = 2,
            name = "European Bistro",
            type = RestaurantType.EUROPEAN,
            openingTickStart = 5,
            openingTickEnd = 20
        )

        simulation = Simulation(SimulationLogger(LogLevel.INFO, null))
        simulation.restaurants = listOf(restaurant1, restaurant2)
    }

    private fun createRestaurant(
        id: Int,
        name: String,
        type: RestaurantType,
        openingTickStart: Int,
        openingTickEnd: Int
    ): Restaurant {
        val staff = Staff(
            2,
            mapOf(
                CookType.EXEC to 1,
                CookType.SOUS to 1,
                CookType.TOURNANT to 0,
                CookType.SAUCE to 0,
                CookType.FISH to 0,
                CookType.ROAST to 1,
                CookType.VEGETABLE to 0,
                CookType.PASTRY to 0
            ),
            1
        )
        val menu = Menu(supplier, recipes, type)
        val table = Table(1, TableType.COMMON, 10)
        val foh = FrontOfHouse(listOf(table), staff.getWaiters(), null, menu)
        val kitchen = Kitchen(staff.getCooks(), menu)
        return Restaurant(
            id = id,
            name = name,
            type = type,
            openingTickStart = openingTickStart,
            openingTickEnd = openingTickEnd,
            canHostEvent = true,
            staff = staff,
            positiveRatings = 0,
            negativeRatings = 0,
            frontOfHouse = foh,
            kitchen = kitchen,
            deliveryDriverCount = 1
        )
    }

    private val validScenarioJson = """
    {
      "customerGroups": [
        {
          "id": 1,
          "type": "REGULAR",
          "size": 4,
          "visitingTick": 10,
          "tableType": "COMMON",
          "foodPreferences": [
            {
              "size": 4,
              "excludedIngredients": ["Potato"],
              "preferredIngredients": ["Tomato"],
              "favoriteDishes": ["Pizza"]
            }
          ],
          "visitingStart": 1,
          "visitingPeriod": 2,
          "restaurant": 1
        },
        {
          "id": 2,
          "type": "CASUAL",
          "size": 3,
          "visitingTick": 12,
          "tableType": "BAR",
          "foodPreferences": [
            {
              "size": 2,
              "preferredIngredients": ["Rice"]
            }
          ],
          "restaurantTypes": ["ASIAN"],
          "visitingEvenings": [1, 2],
          "ratingLikelihood": "ALWAYS"
        },
        {
          "id": 3,
          "type": "CASUAL",
          "size": 2,
          "visitingTick": 15,
          "foodPreferences": [
            {
              "size": 2,
              "favoriteDishes": ["Pizza"]
            }
          ],
          "restaurantTypes": ["EUROPEAN"],
          "visitingEvenings": [1],
          "deliveryDistance": 5,
          "ratingLikelihood": "SOME"
        },
        {
          "id": 4,
          "type": "EVENT",
          "size": 8,
          "visitingTick": 10,
          "tableType": "SEPARATED",
          "foodPreferences": [
            {
              "size": 8,
              "favoriteDishes": ["Pizza"]
            }
          ],
          "restaurantTypes": ["EUROPEAN", "ASIAN"],
          "eventEvening": 4,
          "favoriteDishes": {
            "EUROPEAN": "Pizza",
            "ASIAN": "Sushi"
          }
        }
      ],
      "incidents": []
    }
    """.trimIndent()

    private fun parseScenario(json: String): Boolean {
        val file = File(tmp, "scenario.json")
        file.writeText(json)
        return parser.parse(file.path, simulation, recipes)
    }

    private fun validJsonObject(): JSONObject = JSONObject(validScenarioJson)

    private fun getCustomerGroup(json: JSONObject, index: Int): JSONObject {
        return json.getJSONArray("customerGroups").getJSONObject(index)
    }

    private fun jsonArrayOf(vararg elements: Any): JSONArray {
        val array = JSONArray()
        for (element in elements) {
            array.put(element)
        }
        return array
    }

    // -------------------------------------------------------------------------
    // Happy path tests
    // -------------------------------------------------------------------------

    @Test
    fun testValidAllCustomerGroupTypes() {
        assertTrue(parseScenario(validScenarioJson))
        assertNotNull(simulation.customers)
        val customers = simulation.customers
        assertEquals(4, customers.size)

        // Group 0: REGULAR
        assertTrue(customers[0] is RegularCustomerGroup)
        val regular = customers[0] as RegularCustomerGroup
        assertEquals(1, regular.id)
        assertEquals(4, regular.size)
        assertEquals(10, regular.visitingTick)
        assertEquals(TableType.COMMON, regular.preferredTableType)
        assertEquals(1, regular.getVisitingStart())
        assertEquals(2, regular.getVisitingPeriod())
        assertEquals(1, regular.getRestaurant())

        // Group 1: CASUAL dine-in
        assertTrue(customers[1] is CasualCustomerGroup)
        assertFalse(customers[1] is DeliveryCustomerGroup)
        val casual = customers[1] as CasualCustomerGroup
        assertEquals(2, casual.id)
        assertEquals(TableType.BAR, casual.preferredTableType)
        assertEquals(RatingLikelihood.ALWAYS, casual.ratingLikelihood)

        // Group 2: CASUAL delivery
        assertTrue(customers[2] is DeliveryCustomerGroup)
        val delivery = customers[2] as DeliveryCustomerGroup
        assertEquals(3, delivery.id)
        assertEquals(5, delivery.deliveryDistance)
        assertNull(delivery.preferredTableType)
        assertTrue(delivery.isDelivery())

        // Group 3: EVENT
        assertTrue(customers[3] is EventCustomerGroup)
        val event = customers[3] as EventCustomerGroup
        assertEquals(4, event.id)
        assertEquals(8, event.size)
        assertEquals(4, event.eventEvening)
        assertEquals(TableType.SEPARATED, event.preferredTableType)
        assertEquals("Pizza", event.favoriteBasicDish[RestaurantType.EUROPEAN])
        assertEquals("Sushi", event.favoriteBasicDish[RestaurantType.ASIAN])
    }

    @Test
    fun testEmptyCustomerGroupsAccepted() {
        val json = validJsonObject()
        json.put("customerGroups", JSONArray())
        assertTrue(parseScenario(json.toString()))
        assertEquals(0, simulation.customers.size)
    }

    // -------------------------------------------------------------------------
    // Unique ID and general property validation
    // -------------------------------------------------------------------------

    @Test
    fun testDuplicateCustomerGroupIdRejected() {
        val json = validJsonObject()
        getCustomerGroup(json, 1).put("id", 1)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testNegativeCustomerGroupIdRejected() {
        val json = validJsonObject()
        getCustomerGroup(json, 0).put("id", -1)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testUnknownCustomerGroupTypeRejected() {
        val json = validJsonObject()
        getCustomerGroup(json, 0).put("type", "VIP")
        assertFalse(parseScenario(json.toString()))
    }

    // -------------------------------------------------------------------------
    // Visiting tick constraints
    // -------------------------------------------------------------------------

    @Test
    fun testVisitingTickBelowMinimumRejected() {
        val json = validJsonObject()
        getCustomerGroup(json, 0).put("visitingTick", 0)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testVisitingTickAboveMaximumRejected() {
        val json = validJsonObject()
        getCustomerGroup(json, 0).put("visitingTick", 22)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testVisitingTickBoundariesAccepted() {
        val jsonMin = validJsonObject()
        getCustomerGroup(jsonMin, 0).put("visitingTick", 1)
        assertTrue(parseScenario(jsonMin.toString()))

        val jsonMax = validJsonObject()
        getCustomerGroup(jsonMax, 0).put("visitingTick", 21)
        assertTrue(parseScenario(jsonMax.toString()))
    }

    // -------------------------------------------------------------------------
    // Size constraints per type
    // -------------------------------------------------------------------------

    @Test
    fun testRegularSizeLimits() {
        val jsonZero = validJsonObject()
        getCustomerGroup(jsonZero, 0).put("size", 0)
        assertFalse(parseScenario(jsonZero.toString()))

        val jsonTooLarge = validJsonObject()
        getCustomerGroup(jsonTooLarge, 0).put("size", 11)
        assertFalse(parseScenario(jsonTooLarge.toString()))
    }

    @Test
    fun testCasualSizeLimits() {
        val jsonZero = validJsonObject()
        getCustomerGroup(jsonZero, 1).put("size", 0)
        assertFalse(parseScenario(jsonZero.toString()))

        val jsonTooLarge = validJsonObject()
        getCustomerGroup(jsonTooLarge, 1).put("size", 11)
        assertFalse(parseScenario(jsonTooLarge.toString()))
    }

    @Test
    fun testEventSizeLimits() {
        val jsonTooSmall = validJsonObject()
        getCustomerGroup(jsonTooSmall, 3).put("size", 3)
        assertFalse(parseScenario(jsonTooSmall.toString()))

        val jsonTooLarge = validJsonObject()
        getCustomerGroup(jsonTooLarge, 3).put("size", 101)
        assertFalse(parseScenario(jsonTooLarge.toString()))
    }

    @Test
    fun testBoundarySizesAccepted() {
        val json = validJsonObject()
        // Regular size 1 (preference size adjusted to 1)
        getCustomerGroup(json, 0).put("size", 1)
        getCustomerGroup(json, 0).getJSONArray("foodPreferences")
            .getJSONObject(0).put("size", 1)
        // Event size 4 (preference size adjusted to 4)
        getCustomerGroup(json, 3).put("size", 4)
        getCustomerGroup(json, 3).getJSONArray("foodPreferences")
            .getJSONObject(0).put("size", 4)
        assertTrue(parseScenario(json.toString()))
    }

    @Test
    fun testEventEveningBelowMinimumRejected() {
        val json = validJsonObject()
        getCustomerGroup(json, 3).put("eventEvening", 3)
        assertFalse(parseScenario(json.toString()))
    }

    // -------------------------------------------------------------------------
    // Food preferences validation
    // -------------------------------------------------------------------------

    @Test
    fun testPreferenceSizeSumExceedsGroupSizeRejected() {
        val json = validJsonObject()
        // Group size is 4, preference size set to 5
        getCustomerGroup(json, 0).getJSONArray("foodPreferences")
            .getJSONObject(0).put("size", 5)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testPreferenceSizeSumLessThanGroupSizeAccepted() {
        val json = validJsonObject()
        // Group size is 4, preference size set to 2
        getCustomerGroup(json, 0).getJSONArray("foodPreferences")
            .getJSONObject(0).put("size", 2)
        assertTrue(parseScenario(json.toString()))
    }

    @Test
    fun testMultiplePreferencesSumHandling() {
        val jsonExceed = validJsonObject()
        val prefsExceed = getCustomerGroup(jsonExceed, 0).getJSONArray("foodPreferences")
        val extraPref = JSONObject().apply {
            put("size", 3)
            put("preferredIngredients", jsonArrayOf("Tomato"))
        }
        prefsExceed.put(extraPref) // 4 + 3 = 7 > 4
        assertFalse(parseScenario(jsonExceed.toString()))

        val jsonMatch = validJsonObject()
        val prefsMatch = getCustomerGroup(jsonMatch, 0).getJSONArray("foodPreferences")
        prefsMatch.getJSONObject(0).put("size", 2)
        val validExtra = JSONObject().apply {
            put("size", 2)
            put("preferredIngredients", jsonArrayOf("Tomato"))
        }
        prefsMatch.put(validExtra) // 2 + 2 = 4 == 4
        assertTrue(parseScenario(jsonMatch.toString()))
    }

    @Test
    fun testPreferenceSchemaViolations() {
        // Size 0 in preference
        val jsonZero = validJsonObject()
        getCustomerGroup(jsonZero, 0).getJSONArray("foodPreferences")
            .getJSONObject(0).put("size", 0)
        assertFalse(parseScenario(jsonZero.toString()))

        // No criteria specified (none of excluded, preferred, favoriteDishes)
        val jsonEmptyCriteria = validJsonObject()
        val pref = getCustomerGroup(jsonEmptyCriteria, 0).getJSONArray("foodPreferences")
            .getJSONObject(0)
        pref.remove("excludedIngredients")
        pref.remove("preferredIngredients")
        pref.remove("favoriteDishes")
        assertFalse(parseScenario(jsonEmptyCriteria.toString()))

        // Empty array for excluded ingredients (minItems: 1)
        val jsonEmptyArray = validJsonObject()
        getCustomerGroup(jsonEmptyArray, 0).getJSONArray("foodPreferences")
            .getJSONObject(0).put("excludedIngredients", JSONArray())
        assertFalse(parseScenario(jsonEmptyArray.toString()))
    }

    @Test
    fun testPreferenceDuplicateElementsRejected() {
        val json = validJsonObject()
        val pref = getCustomerGroup(json, 0).getJSONArray("foodPreferences")
            .getJSONObject(0)
        pref.put("excludedIngredients", jsonArrayOf("Potato", "Potato"))
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testSingleCustomerGroupExcludedAndPreferredIntersectionRejected() {
        val invalidScenarioJson = """
    {
      "customerGroups": [
        {
          "id": 1,
          "type": "REGULAR",
          "size": 4,
          "visitingTick": 10,
          "tableType": "COMMON",
          "foodPreferences": [
            {
              "size": 4,
              "excludedIngredients": [
                "Potato"
              ],
              "preferredIngredients": [
                "Potato",
                "Tomato"
              ],
              "favoriteDishes": [
                "Pizza"
              ]
            }
          ],
          "visitingStart": 1,
          "visitingPeriod": 2,
          "restaurant": 1
        }
      ],
      "incidents": []
    }
        """.trimIndent()

        // The parser should return false because "Potato" cannot be both
        // excluded and preferred at the same time.
        assertFalse(parseScenario(invalidScenarioJson))
    }

    @Test
    fun testPreferenceExcludedAndPreferredIntersectionRejected() {
        val json = validJsonObject()
        val pref = getCustomerGroup(json, 0).getJSONArray("foodPreferences")
            .getJSONObject(0)
        pref.put("excludedIngredients", jsonArrayOf("Potato"))
        pref.put("preferredIngredients", jsonArrayOf("Potato", "Tomato"))
        assertFalse(parseScenario(json.toString()))
    }

    // -------------------------------------------------------------------------
    // Cross-validation: existence of referenced ingredients and dishes
    // -------------------------------------------------------------------------

    @Test
    fun testUnknownReferencedEntitiesRejected() {
        val jsonExcluded = validJsonObject()
        getCustomerGroup(jsonExcluded, 0).getJSONArray("foodPreferences")
            .getJSONObject(0).put("excludedIngredients", jsonArrayOf("DragonFruit"))
        assertFalse(parseScenario(jsonExcluded.toString()))

        val jsonPreferred = validJsonObject()
        getCustomerGroup(jsonPreferred, 0).getJSONArray("foodPreferences")
            .getJSONObject(0).put("preferredIngredients", jsonArrayOf("DragonFruit"))
        assertFalse(parseScenario(jsonPreferred.toString()))

        val jsonDish = validJsonObject()
        getCustomerGroup(jsonDish, 0).getJSONArray("foodPreferences")
            .getJSONObject(0).put("favoriteDishes", jsonArrayOf("UnknownDish"))
        assertFalse(parseScenario(jsonDish.toString()))
    }

    @Test
    fun testPreferenceEqualsAllAvailableEntitiesRejected() {
        // Excluded equals all available ingredients
        val jsonAllExcluded = validJsonObject()
        val prefExcluded = getCustomerGroup(jsonAllExcluded, 0).getJSONArray("foodPreferences")
            .getJSONObject(0)
        prefExcluded.put(
            "excludedIngredients",
            jsonArrayOf("Potato", "Tomato", "Rice", "Chicken", "Beef")
        )
        assertFalse(parseScenario(jsonAllExcluded.toString()))

        // Preferred equals all available ingredients
        val jsonAllPreferred = validJsonObject()
        val prefPreferred = getCustomerGroup(jsonAllPreferred, 0).getJSONArray("foodPreferences")
            .getJSONObject(0)
        prefPreferred.put(
            "preferredIngredients",
            jsonArrayOf("Potato", "Tomato", "Rice", "Chicken", "Beef")
        )
        assertFalse(parseScenario(jsonAllPreferred.toString()))

        // Favorite dishes equals all available dishes
        val jsonAllDishes = validJsonObject()
        val prefDishes = getCustomerGroup(jsonAllDishes, 0).getJSONArray("foodPreferences")
            .getJSONObject(0)
        prefDishes.put(
            "favoriteDishes",
            jsonArrayOf("Pizza", "Sushi", "Burger")
        )
        assertFalse(parseScenario(jsonAllDishes.toString()))
    }

    // -------------------------------------------------------------------------
    // Delivery customer timing and schema constraints
    // -------------------------------------------------------------------------

    @Test
    fun testDeliveryTimingValidation() {
        // distance 5 -> travelTicks = ceil(5 / 5) = 1; tick 4 - 3 - 1 = 0 <= 0 -> invalid
        val jsonTooLate = validJsonObject()
        val deliveryLate = getCustomerGroup(jsonTooLate, 2)
        deliveryLate.put("visitingTick", 4)
        deliveryLate.put("deliveryDistance", 5)
        assertFalse(parseScenario(jsonTooLate.toString()))

        // distance 5 -> travelTicks = 1; tick 5 - 3 - 1 = 1 > 0 -> valid
        val jsonValid = validJsonObject()
        val deliveryValid = getCustomerGroup(jsonValid, 2)
        deliveryValid.put("visitingTick", 5)
        deliveryValid.put("deliveryDistance", 5)
        assertTrue(parseScenario(jsonValid.toString()))

        // distance 6 -> travelTicks = ceil(6 / 5) = 2; tick 5 - 3 - 2 = 0 <= 0 -> invalid
        val jsonCeil = validJsonObject()
        val deliveryCeil = getCustomerGroup(jsonCeil, 2)
        deliveryCeil.put("visitingTick", 5)
        deliveryCeil.put("deliveryDistance", 6)
        assertFalse(parseScenario(jsonCeil.toString()))
    }

    @Test
    fun testCasualWithBothTableTypeAndDeliveryDistanceRejected() {
        val json = validJsonObject()
        val delivery = getCustomerGroup(json, 2)
        delivery.put("tableType", "COMMON")
        assertFalse(parseScenario(json.toString()))
    }

    // -------------------------------------------------------------------------
    // Regular customer visit constraints
    // -------------------------------------------------------------------------

    @Test
    fun testRegularRestaurantNotFoundRejected() {
        val json = validJsonObject()
        getCustomerGroup(json, 0).put("restaurant", 99)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testRegularVisitingTickWithinOpeningHours() {
        // Restaurant 2: openingTickStart = 5, openingTickEnd = 20 (cutoff = 17)
        val jsonEarly = validJsonObject()
        getCustomerGroup(jsonEarly, 0).put("restaurant", 2)
        getCustomerGroup(jsonEarly, 0).put("visitingTick", 4)
        assertFalse(parseScenario(jsonEarly.toString()))

        val jsonLate = validJsonObject()
        getCustomerGroup(jsonLate, 0).put("restaurant", 2)
        getCustomerGroup(jsonLate, 0).put("visitingTick", 18)
        assertFalse(parseScenario(jsonLate.toString()))

        val jsonMin = validJsonObject()
        getCustomerGroup(jsonMin, 0).put("restaurant", 2)
        getCustomerGroup(jsonMin, 0).put("visitingTick", 5)
        assertTrue(parseScenario(jsonMin.toString()))

        val jsonMax = validJsonObject()
        getCustomerGroup(jsonMax, 0).put("restaurant", 2)
        getCustomerGroup(jsonMax, 0).put("visitingTick", 17)
        assertTrue(parseScenario(jsonMax.toString()))
    }

    // -------------------------------------------------------------------------
    // Event customer group favorites coverage
    // -------------------------------------------------------------------------

    @Test
    fun testEventMissingFavoriteDishForRestaurantTypeRejected() {
        val json = validJsonObject()
        val event = getCustomerGroup(json, 3)
        // restaurantTypes has ["EUROPEAN", "ASIAN"], but favoriteDishes only defines EUROPEAN
        val fav = JSONObject().apply {
            put("EUROPEAN", "Pizza")
        }
        event.put("favoriteDishes", fav)
        assertFalse(parseScenario(json.toString()))
    }

    // -------------------------------------------------------------------------
    // Schema exclusivity: forbidden properties per customer type
    // -------------------------------------------------------------------------

    @Test
    fun testForbiddenPropertiesRejected() {
        val jsonRegular = validJsonObject()
        getCustomerGroup(jsonRegular, 0).put("eventEvening", 4)
        assertFalse(parseScenario(jsonRegular.toString()))

        val jsonCasual = validJsonObject()
        getCustomerGroup(jsonCasual, 1).put("restaurant", 1)
        assertFalse(parseScenario(jsonCasual.toString()))

        val jsonEvent = validJsonObject()
        getCustomerGroup(jsonEvent, 3).put("visitingStart", 1)
        assertFalse(parseScenario(jsonEvent.toString()))
    }

    @Test
    fun testMissingRequiredPropertiesRejected() {
        val jsonRegular = validJsonObject()
        getCustomerGroup(jsonRegular, 0).remove("visitingPeriod")
        assertFalse(parseScenario(jsonRegular.toString()))

        val jsonCasual = validJsonObject()
        getCustomerGroup(jsonCasual, 1).remove("restaurantTypes")
        assertFalse(parseScenario(jsonCasual.toString()))

        val jsonEvent = validJsonObject()
        getCustomerGroup(jsonEvent, 3).remove("eventEvening")
        assertFalse(parseScenario(jsonEvent.toString()))
    }

    // -------------------------------------------------------------------------
    // File & JSON structure errors
    // -------------------------------------------------------------------------

    @Test
    fun testNonExistentFileReturnsFalse() {
        assertFalse(parser.parse("non_existent_file.json", simulation, recipes))
    }

    @Test
    fun testMalformedJsonReturnsFalse() {
        assertFalse(parseScenario("{ invalid json"))
    }

    @Test
    fun testMissingTopLevelKeysReturnsFalse() {
        assertFalse(parseScenario("""{"incidents": []}"""))
        assertFalse(parseScenario("""{"customerGroups": []}"""))
    }
}
