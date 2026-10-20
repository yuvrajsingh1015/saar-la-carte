package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.Cook
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.to

class CookingStaffTest {
    private val chicken = Ingredient("chicken", MeasuringUnit.G, 1)
    private val rice = Ingredient("rice", MeasuringUnit.G, 3)
    val potato = Ingredient("Potato", MeasuringUnit.G, 5)
    val tomato = Ingredient("Tomato", MeasuringUnit.X, 4)
    private val chickenRice = Recipe(
        1,
        "Chicken Rice",
        15,
        mutableMapOf(rice to 1, chicken to 1),
        listOf(CookType.TOURNANT, CookType.SOUS, CookType.EXEC),
        RestaurantType.EUROPEAN
    )
    private val chickenRiceCreative = Recipe(
        2,
        "Chicken Rice 2",
        15,
        mutableMapOf(rice to 1, chicken to 1),
        listOf(CookType.SOUS, CookType.EXEC, CookType.SAUCE)
    )
    val pizzaRecipe = Recipe(
        3,
        "Pizza",
        15,
        mutableMapOf(tomato to 2, potato to 100),
        listOf(CookType.EXEC, CookType.ROAST),
        RestaurantType.EUROPEAN
    )
    val sushiRecipe = Recipe(
        4,
        "Sushi",
        10,
        mutableMapOf(rice to 150),
        listOf(CookType.EXEC, CookType.FISH),
        RestaurantType.ASIAN
    )
    private val supplier = Supplier(
        listOf(potato, tomato, rice, chicken),
        mutableMapOf(
            "Potato" to 1000,
            "Tomato" to 10,
            "Rice" to 500,
            "chicken" to 400,
            "Beef" to 300,
            "rice" to 1
        )
    )
    val recipes = listOf(chickenRiceCreative, chickenRice, pizzaRecipe, sushiRecipe)
    private val menu = Menu(supplier, recipes, RestaurantType.EUROPEAN)
    private val cooks =
        mutableListOf(Cook(CookType.SAUCE), Cook(CookType.TOURNANT), Cook(CookType.SOUS), Cook(CookType.EXEC))
    private var kitchen = Kitchen(cooks, menu)
    private val dishChicken = Dish(chickenRice, DishStatus.NOTSTARTED, true)
    private val dishChicken2 = Dish(chickenRiceCreative, DishStatus.NOTSTARTED, false)
    private val dishPizza = Dish(pizzaRecipe, DishStatus.NOTSTARTED, true)
    private val dishSushi = Dish(sushiRecipe, DishStatus.NOTSTARTED, false)
    private val order = Order(1, mutableListOf(dishChicken, dishChicken2), false, 0, 1)

    @BeforeEach
    fun setup() {
        cooks.clear()
        cooks.addAll(listOf(Cook(CookType.SAUCE), Cook(CookType.TOURNANT), Cook(CookType.SOUS), Cook(CookType.EXEC)))
        kitchen = Kitchen(cooks, menu)
    }

    // tests
    @Test
    fun testCookAssignId() {
        cooks.first().assignId(1)
        assertEquals(1, cooks.first().getId())
    }

    @Test
    fun testCanCook() {
        val tournant = cooks.first { it.getType() == CookType.TOURNANT }
        assertEquals(
            listOf(chickenRice),
            tournant.canCook(listOf(chickenRice, chickenRiceCreative))
        )
    }

    @Test
    fun testCookTypeOrdering() {
        // ordered by ascending priority
        val orderedCookTypes = listOf(
            CookType.EXEC,
            CookType.SOUS,
            CookType.TOURNANT,
            CookType.SAUCE,
            CookType.FISH,
            CookType.ROAST,
            CookType.VEGETABLE,
            CookType.PASTRY
        )
        val easyRecipe = Recipe(
            1,
            "easy recipe",
            10,
            mutableMapOf(tomato to 1),
            orderedCookTypes
        )
        val easyMenu = Menu(supplier, listOf(easyRecipe), RestaurantType.EUROPEAN)
        kitchen = Kitchen(cooks, easyMenu)

        for (i in 1..<orderedCookTypes.size) {
            cooks.clear()
            cooks.add(Cook(orderedCookTypes[i - 1]))
            cooks.add(Cook(orderedCookTypes[i]))

            val easyOrder = Order(
                1,
                mutableListOf(Dish(easyRecipe, DishStatus.NOTSTARTED)),
                false,
                null,
                1
            )
            kitchen.registerOrders(listOf(easyOrder))
            kitchen.assignAndCook()
            assertEquals(null, cooks.first().getId())
            assertEquals(1, cooks.last().getId())
            kitchen.abortMealsOf(easyOrder)
        }
    }

