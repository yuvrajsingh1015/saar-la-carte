package general.simulationTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.BrowsingService
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

// F28: Browsing service eligibility rules (spec p.22-23)
class BrowsingServiceTest {

    private lateinit var potato: Ingredient
    private lateinit var tomato: Ingredient
    private lateinit var beef: Ingredient

    @BeforeEach
    fun setUp() {
        potato = Ingredient("Potato", MeasuringUnit.G, 5)
        tomato = Ingredient("Tomato", MeasuringUnit.G, 5)
        beef = Ingredient("Beef", MeasuringUnit.G, 5)
        Ticks.init(70)
        Ticks.advanceEvening()
        Ticks.advanceTick()
    }

    private fun recipe(id: Int, dishName: String, ingredients: List<Ingredient>) = Recipe(
        id,
        dishName,
        10,
        ingredients.associateWith { 1 }.toMutableMap(),
        listOf(CookType.EXEC)
    )

    private fun restaurant(
        id: Int,
        type: RestaurantType,
        recipes: List<Recipe>,
        openingTickStart: Int = 1,
        openingTickEnd: Int = 24,
        canHostEvent: Boolean = true,
        driverCount: Int = 1,
        tables: List<Table> = listOf(Table(1, TableType.COMMON, 10))
    ): Restaurant {
        val supplier = Supplier(listOf(potato, tomato), mutableMapOf("Potato" to 100, "Tomato" to 100))
        val staff = Staff(1, mapOf(CookType.EXEC to 1), driverCount)
        val menu = Menu(supplier, recipes, type)
        val foh = FrontOfHouse(tables, staff.getWaiters(), null, menu)
        val kitchen = Kitchen(staff.getCooks(), menu)
        return Restaurant(
            id = id,
            name = "Restaurant $id",
            type = type,
            openingTickStart = openingTickStart,
            openingTickEnd = openingTickEnd,
            canHostEvent = canHostEvent,
            staff = staff,
            positiveRatings = 0,
            negativeRatings = 0,
            frontOfHouse = foh,
            kitchen = kitchen,
            deliveryDriverCount = driverCount
        )
    }

    private fun eventGroup(restaurantTypes: List<RestaurantType>) = EventCustomerGroup(
        id = 1,
        size = 4,
        visitingTick = 10,
        customerPreferences = emptyList(),
        preferredTableType = TableType.COMMON,
        restaurantTypes = restaurantTypes,
        eventEvening = 4,
        favoriteBasicDish = mutableMapOf()
    )

    private fun eligibleFor(
        service: BrowsingService,
        types: List<RestaurantType>,
        tableType: TableType?,
        size: Int,
        prefs: List<FoodPreferences> = emptyList(),
        delivery: Boolean = false,
        isEvent: Boolean = false,
        visitingTick: Int = 10
    ) = service.getEligibleRestaurants(types, tableType, size, prefs, delivery, isEvent, visitingTick)

