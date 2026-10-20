package general.parserTests

import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.parser.RestaurantParser
import saarlacarte.simulation.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val PATH_PREFIX_INVALID_RESTAURANTS = "/scenarios/invalidRestaurants/"
private const val PATH_PREFIX_RESTAURANTS = "/scenarios/restaurantFiles/"

class ParseRestaurantTest {
    val invalidRestaurantsPathPrefix: String = System.getProperty("user.dir") + PATH_PREFIX_INVALID_RESTAURANTS
    val validRestaurantsPathPrefix: String = System.getProperty("user.dir") + PATH_PREFIX_RESTAURANTS
    val restaurantParser = RestaurantParser()
    val chicken = Ingredient("chicken", MeasuringUnit.G, 2)
    val rice = Ingredient("rice", MeasuringUnit.G, 3)
    val potato = Ingredient("potato", MeasuringUnit.X, 5)
    val recipes = listOf(
        Recipe(
            1,
            "dish one",
            20,
            mutableMapOf(chicken to 200, potato to 2),
            listOf(CookType.EXEC, CookType.ROAST),
            RestaurantType.ASIAN
        ),
        Recipe(
            2,
            "dish two",
            30,
            mutableMapOf(rice to 100, potato to 4),
            listOf(CookType.VEGETABLE, CookType.ROAST, CookType.TOURNANT),
            RestaurantType.EUROPEAN
        ),
        Recipe(
            3,
            "dish three",
            30,
            mutableMapOf(rice to 100, potato to 1, chicken to 100),
            listOf(CookType.PASTRY, CookType.ROAST, CookType.TOURNANT),
            RestaurantType.AFRICAN
        ),
        Recipe(
            4,
            "dish four",
            10,
            mutableMapOf(rice to 100, chicken to 400),
            listOf(CookType.SOUS, CookType.ROAST, CookType.TOURNANT),
            RestaurantType.AMERICAN
        )
    )
    val supplier = Supplier(
        listOf(chicken, rice, potato),
        mutableMapOf("chicken" to 200, "rice" to 800, "potato" to 15)
    )

    @Test
    fun testZeroRestaurants() {
        val fullPath = invalidRestaurantsPathPrefix + "zeroRestaurants.json"
        assert(
            restaurantParser.parse(
                fullPath,
                supplier,
                recipes
            ) == null
        )
    }

    @Test
    fun testNotUniqueIdRestaurants() {
        assert(
            restaurantParser.parse(
                invalidRestaurantsPathPrefix + "restaurantsNotUniqueId.json",
                supplier,
                recipes
            ) == null
        )
    }

    @Test
    fun testNotUniqueNameRestaurants() {
        assert(
            restaurantParser.parse(
                invalidRestaurantsPathPrefix + "restaurantsNotUniqueName.json",
                supplier,
                recipes
            ) == null
        )
    }

    @Test
    fun testTableUniqueIds() {
        assert(
            restaurantParser.parse(
                invalidRestaurantsPathPrefix + "restaurantsNotUniqueTableIds.json",
                supplier,
                recipes
            ) == null
        )
    }

    @Test
    fun testUniqueDishNameRestaurant() {
        val recipesDuplicateDishNames = listOf(
            Recipe(
                1,
                "dish one",
                20,
                mutableMapOf(chicken to 200, potato to 2),
                listOf(CookType.EXEC, CookType.ROAST),
                RestaurantType.ASIAN
            ),
            Recipe(
                2,
                "duplicate name",
                30,
                mutableMapOf(rice to 100, potato to 1, chicken to 100),
                listOf(CookType.PASTRY, CookType.ROAST, CookType.TOURNANT),
                RestaurantType.EUROPEAN
            ),
            Recipe(
                4,
                "duplicate name",
                10,
                mutableMapOf(rice to 100, chicken to 400),
                listOf(CookType.SOUS, CookType.ROAST, CookType.TOURNANT),
                RestaurantType.AMERICAN
            )
        )
        assert(
            restaurantParser.parse(
                validRestaurantsPathPrefix + "europeanRestaurant.json",
                supplier,
                recipesDuplicateDishNames
            ) == null
        )
    }