    // tests
    @Test
    fun testCookAssignmentOrder() {
        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()
        val cooks = kitchen.cooks

        for (cook in cooks) {
            if (cook.getId() == null) {
                assertTrue(cook.getType() == CookType.SOUS || cook.getType() == CookType.EXEC) {
                    "cook of type ${cook.getType()} has id ${cook.getId() ?: 0}"
                }
            } else {
                assertTrue(cook.getType() == CookType.TOURNANT || cook.getType() == CookType.SAUCE)
            }
        }

        val tournant = requireNotNull(cooks.find { it.getType() == CookType.TOURNANT }) {
            "Tournant cook not found!"
        }
        assertEquals(1, tournant.getId())

        assertEquals(chickenRice, tournant.getCurrentRecipe())

        val sauce = requireNotNull(cooks.find { it.getType() == CookType.SAUCE }) {
            "sauce cook not found!"
        }
        assertEquals(2, sauce.getId())
    }

    @Test
    fun correctDishOrderAssignmentBasic() {
        val order2 = Order(1, mutableListOf(dishPizza, dishSushi), false, 0, 1)
        kitchen.registerOrders(listOf(order2))
        kitchen.assignAndCook()
        val cook = cooks.find { it.getType() == CookType.EXEC }
        val cooked = cook?.getCurrentRecipe()
        assertTrue(cooked == pizzaRecipe)
    }

    @Test
    fun correctDishOrderAssignmentID() {
        // Create a new kitchen with exactly one EXEC cook
        val execCook = Cook(CookType.EXEC)
        val singleCookKitchen = Kitchen(mutableListOf(execCook), menu)

        // Create an order with Pizza first, then ChickenRice
        val priorityOrder = Order(2, mutableListOf(dishPizza, dishChicken), false, 0, 1)

        singleCookKitchen.registerOrders(listOf(priorityOrder))
        singleCookKitchen.assignAndCook()

        // The EXEC cook is the only one in the kitchen, so we can just grab first()
        val assignedCook = singleCookKitchen.cooks.first()

        assertEquals(
            chickenRice,
            assignedCook.getCurrentRecipe(),
            "The EXEC cook should have been assigned Chicken Rice instead of Pizza"
        )
    }

    @Test
    fun testDishIdPriorityWithAllCookTypes() {
        // All 8 cook types from your enum
        val allCookTypes = listOf(
            CookType.EXEC,
            CookType.SOUS,
            CookType.TOURNANT,
            CookType.SAUCE,
            CookType.FISH,
            CookType.ROAST,
            CookType.VEGETABLE,
            CookType.PASTRY
        )

        // 1. Create 8 cooks (exactly one of each type)
        val testCooks = allCookTypes.map { Cook(it) }.toMutableList()

        // 2. Create 8 identical recipes, but with different names and IDs (1 to 8)
        // We pass `allCookTypes` so every single recipe can be cooked by ANY cook.
        val testRecipes = (1..8).map { id ->
            Recipe(
                id,
                "Universal Dish $id",
                15,
                mutableMapOf(potato to 1), // Using your existing potato ingredient
                allCookTypes,
                RestaurantType.EUROPEAN
            )
        }

        val testMenu = Menu(supplier, testRecipes, RestaurantType.EUROPEAN)
        val testKitchen = Kitchen(testCooks, testMenu)

        val testDishes = testRecipes.map { Dish(it, DishStatus.NOTSTARTED, false) }
            .reversed()
            .toMutableList()

        val testOrder = Order(3, testDishes, false, 0, 1)

        testKitchen.registerOrders(listOf(testOrder))
        testKitchen.assignAndCook()

        val busyCooks = testKitchen.cooks.filter { it.getCurrentRecipe() != null }

        assertEquals(8, busyCooks.size, "All 8 cooks should have grabbed a dish")

        val assignedRecipeIds = testCooks.mapNotNull { it.getId() }

        assertEquals(
            listOf(8, 7, 6, 5, 4, 3, 2, 1),
            assignedRecipeIds,
            "Dishes were not assigned in the correct Recipe ID order!"
        )
    }

