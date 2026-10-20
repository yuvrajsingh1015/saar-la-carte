package general.customerTests

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.DishStatus
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Recipe
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks

// import org.mockito.kotlin.anyOrNull
// import org.mockito.kotlin.whenever
// import saarlacarte.restaurant.Restaurant
// import saarlacarte.simulation.BrowsingService

/**
 * F24: decide at visitingTick - ceil(distance/5) - 3; reject 3 ticks after visitingTick.
 */
class DeliveryCustomerGroupTest {

    @BeforeEach
    fun resetTicks() {
        Ticks.init(80)
    }

    @AfterEach
    fun clearTicks() {
        Ticks.init(80)
    }

    @Test
    fun isDeliveryAndDoesNotDecideOnVisitingTickLikeCasual() {
        val group = delivery(visitingTick = 10, distance = 10)
        goToEveningAndTick(1, 10)
        assertTrue(group.isDelivery())
        assertFalse(group.isReadyToDecide())
    }

    @Test
    fun decidesOnTravelPlusThreeTicksBeforeVisit() {
        // dist 10 → ceil(10/5)=2, decide at 10-3-2 = 5 (forum 151 / spec p.20)
        val group = delivery(visitingTick = 10, distance = 10)
        goToEveningAndTick(1, 4)
        assertFalse(group.isReadyToDecide())
        goToEveningAndTick(1, 5)
        assertTrue(group.isReadyToDecide())
        goToEveningAndTick(1, 6)
        assertFalse(group.isReadyToDecide())
    }

    @Test
    fun travelTicksUsesCeilNotIntegerDivision() {
        // dist 6 → ceil(1.2)=2, not 6/5=1. Decide at 10-3-2=5, not tick 6.
        val group = delivery(visitingTick = 10, distance = 6)
        goToEveningAndTick(1, 5)
        assertTrue(group.isReadyToDecide())
        goToEveningAndTick(1, 6)
        assertFalse(group.isReadyToDecide())
    }

    @Test
    fun doesNotDecideOnWrongEvening() {
        val group = delivery(visitingTick = 10, distance = 10, evenings = listOf(2))
        goToEveningAndTick(1, 5)
        assertFalse(group.isReadyToDecide())
    }

    /* @Test
     fun browsingSeesDeliveryTrue() {
         val group = delivery(size = 3, preferredTable = TableType.BAR, distance = 8)
         val restaurant = mock<Restaurant>()
         whenever(restaurant.id).thenReturn(1)
         whenever(restaurant.positiveRatings).thenReturn(1)
         whenever(restaurant.negativeRatings).thenReturn(0)
         val browsing = mock<BrowsingService>()
         whenever(
             browsing.getEligibleRestaurants(any(), anyOrNull(), any(), any(), any(), any(), any())
         ).thenReturn(listOf(restaurant))
         group.setBrowsingService(browsing)

         assertEquals(restaurant, group.decideRestaurant())
         verify(browsing).getEligibleRestaurants(
             group.restaurantTypes,
             TableType.BAR,
             3,
             group.customerPreferences,
             true,
             false,
             group.visitingTick,
         )
         verify(browsing).updateAvailability(restaurant, 3, TableType.BAR, true)
     }*/

    @Test
    fun experienceFromArrivalTickRelativeToVisit() {
        val early = delivery(size = 1, visitingTick = 10)
        serveAll(early)
        goToEveningAndTick(1, 9)
        early.receiveOrder()
        assertEquals(CustomerExperience.POSITIVE, experience(early))

        val onTime = delivery(size = 1, visitingTick = 10)
        serveAll(onTime)
        goToEveningAndTick(1, 10)
        onTime.receiveOrder()
        assertEquals(CustomerExperience.NEUTRAL, experience(onTime))

        val late = delivery(size = 1, visitingTick = 10)
        serveAll(late)
        goToEveningAndTick(1, 11)
        late.receiveOrder()
        assertEquals(CustomerExperience.NEGATIVE, experience(late))
    }

