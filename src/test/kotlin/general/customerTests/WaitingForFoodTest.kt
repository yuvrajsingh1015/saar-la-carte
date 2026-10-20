package general.customerTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.enums.DishStatus
import saarlacarte.enums.RatingLikelihood
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks

class WaitingForFoodTest {

    // minimal class just to test general CustomerGroup logic
    private class TestCustomerGroup(
        id: Int,
        size: Int,
        visitingTick: Int
    ) : CustomerGroup(id, size, visitingTick, emptyList(), null) {
        override fun isVisiting(): Boolean = true
        override fun giveRating(): Int = 0
    }

    private lateinit var deliveryGroup: DeliveryCustomerGroup
    private lateinit var loggerMock: DeliveryCustomerLogger

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
    }

    private fun setUpDelivery() {
        loggerMock = mock()
        deliveryGroup = DeliveryCustomerGroup(
            id = 2,
            size = 2,
            visitingTick = 10,
            customerPreferences = emptyList(),
            preferredTableType = null,
            restaurantTypes = emptyList(),
            visitingEvenings = listOf(1),
            ratingLikelihood = RatingLikelihood.ALWAYS,
            deliveryDistance = 10
        )
        deliveryGroup.setLogger(loggerMock)
    }

    @Test
    fun `waitAndGiveUp does nothing if no order was placed`() {
        Ticks.advanceTick()
        val group = TestCustomerGroup(1, 2, 1)

        val left = group.waitAndGiveUp()

        assertEquals(0, left)
        assertFalse(group.hasLeftVisit)
    }

    @Test
    fun `waitAndGiveUp everyone leaves when nobody is served after 5 ticks`() {
        val group = TestCustomerGroup(1, 3, 10)
        val mockOrder = mock<Order>()
        whenever(mockOrder.getOrderedTick()).thenReturn(2)
        group.order = mockOrder

        // 5 ticks later: tick 7[cite: 2]
        while (Ticks.getTick() != 7) Ticks.advanceTick()

        val left = group.waitAndGiveUp()

        assertEquals(3, left)
        assertTrue(group.hasLeftVisit)
    }

    @Test
    fun `waitAndGiveUp no one leaves when nobody is served before 4 ticks`() {
        val group = TestCustomerGroup(1, 3, 10)
        val mockOrder = mock<Order>()
        whenever(mockOrder.getOrderedTick()).thenReturn(2)
        group.order = mockOrder

        // 3 ticks later: tick 5
        while (Ticks.getTick() != 5) Ticks.advanceTick()

        val left = group.waitAndGiveUp()

        assertEquals(0, left)
        assertFalse(group.hasLeftVisit)
    }

    @Test
    fun `giveUpCaseCheck group gives up and logs when waiting past visitingTick + 3`() {
        setUpDelivery()
        val mockOrder = mock<Order>()
        whenever(mockOrder.getId()).thenReturn(2)
        deliveryGroup.order = mockOrder

        // visitingTick (10) + 3 = tick 13[cite: 2]
        while (Ticks.getTick() != 13) Ticks.advanceTick()

        deliveryGroup.giveUpCaseCheck()

        assertTrue(deliveryGroup.checkGaveUp())
        verify(loggerMock).deliveryGivenUp(2, 2)
    }

    @Test
    fun `eating takes exactly 2 full ticks after receiving food`() {
        val group = TestCustomerGroup(1, 2, 1)
        val mockOrder = mock<Order>()
        val dish1 = mock<Dish>()
        val dish2 = mock<Dish>()
        whenever(dish1.getStatus()).thenReturn(DishStatus.SERVED)
        whenever(dish2.getStatus()).thenReturn(DishStatus.SERVED)
        whenever(mockOrder.getDishes()).thenReturn(mutableListOf(dish1, dish2))

        group.order = mockOrder
        group.receiveOrder()

        // 1st tick of eating (Transition from state 3 to 2)
        val eatingTick1 = group.eat()
        assertEquals(2, eatingTick1.first)
        assertEquals(0, eatingTick1.second)

        // 2nd full tick of eating (Transition from state 2 to 1)
        val eatingTick2 = group.eat()
        assertEquals(2, eatingTick2.first)
        assertEquals(0, eatingTick2.second)

        // 3rd tick: Eating finished
        val eatingTick3 = group.eat()
        assertEquals(0, eatingTick3.first)
        assertEquals(2, eatingTick3.second)
    }

    @Test
    fun `waitAndGiveUp triggers partial grace period causing only unserved customers to leave`() {
        val group = TestCustomerGroup(1, 2, 1)
        val mockOrder = mock<Order>()
        whenever(mockOrder.getOrderedTick()).thenReturn(1)

        val servedDish = mock<Dish>()
        whenever(servedDish.getStatus()).thenReturn(DishStatus.SERVED)
        val unservedDish = mock<Dish>()
        whenever(unservedDish.getStatus()).thenReturn(DishStatus.INPROGRESS)

        whenever(mockOrder.getDishes()).thenReturn(mutableListOf(servedDish, unservedDish))
        group.order = mockOrder

        while (Ticks.getTick() != 3) Ticks.advanceTick()

        // Partial serve occurs at tick 3
        group.receiveOrder()

        var left = 0
        // Advance ticks until the unserved customer gives up after the grace period
        // FIXED: Using '_' instead of 'i' to resolve the Detekt UnusedPrivateProperty/Member warning
        var count = 0
        while (count < 5) {
            Ticks.advanceTick()
            left = group.waitAndGiveUp()
            if (left > 0) break
            count++
        }

        // Only the 1 unserved person leaves; the group as a whole stays because 1 is eating
        assertEquals(1, left)
        assertEquals(1, group.peopleLeftFrustrated)
        assertEquals(0, group.peopleWaiting)
        assertFalse(group.hasLeftVisit)
    }

    @Test
    fun `deliveryFinishedEating is logged when the delivery group finishes eating`() {
        setUpDelivery()
        val mockOrder = mock<Order>()
        val dish1 = mock<Dish>()
        val dish2 = mock<Dish>()
        whenever(dish1.getStatus()).thenReturn(DishStatus.SERVED)
        whenever(dish2.getStatus()).thenReturn(DishStatus.SERVED)
        whenever(mockOrder.getDishes()).thenReturn(mutableListOf(dish1, dish2))

        deliveryGroup.order = mockOrder
        deliveryGroup.receiveOrder()

        // Advance through the eating ticks
        deliveryGroup.eat()
        deliveryGroup.eat()
        deliveryGroup.eat()

        // Verifies the exact logging condition for delivery groups finishing their meals[cite: 2]
        assertEquals(2, deliveryGroup.peopleFinishedEating)
        verify(loggerMock).deliveryFinishedEating(deliveryGroup.id)
    }
}
