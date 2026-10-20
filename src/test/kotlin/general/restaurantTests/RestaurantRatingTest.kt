package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.loggers.DriverLogger
import saarlacarte.loggers.RestaurantLogger
import saarlacarte.orders.Order
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

/**
 * P05: Restaurant.rate is private and escort() is still a stub, so these tests
 * invoke rate by reflection the same way Casual tests set experience.
 */
class RestaurantRatingTest {

    @Test
    fun emptyEscortRecordsZeroRatingsAndLogsStatus() {
        val restaurant = restaurant()
        val logger = mock<RestaurantLogger>()
        restaurant.logger = logger

        Ticks.init(1)
        Ticks.advanceEvening()
        Ticks.advanceTick()
        restaurant.runServingPhaseTick()

        assertEquals(0, restaurant.positiveRatings)
        assertEquals(0, restaurant.negativeRatings)
        assertEquals(0, restaurant.statistics.getRatingsReceived())
        verify(logger).ratingStatus(0)
        verify(logger, never()).receivedRating(any(), any(), any(), any())
    }

    @Test
    fun positiveRatingIncrementsCounterLogsAndStatistics() {
        val restaurant = restaurant()
        val logger = mock<RestaurantLogger>()
        restaurant.logger = logger
        val group = regular(12)
        setExperience(group, CustomerExperience.POSITIVE)

        invokeRate(restaurant, listOf(group))

        assertEquals(1, restaurant.positiveRatings)
        assertEquals(0, restaurant.negativeRatings)
        assertEquals(1, restaurant.statistics.getRatingsReceived())
        verify(logger).receivedRating(12, "POSITIVE", 1, 0)
        verify(logger).ratingStatus(1)
    }

    @Test
    fun negativeRatingIncrementsCounterLogsAndStatistics() {
        val restaurant = restaurant(startPositive = 2, startNegative = 4)
        val logger = mock<RestaurantLogger>()
        restaurant.logger = logger
        val group = regular(7)
        setExperience(group, CustomerExperience.NEGATIVE)

        invokeRate(restaurant, listOf(group))

        assertEquals(2, restaurant.positiveRatings)
        assertEquals(5, restaurant.negativeRatings)
        assertEquals(1, restaurant.statistics.getRatingsReceived())
        verify(logger).receivedRating(7, "NEGATIVE", 2, 5)
        verify(logger).ratingStatus(1)
    }

    @Test
    fun zeroRatingDoesNotCountOrLogReceivedRating() {
        val restaurant = restaurant()
        val logger = mock<RestaurantLogger>()
        restaurant.logger = logger
        val group = casualSome(3)
        setExperience(group, CustomerExperience.NEUTRAL)

        invokeRate(restaurant, listOf(group))

        assertEquals(0, restaurant.positiveRatings)
        assertEquals(0, restaurant.negativeRatings)
        assertEquals(0, restaurant.statistics.getRatingsReceived())
        verify(logger, never()).receivedRating(any(), any(), any(), any())
        verify(logger).ratingStatus(0)
    }

    @Test
    fun mixedGroupsCountOnlyThoseWhoActuallyRated() {
        val restaurant = restaurant()
        val logger = mock<RestaurantLogger>()
        restaurant.logger = logger
        val positive = regular(1)
        val skipped = casualSome(2)
        val negative = regular(3)
        setExperience(positive, CustomerExperience.POSITIVE)
        setExperience(skipped, CustomerExperience.NEUTRAL)
        setExperience(negative, CustomerExperience.NEGATIVE)

        invokeRate(restaurant, listOf(positive, skipped, negative))

        assertEquals(1, restaurant.positiveRatings)
        assertEquals(1, restaurant.negativeRatings)
        assertEquals(2, restaurant.statistics.getRatingsReceived())
        verify(logger).receivedRating(1, "POSITIVE", 1, 0)
        verify(logger).receivedRating(3, "NEGATIVE", 1, 1)
        verify(logger).ratingStatus(2)
    }

    @Test
    fun ratingsAreLoggedRegularThenEventThenCasualById() {
        val restaurant = restaurant()
        val logger = mock<RestaurantLogger>()
        restaurant.logger = logger
        val casual = casualSome(1)
        val event = EventCustomerGroup(4, 1, 1, emptyList(), TableType.COMMON, emptyList(), 1, mutableMapOf())
        val regular = regular(9)
        val delivery = DeliveryCustomerGroup(
            2,
            1,
            1,
            emptyList(),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS,
            5
        )
        listOf(casual, event, regular, delivery).forEach { setExperience(it, CustomerExperience.POSITIVE) }

        invokeRate(restaurant, listOf(delivery, casual, event, regular))

        val order = inOrder(logger)
        order.verify(logger).receivedRating(9, "POSITIVE", 1, 0)
        order.verify(logger).receivedRating(4, "POSITIVE", 2, 0)
        order.verify(logger).receivedRating(1, "POSITIVE", 3, 0)
        order.verify(logger).receivedRating(2, "POSITIVE", 4, 0)
    }

    @Test
    fun strandedLeaverStillRatesThisTick() {
        val restaurant = restaurant()
        val logger = mock<RestaurantLogger>()
        restaurant.logger = logger
        val group = regular(5)
        setExperience(group, CustomerExperience.NEGATIVE)
        restaurant.addCustomerGroup(group)
        group.leave()

        Ticks.init(1)
        Ticks.advanceEvening()
        Ticks.advanceTick()
        restaurant.runServingPhaseTick()

        assertEquals(0, restaurant.positiveRatings)
        assertEquals(1, restaurant.negativeRatings)
        verify(logger).receivedRating(5, "NEGATIVE", 0, 1)
    }

