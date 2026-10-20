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
import saarlacarte.enums.RatingLikelihood
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks

class WaitingAndGivingUpTest {

    // minimal class just to test general CustomerGroup logic
    private class TestCustomerGroup(
        id: Int,
        size: Int,
        visitingTick: Int
    ) : CustomerGroup(id, size, visitingTick, emptyList(), null) {
        override fun isVisiting(): Boolean = true
        override fun giveRating(): Int = 0
    }

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
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

        // 5 ticks later: tick 7
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

    /* @Test
    fun `waitAndGiveUp counts down waitingTicksLeft and remaining unserved guests give up`() {
        val group = TestCustomerGroup(1, 2, 10)
        val mockOrder = mock<Order>()
        val servedDish = mock<Dish>()
        whenever(servedDish.getStatus()).thenReturn(DishStatus.SERVED)
        val unservedDish = mock<Dish>()
        whenever(unservedDish.getStatus()).thenReturn(DishStatus.INPROGRESS)

        whenever(mockOrder.getDishes()).thenReturn(mutableListOf(servedDish, unservedDish))
        whenever(mockOrder.getOrderedTick()).thenReturn(1)

        while (Ticks.getTick() != 2) Ticks.advanceTick()
        group.order = mockOrder
        group.receiveOrder() // partial serve: 1 served, 1 waiting

        // partial wait begins
        Ticks.advanceTick() // 3
        var left = group.waitAndGiveUp()
        assertEquals(0, left) // waitingTicksLeft goes from 2 -> 1

        Ticks.advanceTick() // 4
        left = group.waitAndGiveUp()
        assertEquals(0, left) // waitingTicksLeft goes from 1 -> 0

        // waitingTicksLeft is now 0, the one unserved person gives up
        Ticks.advanceTick() // 5
        left = group.waitAndGiveUp()
        assertEquals(1, left)
        assertEquals(1, group.peopleLeftFrustrated)
        assertEquals(0, group.peopleWaiting)
    } */

    // Tests for specific behavior of delivery groups

    private lateinit var deliveryGroup: DeliveryCustomerGroup
    private lateinit var loggerMock: DeliveryCustomerLogger

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
    fun `giveUpCaseCheck group gives up and logs when waiting past visitingTick + 3`() {
        setUpDelivery()
        val mockOrder = mock<Order>()
        whenever(mockOrder.getId()).thenReturn(2)
        deliveryGroup.order = mockOrder

        // visitingTick (10) + 3 = tick 13
        while (Ticks.getTick() != 13) Ticks.advanceTick()

        deliveryGroup.giveUpCaseCheck()

        assertTrue(deliveryGroup.checkGaveUp())
        verify(loggerMock).deliveryGivenUp(2, 2)
    }
}