    @Test
    fun testUseExistingRecipeRestaurant() {
        val recipesTooFew = listOf(
            Recipe(
                1,
                "dish one",
                20,
                mutableMapOf(chicken to 200, potato to 2),
                listOf(CookType.EXEC, CookType.ROAST),
                RestaurantType.EUROPEAN
            ),
            Recipe(
                2,
                "name",
                30,
                mutableMapOf(rice to 100, potato to 1, chicken to 100),
                listOf(CookType.PASTRY, CookType.ROAST, CookType.TOURNANT),
                RestaurantType.AFRICAN
            ),
            Recipe(
                3,
                "dish name",
                10,
                mutableMapOf(rice to 100, chicken to 400),
                listOf(CookType.SOUS, CookType.ROAST, CookType.TOURNANT),
                RestaurantType.AMERICAN
            )
        )
        assert(
            restaurantParser.parse(
                validRestaurantsPathPrefix + "europeanRestaurant.json",
                supplier,
                recipesTooFew
            ) == null
        )
    }

    @Test
    fun testCreationOfValidRestaurant() {
        val recipes = listOf(
            Recipe(
                1,
                "dish one",
                20,
                mutableMapOf(chicken to 200, potato to 2),
                listOf(CookType.EXEC, CookType.ROAST),
                RestaurantType.ASIAN
            ),
            Recipe(
                2,
                "name",
                30,
                mutableMapOf(rice to 100, potato to 1, chicken to 100),
                listOf(CookType.PASTRY, CookType.ROAST, CookType.TOURNANT),
                RestaurantType.AFRICAN
            ),
            Recipe(
                3,
                "dish name",
                10,
                mutableMapOf(rice to 100, chicken to 400),
                listOf(CookType.SOUS, CookType.ROAST, CookType.TOURNANT),
                RestaurantType.AMERICAN
            )
        )
        val restaurants = restaurantParser.parse(
            validRestaurantsPathPrefix + "restaurantsNoRecipes.json",
            supplier,
            recipes
        )
        assertEquals(1, restaurants?.size ?: 0)
        val restaurant = restaurants?.first()
        checkNotNull(restaurant)
        assertEquals("No cooks", restaurant.name)
        assertEquals(24, restaurant.openingTickEnd)
        assertEquals(1, restaurant.openingTickStart)
        assertNotNull(restaurant.frontOfHouse)
        assertEquals(false, restaurant.canHostEvent)
        assertEquals(RestaurantType.ASIAN, restaurant.type)
        assertEquals(5, restaurant.positiveRatings)
        assertEquals(1, restaurant.negativeRatings)
        assertEquals(2, restaurant.frontOfHouse.availableSeats(TableType.COMMON))
        assertEquals(2, restaurant.frontOfHouse.freeSeatCount())
        val menu = restaurant.kitchen.menu
        assertEquals(1, menu.recipes.size)
        assertEquals(1, menu.recipes.first().id)

        val staff = restaurant.staff
        assertEquals(CookType.EXEC, staff.getCooks().first().getType())
        assertEquals(1, restaurant.kitchen.cooks.size)
        assertSame(restaurant.kitchen.cooks, staff.getCooks())
        assertTrue { staff.getCooks().all { it.getId() == null } }
        assertEquals(3, staff.getWaiters().size)
        assertTrue { staff.getWaiters().all { it.getId() == null } }
    }

    @Test
    fun testNoCooks() {
        assertNull(
            restaurantParser.parse(
                invalidRestaurantsPathPrefix + "restaurantNoCooks.json",
                supplier,
                recipes
            )
        )
    }

    @Test
    fun `test - openingTickStart less than openingTickEnd`() {
        assertNull(
            restaurantParser.parse(
                invalidRestaurantsPathPrefix + "restaurantWrongOpeningTicks.json",
                supplier,
                recipes
            )
        )
    }
    // check more ideas in corresponding system tests
}