    @Test
    fun lastThreeTicksStillSeatReservedRegular() {
        val restaurant = reservedRestaurant(openingEnd = 5)
        val group = regular(1, visitingTick = 4)
        restaurant.addCustomerGroup(group)
        restaurant.runPreparationPhase(listOf(group))
        goToTick(4)
        invokeSeat(restaurant)

        assertTrue(group.isSeated)
        assertFalse(group.hasLeftVisit)
    }

    @Test
    fun deliveryGiveUpLogsThenRatesOnDeadlineTick() {
        val restaurant = restaurant()
        val restaurantLogger = mock<RestaurantLogger>()
        val deliveryLogger = mock<DeliveryCustomerLogger>()
        val driverLogger = mock<DriverLogger>()
        restaurant.assignAllLoggers(restaurantLogger, driverLog = driverLogger, deliveryLogger = deliveryLogger)
        val group = DeliveryCustomerGroup(
            8,
            1,
            1,
            emptyList(),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS,
            5
        )
        group.order = Order(3, mutableListOf(), true, 5, 1)
        restaurant.addCustomerGroup(group)
        goToTick(4)
        restaurant.runServingPhaseTick()

        verify(deliveryLogger).deliveryGivenUp(8, 3)
        assertEquals(1, restaurant.negativeRatings)
        verify(restaurantLogger).receivedRating(8, "NEGATIVE", 0, 1)
    }

    @Test
    fun lastThreeTicksDoNotSeatUnreservedCasual() {
        val restaurant = reservedRestaurant(openingEnd = 5)
        val group = casualSome(2, visitingTick = 4)
        restaurant.addCustomerGroup(group)
        goToTick(4)
        invokeSeat(restaurant)

        assertFalse(group.isSeated)
    }

    @Test
    fun nextEveningPrepClearsStaleNegativeExperience() {
        val restaurant = restaurant()
        val group = regular(1)
        setExperience(group, CustomerExperience.NEGATIVE)
        restaurant.addCustomerGroup(group)

        restaurant.runPreparationPhase()
        invokeRate(restaurant, listOf(group))

        assertEquals(1, restaurant.positiveRatings)
        assertEquals(0, restaurant.negativeRatings)
    }

    @Test
    fun nullLoggerDoesNotCrashWhenGroupsRate() {
        val restaurant = restaurant()
        val positive = regular(1)
        val negative = regular(2)
        setExperience(positive, CustomerExperience.POSITIVE)
        setExperience(negative, CustomerExperience.NEGATIVE)

        assertDoesNotThrow { invokeRate(restaurant, listOf(positive, negative)) }
        assertEquals(1, restaurant.positiveRatings)
        assertEquals(1, restaurant.negativeRatings)
        assertEquals(2, restaurant.statistics.getRatingsReceived())
    }

    private fun restaurant(startPositive: Int = 0, startNegative: Int = 0): Restaurant {
        val staff = Staff(0, emptyMap(), 0)
        val menu = Menu(Supplier(emptyList(), mutableMapOf()), emptyList(), RestaurantType.ASIAN)
        return Restaurant(
            1,
            "R1",
            RestaurantType.ASIAN,
            1,
            24,
            false,
            staff,
            startPositive,
            startNegative,
            FrontOfHouse(emptyList(), staff.getWaiters(), null, menu),
            Kitchen(staff.getCooks(), menu),
            0
        )
    }

    private fun reservedRestaurant(openingEnd: Int): Restaurant {
        val staff = Staff(1, emptyMap(), 0)
        val menu = Menu(Supplier(emptyList(), mutableMapOf()), emptyList(), RestaurantType.ASIAN)
        return Restaurant(
            1,
            "R1",
            RestaurantType.ASIAN,
            1,
            openingEnd,
            false,
            staff,
            0,
            0,
            FrontOfHouse(listOf(Table(1, TableType.COMMON, 1)), staff.getWaiters(), null, menu),
            Kitchen(staff.getCooks(), menu),
            0
        )
    }

    private fun regular(id: Int, visitingTick: Int = 1) = RegularCustomerGroup(
        id,
        1,
        visitingTick,
        emptyList(),
        TableType.COMMON,
        1,
        1,
        1
    )

    private fun casualSome(id: Int, visitingTick: Int = 1) = CasualCustomerGroup(
        id,
        1,
        visitingTick,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        listOf(1),
        RatingLikelihood.SOME
    )
}

private fun invokeRate(restaurant: Restaurant, groups: List<CustomerGroup>) {
    val method = Restaurant::class.java.getDeclaredMethod("rate", List::class.java)
    method.isAccessible = true
    method.invoke(restaurant, groups)
}

private fun invokeSeat(restaurant: Restaurant) {
    val method = Restaurant::class.java.getDeclaredMethod("seat")
    method.isAccessible = true
    method.invoke(restaurant)
}

private fun goToTick(tick: Int) {
    Ticks.init(tick)
    Ticks.advanceEvening()
    repeat(tick) { Ticks.advanceTick() }
}

private fun setExperience(group: CustomerGroup, experience: CustomerExperience) {
    val field = CustomerGroup::class.java.getDeclaredField("customerExperience")
    field.isAccessible = true
    field.set(group, experience)
}
