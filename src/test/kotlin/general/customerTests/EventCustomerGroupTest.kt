package general.customerTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.restaurant.Restaurant
import saarlacarte.simulation.BrowsingService
import saarlacarte.simulation.Ticks

class EventCustomerGroupTest {
    private lateinit var group: EventCustomerGroup
    private lateinit var browsingServiceMock: BrowsingService
    private lateinit var foodPreferencesMock: FoodPreferences

    private val defaultTableType = TableType.COMMON
    private val defaultRestaurantType = RestaurantType.EUROPEAN

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        browsingServiceMock = mock()
        foodPreferencesMock = mock()

        group = EventCustomerGroup(
            id = 1,
            size = 4,
            visitingTick = 1,
            customerPreferences = listOf(foodPreferencesMock),
            preferredTableType = defaultTableType,
            restaurantTypes = listOf(defaultRestaurantType),
            eventEvening = 5,
            favoriteBasicDish = mutableMapOf(defaultRestaurantType to "Pizza")
        )
    }

    @Test
    fun `isVisiting returns true only when current evening == eventEvening`() {
        // visitingTick = 1, eventEvening = 5
        Ticks.advanceTick()
        while (Ticks.getEvening() != 5) Ticks.advanceEvening()
        assertTrue(group.isVisiting())

        Ticks.advanceEvening()
        assertFalse(group.isVisiting())
    }

    @Test
    fun `isEvent is always returns true`() {
        assertTrue(group.isEvent())
    }

    @Test
    fun `isReadyToDecide returns true on event tick 3 evenings prior`() {
        // visitingTick = 1, eventEvening = 5
        while (Ticks.getEvening() != group.eventEvening - 3) Ticks.advanceEvening()
        Ticks.advanceTick()
        assertTrue(group.isReadyToDecide())

        // correct evening, wrong tick
        Ticks.advanceTick()
        assertFalse(group.isReadyToDecide())

        // correct tick, wrong evening
        Ticks.init(24)
        Ticks.advanceTick()
        Ticks.advanceEvening()
        assertFalse(group.isReadyToDecide())
    }

    @Test
    fun `decideRestaurant returns null when BrowsingService is null`() {
        assertNull(group.decideRestaurant())
    }

    @Test
    fun `decideRestaurant returns null when no eligible restaurants are found`() {
        group.setBrowsingService(browsingServiceMock)

        whenever(
            browsingServiceMock.getEligibleRestaurants(
                group.restaurantTypes,
                group.preferredTableType,
                group.size,
                group.customerPreferences,
                false,
                true,
                group.visitingTick,
            )
        ).thenReturn(emptyList())

        assertNull(group.decideRestaurant())
        assertNull(group.getReservedRestaurant())
    }

    @Test
    fun `decideRestaurant succeeds, makes final choice, and triggers reservation`() {
        group.setBrowsingService(browsingServiceMock)
        val restaurant = mock<Restaurant>()

        whenever(
            browsingServiceMock.getEligibleRestaurants(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(listOf(restaurant))
        val chosen = group.decideRestaurant()

        assertEquals(restaurant, chosen)
        assertEquals(restaurant, group.getReservedRestaurant())
        verify(browsingServiceMock).addReservation(restaurant, group)
    }

    @Test
    fun `test giveRating based on customerExperience`() {
        // Assign the state property directly on the real object
        setExperience(group, CustomerExperience.NEGATIVE)
        assertEquals(-1, group.giveRating())

        setExperience(group, CustomerExperience.POSITIVE)
        assertEquals(1, group.giveRating())

        setExperience(group, CustomerExperience.NEUTRAL)
        assertEquals(1, group.giveRating())
    }

    private fun setExperience(group: CustomerGroup, experience: CustomerExperience) {
        val field = CustomerGroup::class.java.getDeclaredField("customerExperience")
        field.isAccessible = true
        field.set(group, experience)
    }

    private fun setReservedRestaurant(group: EventCustomerGroup, restaurant: Restaurant?) {
        val field = EventCustomerGroup::class.java.getDeclaredField("reservedRestaurant")
        field.isAccessible = true
        field.set(group, restaurant)
    }

    @Test
    fun `favorite dish takes precedence over personal preferences`() {
        val restaurant = createMockRestaurant(defaultRestaurantType)
        setReservedRestaurant(group, restaurant)

        val eventDish = createMockRecipe(1, "Pizza", listOf("cheese", "dough"))
        val personalFavDish = createMockRecipe(2, "Pasta", listOf("pasta", "tomato"))
        val recipes = listOf(eventDish, personalFavDish)

        val preferences = FoodPreferences(
            size = 4,
            excludedIngredients = emptyList(),
            preferredIngredients = listOf("pasta"),
            favoriteDishes = listOf("Pasta")
        )

        val selected = group.selectDishForEach(recipes, preferences)
        assertEquals(eventDish, selected)
    }

    @Test
    fun `when the favorite dish has excluded ingredients, decide based on personal preferences`() {
        val restaurant = createMockRestaurant(defaultRestaurantType)
        setReservedRestaurant(group, restaurant)

        val eventDish = createMockRecipe(1, "Pizza", listOf("cheese", "dough"))
        val personalFavDish = createMockRecipe(2, "Pasta", listOf("pasta", "tomato"))
        val recipes = listOf(eventDish, personalFavDish)

        val preferences = FoodPreferences(
            size = 4,
            excludedIngredients = listOf("cheese"),
            preferredIngredients = emptyList(),
            favoriteDishes = listOf("Pasta")
        )

        val selected = group.selectDishForEach(recipes, preferences)
        assertEquals(personalFavDish, selected)
    }

    @Test
    fun `tiebreaker - when preferred ingredients tie, select recipe with highest id`() {
        val restaurant = createMockRestaurant(defaultRestaurantType)
        setReservedRestaurant(group, restaurant)

        // Event dish is "Pizza", which is absent from the menu
        val dishA = createMockRecipe(10, "Burger", listOf("garlic"))
        val dishB = createMockRecipe(20, "Steak", listOf("garlic"))
        val recipes = listOf(dishA, dishB)

        val preferences = FoodPreferences(
            size = 4,
            excludedIngredients = emptyList(),
            preferredIngredients = listOf("garlic"),
            favoriteDishes = emptyList()
        )

        val selected = group.selectDishForEach(recipes, preferences)
        assertEquals(dishB, selected)
    }

    @Test
    fun `return null when all dishes have excluded ingredients`() {
        val restaurant = createMockRestaurant(defaultRestaurantType)
        setReservedRestaurant(group, restaurant)

        val dishA = createMockRecipe(1, "Pizza", listOf("cheese"))
        val dishB = createMockRecipe(2, "Pasta", listOf("gluten"))
        val recipes = listOf(dishA, dishB)

        val preferences = FoodPreferences(
            size = 4,
            excludedIngredients = listOf("cheese", "gluten"),
            preferredIngredients = emptyList(),
            favoriteDishes = emptyList()
        )

        val selected = group.selectDishForEach(recipes, preferences)
        assertNull(selected)
    }

    private fun createMockIngredient(name: String): Ingredient {
        val ingredientMock = mock<Ingredient>()
        whenever(ingredientMock.getName()).thenReturn(name)
        return ingredientMock
    }

    private fun createMockRecipe(
        id: Int,
        dishName: String,
        ingredientNames: List<String>
    ): Recipe {
        val ingredientMap = ingredientNames.associate { name ->
            createMockIngredient(name) to 100
        }
        val recipeMock = mock<Recipe>()
        whenever(recipeMock.id).thenReturn(id)
        whenever(recipeMock.dishName).thenReturn(dishName)
        whenever(recipeMock.ingredients).thenReturn(ingredientMap.toMutableMap())
        return recipeMock
    }

    private fun createMockRestaurant(type: RestaurantType): Restaurant {
        val restaurantMock = mock<Restaurant>()
        whenever(restaurantMock.type).thenReturn(type)
        return restaurantMock
    }
}
