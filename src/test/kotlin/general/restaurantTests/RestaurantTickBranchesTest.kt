package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.RestaurantLogger
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

/** F30 / F01 glue: opening window, seating retry, leftover reserve, delivery no-order. */
class RestaurantTickBranchesTest {

    @Test
    fun beforeOpeningDoesNotSeatOrEscort() {
        val restaurant = restaurant(openingStart = 5, openingEnd = 10)
        val group = casual(1)
        restaurant.addCustomerGroup(group)
        goTo(1)
        restaurant.runServingPhaseTick()
        assertFalse(group.isSeated)
        assertFalse(group.hasLeftVisit)
    }

    @Test
    fun afterClosingStillRunsDeliveryAndEatWithoutEscort() {
        val restaurant = restaurant(openingStart = 1, openingEnd = 2, drivers = 1)
        goTo(3)
        restaurant.runServingPhaseTick()
        assertEquals(1, restaurant.availableDrivers)
    }

    @Test
    fun regularWithoutWaiterRetriesThenLeavesOnTheNextTick() {
        val restaurant = restaurant(waiters = 0)
        val group = regular(1)
        restaurant.addCustomerGroup(group)
        goTo(1)
        restaurant.runServingPhaseTick()
        assertFalse(group.hasLeftVisit)
        Ticks.advanceTick()
        restaurant.runServingPhaseTick()
        assertTrue(group.hasLeftVisit)
    }

    @Test
    fun eventWithoutWaiterLeavesOnTheVisitingTick() {
        val restaurant = restaurant(waiters = 0)
        val group = event(2)
        restaurant.addCustomerGroup(group)
        goTo(1)
        restaurant.runServingPhaseTick()
        assertTrue(group.hasLeftVisit)
    }

    @Test
    fun waiterButNoTableSendsTheGroupAwayThisTick() {
        val restaurant = restaurant(tables = 0)
        val group = casual(3)
        restaurant.addCustomerGroup(group)
        goTo(1)
        restaurant.runServingPhaseTick()
        assertTrue(group.hasLeftVisit)
        assertEquals(1, restaurant.negativeRatings)
    }

    @Test
    fun deliveryWithoutADishLeavesAndFreesTheDriverSlot() {
        val restaurant = restaurant(drivers = 1, recipes = false)
        val group = delivery(4)
        restaurant.addCustomerGroup(group)
        assertEquals(0, restaurant.availableDrivers)
        goTo(1)
        restaurant.runServingPhaseTick()
        assertTrue(group.hasLeftVisit)
        assertEquals(1, restaurant.availableDrivers)
    }

    @Test
    fun failedReservationIsRatedOnTheFirstServingTick() {
        val restaurant = restaurant(tables = 0)
        val group = regular(5)
        goTo(1)
        restaurant.runPreparationPhase(listOf(group))
        assertFalse(restaurant.frontOfHouse.hasReservedTable(group))
        restaurant.runServingPhaseTick()
        assertEquals(1, restaurant.negativeRatings)
    }

    @Test
    fun firstTimeRegularEmptyHistoryUsesReservedSeatsForRestock() {
        val restaurant = restaurant()
        val group = regular(6)
        assertTrue(group.last3.isEmpty())
        goTo(1)
        restaurant.runPreparationPhase(listOf(group))
        assertTrue(restaurant.frontOfHouse.hasReservedTable(group))
    }

    @Test
    fun addingALeaverResetsTheVisit() {
        val restaurant = restaurant()
        val group = casual(7)
        group.leave()
        assertTrue(group.hasLeftVisit)
        restaurant.addCustomerGroup(group)
        assertFalse(group.hasLeftVisit)
        assertTrue(group in restaurant.customerGroups)
    }

    @Test
    fun secondEndOfOpeningTimeDoesNotRateLeftoversAgain() {
        val restaurant = restaurant()
        val group = event(8)
        restaurant.addCustomerGroup(group)
        seat(group, restaurant)
        restaurant.endOfOpeningTime()
        val afterFirst = restaurant.negativeRatings
        restaurant.endOfOpeningTime()
        assertEquals(afterFirst, restaurant.negativeRatings)
        assertFalse(restaurant.customerGroups.contains(group))
    }

    @Test
    fun logStatisticsWritesTheFourTotals() {
        val restaurant = restaurant()
        val logger = mock<RestaurantLogger>()
        restaurant.logger = logger
        restaurant.statistics.recordMealsCooked(2)
        restaurant.logStatistics()
        verify(logger).cookedTotal(2)
        verify(logger).servedTotal(0)
        verify(logger).deliveredTotal(0)
        verify(logger).ratingsTotal(0)
    }

    private fun restaurant(
        openingStart: Int = 1,
        openingEnd: Int = 24,
        waiters: Int = 1,
        tables: Int = 1,
        drivers: Int = 0,
        recipes: Boolean = true
    ): Restaurant {
        val potato = Ingredient("Potato", MeasuringUnit.G, 5)
        val recipe = Recipe(1, "Sushi", 10, mutableMapOf(potato to 1), listOf(CookType.EXEC))
        val staff = Staff(waiters, mapOf(CookType.EXEC to 1), drivers)
        val menu = Menu(
            Supplier(listOf(potato), mutableMapOf("Potato" to 1000)),
            if (recipes) listOf(recipe) else emptyList(),
            RestaurantType.ASIAN
        )
        val tableList = if (tables == 0) emptyList() else listOf(Table(1, TableType.COMMON, 4))
        return Restaurant(
            1,
            "R1",
            RestaurantType.ASIAN,
            openingStart,
            openingEnd,
            true,
            staff,
            0,
            0,
            FrontOfHouse(tableList, staff.getWaiters(), null, menu),
            Kitchen(staff.getCooks(), menu),
            drivers
        )
    }

    private fun regular(id: Int) = RegularCustomerGroup(
        id,
        1,
        1,
        emptyList(),
        TableType.COMMON,
        1,
        1,
        1
    )

    private fun casual(id: Int) = CasualCustomerGroup(
        id,
        1,
        1,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        listOf(1),
        RatingLikelihood.ALWAYS
    )

    private fun event(id: Int) = EventCustomerGroup(
        id,
        1,
        1,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        1,
        mutableMapOf()
    )

    private fun delivery(id: Int) = DeliveryCustomerGroup(
        id,
        1,
        1,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        listOf(1),
        RatingLikelihood.ALWAYS,
        5
    )

    private fun seat(group: CustomerGroup, restaurant: Restaurant) {
        restaurant.frontOfHouse.seatedGroups[group] = listOf(Table(1, TableType.COMMON, 4))
        group.isSeated = true
    }

    private fun goTo(tick: Int) {
        Ticks.init(24)
        Ticks.advanceEvening()
        repeat(tick) { Ticks.advanceTick() }
    }
}