    @Test
    fun filtersByRestaurantType() {
        val asian = restaurant(1, RestaurantType.ASIAN, listOf(recipe(1, "Sushi", listOf(potato))))
        val european = restaurant(2, RestaurantType.EUROPEAN, listOf(recipe(2, "Pizza", listOf(potato))))
        val service = BrowsingService(listOf(asian, european))

        val eligible = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 2)
        assertEquals(listOf(asian), eligible)
    }

    @Test
    fun restaurantNotYetOpenIsExcluded() {
        val recipes = listOf(recipe(1, "Sushi", listOf(potato)))
        val late = restaurant(1, RestaurantType.ASIAN, recipes, openingTickStart = 12)
        val service = BrowsingService(listOf(late))

        val eligible = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 2)
        assertTrue(eligible.isEmpty())
    }

    @Test
    fun lastThreeTicksBeforeClosingAreExcludedButOneMoreIsAccepted() {
        // openingTickEnd = 20 -> cutoff is tick 17, tick 18 must be rejected, tick 17 accepted
        val recipes = listOf(recipe(1, "Sushi", listOf(potato)))
        val restaurant = restaurant(1, RestaurantType.ASIAN, recipes, openingTickEnd = 20)
        val service = BrowsingService(listOf(restaurant))

        val tooLate = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 2, visitingTick = 18)
        assertTrue(tooLate.isEmpty())

        val stillOk = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 2, visitingTick = 17)
        assertEquals(listOf(restaurant), stillOk)
    }

    @Test
    fun deliveryRequiresAFreeDriver() {
        val noDrivers = restaurant(
            1,
            RestaurantType.ASIAN,
            listOf(recipe(1, "Sushi", listOf(potato))),
            driverCount = 0
        )
        val hasDriver = restaurant(
            2,
            RestaurantType.ASIAN,
            listOf(recipe(2, "Sushi", listOf(potato))),
            driverCount = 1
        )
        val service = BrowsingService(listOf(noDrivers, hasDriver))

        val eligible = eligibleFor(service, listOf(RestaurantType.ASIAN), null, 2, delivery = true)
        assertEquals(listOf(hasDriver), eligible)
    }

    @Test
    fun casualNeedsEnoughSeatsOfTheRequestedTableType() {
        val recipes = listOf(recipe(1, "Sushi", listOf(potato)))
        val small = restaurant(1, RestaurantType.ASIAN, recipes, tables = listOf(Table(1, TableType.COMMON, 3)))
        val service = BrowsingService(listOf(small))

        val tooBig = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 4)
        assertTrue(tooBig.isEmpty())

        val fitsExactly = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 3)
        assertEquals(listOf(small), fitsExactly)
    }

    @Test
    fun eventNeedsCanHostEventAndEnoughEventSeats() {
        val recipes = listOf(recipe(1, "Sushi", listOf(potato)))
        val cannotHost = restaurant(1, RestaurantType.ASIAN, recipes, canHostEvent = false)
        val service = BrowsingService(listOf(cannotHost))

        val eligible = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 4, isEvent = true)
        assertTrue(eligible.isEmpty())
    }

    @Test
    fun excludedIngredientRulesOutRestaurantsWithoutASafeDish() {
        val onlyUnsafeDish = restaurant(1, RestaurantType.ASIAN, listOf(recipe(1, "Sushi", listOf(potato))))
        val safeRecipes = listOf(recipe(2, "Sushi", listOf(potato)), recipe(3, "Salad", listOf(tomato)))
        val hasSafeDish = restaurant(2, RestaurantType.ASIAN, safeRecipes)
        val service = BrowsingService(listOf(onlyUnsafeDish, hasSafeDish))
        val prefs = listOf(FoodPreferences(2, excludedIngredients = listOf("Potato")))

        val eligible = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 2, prefs)
        assertEquals(listOf(hasSafeDish), eligible)
    }

    @Test
    fun noPreferencesMeansAlwaysEligibleOnFoodCheck() {
        val recipes = listOf(recipe(1, "Sushi", listOf(potato)))
        val restaurant = restaurant(1, RestaurantType.ASIAN, recipes)
        val service = BrowsingService(listOf(restaurant))

        val eligible = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 2)
        assertEquals(listOf(restaurant), eligible)
    }

    @Test
    fun updateAvailabilityReducesSeatsForLaterCalls() {
        val recipes = listOf(recipe(1, "Sushi", listOf(potato)))
        val restaurant = restaurant(1, RestaurantType.ASIAN, recipes, tables = listOf(Table(1, TableType.COMMON, 5)))
        val service = BrowsingService(listOf(restaurant))
        service.updateAvailability(restaurant, 3, TableType.COMMON, false)

        // only 2 seats left, a group of 3 no longer fits
        val eligible = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 3)
        assertTrue(eligible.isEmpty())
    }

    @Test
    fun updateAvailabilityReducesDriversForLaterCalls() {
        val recipes = listOf(recipe(1, "Sushi", listOf(potato)))
        val restaurant = restaurant(1, RestaurantType.ASIAN, recipes, driverCount = 1)
        val service = BrowsingService(listOf(restaurant))
        service.updateAvailability(restaurant, 2, TableType.COMMON, true)

        val eligible = eligibleFor(service, listOf(RestaurantType.ASIAN), null, 2, delivery = true)
        assertTrue(eligible.isEmpty())
    }

    @Test
    fun addReservationOnlyAffectsEventSeatsNotCasualSeats() {
        val recipes = listOf(recipe(1, "Sushi", listOf(potato)))
        val restaurant = restaurant(1, RestaurantType.ASIAN, recipes, tables = listOf(Table(1, TableType.COMMON, 5)))
        val service = BrowsingService(listOf(restaurant))
        service.addReservation(restaurant, eventGroup(listOf(RestaurantType.ASIAN)))

        // casual seats are untouched by an event reservation
        val casualEligible = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 5)
        assertEquals(listOf(restaurant), casualEligible)

        // event seats went from 5 to 1 (group size 4), so a group of 2 no longer fits
        val eventEligible = eligibleFor(service, listOf(RestaurantType.ASIAN), TableType.COMMON, 2, isEvent = true)
        assertFalse(eventEligible.contains(restaurant))
    }

    @Test
    fun `getEligibleRestaurants prioritizes excluded ingredients over ratings`() {
        val potatoSoup = recipe(1, "potato soup", listOf(potato))
        val beefStew = recipe(2, "beef stew", listOf(beef))

        val restaurant1 = restaurant(1, RestaurantType.EUROPEAN, listOf(potatoSoup))
        restaurant1.positiveRatings += 10
        restaurant1.negativeRatings += 2
        val restaurant2 = restaurant(2, RestaurantType.EUROPEAN, listOf(beefStew))
        restaurant2.positiveRatings += 15
        restaurant2.negativeRatings += 2

        val noBeef = FoodPreferences(5, excludedIngredients = listOf("Beef"))

        val browsingService = BrowsingService(listOf(restaurant1, restaurant2))

        val eligible = browsingService.getEligibleRestaurants(
            listOf(RestaurantType.EUROPEAN),
            size = 5,
            prefs = listOf(noBeef),
            delivery = false,
            isEvent = false,
            visitingTick = 1
        )

        assertEquals(listOf(restaurant1), eligible)
    }
}