    @Test
    fun arrivalDoesNotOverwriteExistingNegativeExperience() {
        val group = delivery(size = 1, visitingTick = 10)
        serveAll(group)
        val field = CustomerGroup::class.java.getDeclaredField("customerExperience")
        field.isAccessible = true
        field.set(group, CustomerExperience.NEGATIVE)
        goToEveningAndTick(1, 9)
        group.receiveOrder()
        assertEquals(CustomerExperience.NEGATIVE, experience(group))
    }
    /*
    @Test
    fun rejectsDeliveryOnThirdTickAfterVisitingTickIfStillWaiting() {
        val group = delivery(visitingTick = 6, distance = 5)
        goToEveningAndTick(1, 8)
        assertFalse(group.checkGaveUp())
        goToEveningAndTick(1, 9)
        assertTrue(group.checkGaveUp())
        assertEquals(CustomerExperience.NEGATIVE, experience(group))
    }*/

    @Test
    fun doesNotGiveUpAfterFoodArrived() {
        val group = delivery(size = 1, visitingTick = 6)
        serveAll(group)
        goToEveningAndTick(1, 6)
        group.receiveOrder()
        goToEveningAndTick(1, 12)
        assertFalse(group.checkGaveUp())
    }

    @Test
    fun logsGiveUpOnDeadlineOnlyWhileWaitingWithAnOrder() {
        val logger = mock<DeliveryCustomerLogger>()
        val waiting = delivery(visitingTick = 6)
        waiting.setLogger(logger)
        waiting.order = Order(4, mutableListOf(dish(DishStatus.FINISHED)), true, 5, 1)
        goToEveningAndTick(1, 9)
        waiting.giveUpCaseCheck()
        verify(logger).deliveryGivenUp(1, 4)

        val noOrder = delivery(id = 2, visitingTick = 6)
        noOrder.setLogger(logger)
        noOrder.giveUpCaseCheck()
        verify(logger, never()).deliveryGivenUp(eq(2), any())
    }

    /*@Test
    fun logsFinishedEatingOnceWhenLastPersonDone() {
        val logger = mock<DeliveryCustomerLogger>()
        val group = delivery(size = 1, visitingTick = 10)
        group.setLogger(logger)
        serveAll(group)
        goToEveningAndTick(1, 10)
        group.receiveOrder()
        group.eat()
        verify(logger, never()).deliveryFinishedEating(1)
        group.eat()
        verify(logger).deliveryFinishedEating(1)
        group.eat()
        verify(logger).deliveryFinishedEating(1)
    }*/

    private fun delivery(
        id: Int = 1,
        size: Int = 2,
        visitingTick: Int = 10,
        evenings: List<Int> = listOf(1),
        preferredTable: TableType? = TableType.COMMON,
        distance: Int = 5
    ) = DeliveryCustomerGroup(
        id,
        size,
        visitingTick,
        emptyList(),
        preferredTable,
        listOf(RestaurantType.ASIAN),
        evenings,
        RatingLikelihood.ALWAYS,
        distance
    )

    private fun goToEveningAndTick(evening: Int, tick: Int) {
        Ticks.init(80)
        repeat(evening) { Ticks.advanceEvening() }
        repeat(tick) { Ticks.advanceTick() }
    }

    private fun dish(status: DishStatus) = Dish(
        Recipe(1, "Soup", 1, mutableMapOf(), listOf(CookType.TOURNANT)),
        status
    )

    private fun serveAll(group: DeliveryCustomerGroup) {
        val dishes = MutableList(group.size) { dish(DishStatus.SERVED) }
        group.order = Order(1, dishes, true, group.deliveryDistance, 1)
    }

    private fun experience(group: CustomerGroup): CustomerExperience {
        val field = CustomerGroup::class.java.getDeclaredField("customerExperience")
        field.isAccessible = true
        return field.get(group) as CustomerExperience
    }
}
