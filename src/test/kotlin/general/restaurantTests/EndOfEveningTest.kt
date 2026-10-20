package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

// F30: End of the opening time / evening (spec p.24)
class EndOfEveningTest {

    private lateinit var potato: Ingredient
    private lateinit var restaurant: Restaurant

    @BeforeEach
    fun setUp() {
        potato = Ingredient("Potato", MeasuringUnit.G, 5)
        Ticks.init(70)
        Ticks.advanceEvening()
        Ticks.advanceTick()
        restaurant = buildRestaurant()
    }

    private fun buildRestaurant(): Restaurant {
        val supplier = Supplier(listOf(potato), mutableMapOf("Potato" to 1000))
        val staff = Staff(1, mapOf(CookType.EXEC to 1), 2)
        val recipe = Recipe(1, "Sushi", 10, mutableMapOf(potato to 1), listOf(CookType.EXEC))
        val menu = Menu(supplier, listOf(recipe), RestaurantType.ASIAN)
        val table = Table(1, TableType.COMMON, 4)
        val foh = FrontOfHouse(listOf(table), staff.getWaiters(), null, menu)
        val kitchen = Kitchen(staff.getCooks(), menu)
        return Restaurant(
            id = 1,
            name = "R1",
            type = RestaurantType.ASIAN,
            openingTickStart = 1,
            openingTickEnd = 24,
            canHostEvent = true,
            staff = staff,
            positiveRatings = 0,
            negativeRatings = 0,
            frontOfHouse = foh,
            kitchen = kitchen,
            deliveryDriverCount = 2
        )
    }

    private fun eventGroup(id: Int) = EventCustomerGroup(
        id,
        4,
        1,
        emptyList(),
        TableType.COMMON,
        emptyList(),
        4,
        mutableMapOf()
    )

    private fun deliveryGroup(id: Int) = DeliveryCustomerGroup(
        id, 2, 1, emptyList(), null, emptyList(), emptyList(), RatingLikelihood.ALWAYS, 5
    )

    private fun seatGroupDirectly(group: CustomerGroup) {
        val seatedGroups = restaurant.frontOfHouse.seatedGroups
        val table = Table(1, TableType.COMMON, 4)
        seatedGroups[group] = listOf(table)
        group.isSeated = true
    }

    @Test
    fun endOfOpeningTimeDropsNonDeliveryGroupsButKeepsDeliveries() {
        val regular = eventGroup(1)
        val delivery = deliveryGroup(2)
        restaurant.addCustomerGroup(regular)
        restaurant.addCustomerGroup(delivery)
        seatGroupDirectly(regular)

        restaurant.endOfOpeningTime()

        assertFalse(restaurant.customerGroups.contains(regular))
        assertTrue(restaurant.customerGroups.contains(delivery))
    }

    @Test
    fun endOfOpeningTimeGivesNegativeExperienceToGroupsThatDidNotFinishEating() {
        val group = eventGroup(1)
        group.peopleFinishedEating = 0 // never got their food
        restaurant.addCustomerGroup(group)
        seatGroupDirectly(group)

        restaurant.endOfOpeningTime()

        // spec p.24: "for all other groups this counts as a negative experience"
        assertEquals(-1, group.giveRating())
    }

    @Test
    fun endOfOpeningTimeDoesNotRateReservedButUnseatedGroup() {
        val group = eventGroup(1)
        restaurant.addCustomerGroup(group)
        val seatedGroups = restaurant.frontOfHouse.seatedGroups
        seatedGroups[group] = listOf(Table(1, TableType.COMMON, 4))

        restaurant.endOfOpeningTime()

        // forum 5226: leftover is people still inside, not a discarded reservation
        assertEquals(0, restaurant.negativeRatings)
        assertEquals(0, restaurant.positiveRatings)
    }

    @Test
    fun endOfEveningClearsAllCustomerGroupsIncludingDeliveries() {
        val delivery = deliveryGroup(1)
        restaurant.addCustomerGroup(delivery)

        restaurant.endOfEvening()

        assertTrue(restaurant.customerGroups.isEmpty())
    }

    @Test
    fun testEndOfEveningDelegatesToSubsystemsAndClearsCustomers() {
        val mockedFoh = mock<FrontOfHouse>()
        val mockedKitchen = mock<Kitchen>()
        val staff = Staff(1, mapOf(CookType.EXEC to 1), 2)

        // Mocked restaurant to verify interactions
        val mockRestaurant = Restaurant(
            id = 2,
            name = "MockRest",
            type = RestaurantType.EUROPEAN,
            openingTickStart = 1,
            openingTickEnd = 24,
            canHostEvent = false,
            staff = staff,
            positiveRatings = 0,
            negativeRatings = 0,
            frontOfHouse = mockedFoh,
            kitchen = mockedKitchen,
            deliveryDriverCount = 0
        )

        val group = eventGroup(10)
        mockRestaurant.addCustomerGroup(group)

        mockRestaurant.endOfEvening()

        // Verify that the restaurant triggers the correct end-of-evening lifecycle methods
        verify(mockedKitchen).staffEatLeftovers()
        verify(mockedKitchen).resetForNextEvening()
        verify(mockedFoh).endOfEvening()

        // Ensure the restaurant clears its own groups
        assertTrue(mockRestaurant.customerGroups.isEmpty())
    }

    @Test
    fun testEndOfOpeningTimeEscortsCustomersWithoutWaitstaffAction() {
        val group = eventGroup(5)
        restaurant.addCustomerGroup(group)
        seatGroupDirectly(group)

        val waiter = restaurant.staff.getWaiters().first()
        waiter.addCustomerGroup(group)
        waiter.resetTickLoad()

        restaurant.endOfOpeningTime()

        // Customers left inside at the end of the opening time are escorted immediately
        assertFalse(group.isSeated)

        // Waiters do not perform an action to escort them (tick load remains 0)
        assertEquals(0, waiter.getTickLoad(WaiterAction.ESCORT))

        // The group is released from the waiter's current load
        assertFalse(waiter.getCustomerGroups().contains(group))
    }
}
