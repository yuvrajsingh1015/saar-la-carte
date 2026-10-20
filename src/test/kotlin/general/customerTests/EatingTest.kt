package general.customerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.enums.DishStatus
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Ticks
import kotlin.test.assertEquals

// to test: processEating, eat, waitAndGiveUp

class EatingTest {
    val table = Table(1, TableType.COMMON, 7)
    var waiter = Waiter()
    val foh = FrontOfHouse(
        listOf(table),
        mutableListOf(waiter),
        null,
        mock<Menu>()
    )
    val casuals = CasualCustomerGroup(
        1,
        7,
        1,
        listOf(),
        TableType.COMMON,
        listOf(RestaurantType.AFRICAN),
        listOf(1),
        RatingLikelihood.SOME
    )
    val dishes = mutableListOf<Dish>()
    val order = Order(1, dishes, false, 0, 1)

    fun createDishWithStatus(status: DishStatus): Dish {
        return Dish(mock(), status)
    }

    fun compareEatingToExpected(expectedFinished: Int, expectedEating: Int) {
        casuals.eat().let { (newEating, newFinished) ->
            val eating = newEating
            val finished = newFinished
            casuals.waitAndGiveUp()
            assertEquals(expectedFinished, finished, "wrong estimated finished people in tick ${Ticks.getTick()}")
            assertEquals(expectedEating, eating, "wrong estimated eating people in tick ${Ticks.getTick()}")
        }
        Ticks.advanceTick()
    }

    @BeforeEach
    fun seatCasualsAndTickSetup() {
        Ticks.init(1000)
        Ticks.advanceEvening()
        Ticks.advanceTick()
        waiter = Waiter()
        foh.seatCustomers(listOf(casuals))
        casuals.order = order
        dishes.clear()
    }

    @Test
    fun testReceivingIncompleteOrder() {
        // only 4/7 dishes are served (in the ordered tick)
        repeat(4) {
            dishes.add(createDishWithStatus(DishStatus.SERVED))
        }
        repeat(3) {
            dishes.add(createDishWithStatus(DishStatus.INPROGRESS))
        }
        casuals.receiveOrder()
        casuals.eat()
        assertEquals(4, casuals.howManyEating())
        assertEquals(3, casuals.peopleWaiting)
        assertEquals(7, casuals.howManySitting())
    }

    @Test
    fun testReceivingCompleteOrder() {
        // all 7 dishes are served (in the ordered tick)
        repeat(7) {
            dishes.add(createDishWithStatus(DishStatus.SERVED))
        }
        casuals.receiveOrder()
        assertEquals(7, casuals.howManyEating(), "wrong estimation of people eating")
        assertEquals(0, casuals.peopleWaiting, "wrong estimation of waiting people")
        assertEquals(7, casuals.howManySitting(), "wrong estimation of sitting people")
    }

    @Test
    fun testEatingDurationServedSimultaneously() {
        // tick 1
        casuals.startWaiting4Food()
        repeat(7) {
            dishes.add(createDishWithStatus(DishStatus.SERVED))
        }
        casuals.receiveOrder()
        compareEatingToExpected(0, 7)
        // tick 2
        compareEatingToExpected(0, 7)
        // tick 3
        compareEatingToExpected(7, 0)
    }

    @Test
    fun `test eating duration after receiving orders at different times`() {
        // tick 1
        repeat(7) {
            dishes.add(createDishWithStatus(DishStatus.NOTSTARTED))
        }
        casuals.startWaiting4Food()
        compareEatingToExpected(0, 0)
        // tick 2
        compareEatingToExpected(0, 0)
        // tick 3: serve partially
        dishes.first().setStatus(DishStatus.SERVED)
        casuals.receiveOrder()
        compareEatingToExpected(0, 1)
        // tick 4: serve remaining
        dishes.forEach { it.setStatus(DishStatus.SERVED) }
        casuals.receiveOrder()
        compareEatingToExpected(0, 7)
        // tick 5
        compareEatingToExpected(1, 6)
        // tick 6
        compareEatingToExpected(7, 0)
    }

    @Test
    fun `test giving up waiting when no one served`() {
        // tick 1
        repeat(7) {
            dishes.add(createDishWithStatus(DishStatus.NOTSTARTED))
        }
        casuals.startWaiting4Food()
        repeat(4) {
            compareEatingToExpected(0, 0)
        }
        // tick 5 (order + 4)
        val leftNotServed = foh.processEating()
        assertEquals(listOf(casuals), leftNotServed)
    }

    @Test
    fun `test giving up waiting when partially served`() {
        // tick 1
        repeat(7) {
            dishes.add(createDishWithStatus(DishStatus.NOTSTARTED))
        }
        casuals.startWaiting4Food()
        compareEatingToExpected(0, 0)
        // tick 2
        compareEatingToExpected(0, 0)
        // tick 3
        compareEatingToExpected(0, 0)
        // tick 4: serve partially
        dishes.first().setStatus(DishStatus.SERVED)
        casuals.receiveOrder()
        compareEatingToExpected(0, 1)
        // tick 5
        compareEatingToExpected(0, 1)
        assertEquals(
            6,
            casuals.peopleWaiting,
            "tick 5: people should still be waiting because of partial serve"
        )
        // tick 6
        compareEatingToExpected(1, 0)
        assertEquals(
            6,
            casuals.peopleWaiting,
            "tick 6: people should still be waiting because of partial serve"
        )
        // tick 7: everyone leaves
        compareEatingToExpected(1, 0)
        assertEquals(
            0,
            casuals.peopleWaiting,
            "tick 7: people who weren't served left"
        )
    }
}