    @Test
    fun testReusingCookWithAssignedIdOverUnassignedCookOfSameType() {
        // Recipe with duration <= 10 finishes within the same tick
        val quickSoup = Recipe(
            10,
            "Quick Soup",
            10,
            mutableMapOf(potato to 1),
            listOf(CookType.TOURNANT),
            RestaurantType.EUROPEAN
        )
        val customMenu = Menu(supplier, listOf(quickSoup), RestaurantType.EUROPEAN)
        val tournant1 = Cook(CookType.TOURNANT)
        val tournant2 = Cook(CookType.TOURNANT)
        val testKitchen = Kitchen(mutableListOf(tournant1, tournant2), customMenu)

        // Tick 1: Register and cook first order
        val order1 = Order(1, mutableListOf(Dish(quickSoup, DishStatus.NOTSTARTED)), false, null, 1)
        testKitchen.registerOrders(listOf(order1))
        testKitchen.assignAndCook()

        // One cook was assigned, received ID 1, and finished cooking
        val assignedCook = if (tournant1.getId() != null) tournant1 else tournant2
        val unassignedCook = if (tournant1.getId() == null) tournant1 else tournant2

        assertEquals(1, assignedCook.getId(), "First working cook should receive ID 1")
        assertNull(unassignedCook.getId(), "Unused cook should not receive an ID")
        assertTrue(assignedCook.isFree(), "Cook should be free again after finishing the dish")

        // Tick 2: Register second order
        Ticks.advanceTick()
        val order2 = Order(2, mutableListOf(Dish(quickSoup, DishStatus.NOTSTARTED)), false, null, 2)
        testKitchen.registerOrders(listOf(order2))
        testKitchen.assignAndCook()

        // The cook that already possesses an ID must be preferred over the unassigned cook
        assertEquals(1, assignedCook.getId(), "Assigned cook keeps ID 1")
        assertNull(unassignedCook.getId(), "Unassigned cook should still have no ID assigned")
        assertEquals(DishStatus.FINISHED, order2.getDishes().first().getStatus())
    }

    @Test
    fun testBusyCookCannotTakeParallelJobAndResetsForNextEvening() {
        // chickenRice takes duration 15 (> 10), so it stays in progress into the next tick
        val tournant = Cook(CookType.TOURNANT)
        val singleCookKitchen = Kitchen(mutableListOf(tournant), menu)

        val order1 = Order(1, mutableListOf(Dish(chickenRice, DishStatus.NOTSTARTED)), false, null, 1)
        singleCookKitchen.registerOrders(listOf(order1))
        singleCookKitchen.assignAndCook()

        assertEquals(1, tournant.getId(), "Cook should be assigned ID 1 upon starting work")
        assertFalse(tournant.isFree(), "Cook should not be free while cooking multi-tick dish")
        assertEquals(chickenRice, tournant.getCurrentRecipe())

        // Attempt to queue a different dish while the single cook is still busy
        val order2 = Order(2, mutableListOf(Dish(chickenRiceCreative, DishStatus.NOTSTARTED)), false, null, 1)
        singleCookKitchen.registerOrders(listOf(order2))
        singleCookKitchen.assignAndCook()

        // The busy cook could not take the job, so order2's dish remains NOTSTARTED
        assertEquals(DishStatus.NOTSTARTED, order2.getDishes().first().getStatus())

        // Evening reset: staff must drop IDs and clear remaining job bindings
        singleCookKitchen.resetForNextEvening()
        assertNull(tournant.getId(), "Cook ID must be reset to null for the next evening")
        assertTrue(tournant.isFree(), "Cook must be free after evening reset")
        assertNull(tournant.getCurrentRecipe(), "Cook must have no recipe assigned after reset")
    }
}
