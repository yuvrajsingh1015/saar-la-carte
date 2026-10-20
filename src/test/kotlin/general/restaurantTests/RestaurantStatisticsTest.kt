package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.TableType
import saarlacarte.restaurant.RestaurantStatistics

class RestaurantStatisticsTest {

    @Test
    fun startsAtZeroAndAccumulates() {
        val stats = RestaurantStatistics()
        assertEquals(0, stats.getMealsCooked())
        assertEquals(0, stats.getCustomersServed())
        assertEquals(0, stats.getCustomersDelivered())
        assertEquals(0, stats.getRatingsReceived())

        stats.recordMealsCooked(3)
        stats.recordMealsCooked(2)
        assertEquals(5, stats.getMealsCooked())

        stats.recordRatingsReceived(1)
        stats.recordRatingsReceived(4)
        assertEquals(5, stats.getRatingsReceived())
    }

    // walks a group's peopleWaiting down step by step, checking only the newly
    // served people get added each time, never the same people twice
    @Test
    fun recordCustomersServedOnlyCountsNewlyReceivedDeltas() {
        val stats = RestaurantStatistics()
        val group = RegularCustomerGroup(1, 4, 1, emptyList(), TableType.COMMON, 1, 3, 1)

        // nobody served yet
        stats.recordCustomersServed(group)
        assertEquals(0, stats.getCustomersServed())

        // 2 out of 4 got their food
        group.peopleWaiting = 2
        stats.recordCustomersServed(group)
        assertEquals(2, stats.getCustomersServed())

        // recorded again with no change, should not double count
        stats.recordCustomersServed(group)
        assertEquals(2, stats.getCustomersServed())

        // the remaining 2 got their food too
        group.peopleWaiting = 0
        stats.recordCustomersServed(group)
        assertEquals(4, stats.getCustomersServed())

        // spec p.39: statistics count "the amount of customers (and not visits)"
        // served, so a whole group of 10 served in one shot must add 10, not 1
        val bigGroup = RegularCustomerGroup(2, 10, 1, emptyList(), TableType.COMMON, 1, 3, 2)
        bigGroup.peopleWaiting = 0
        stats.recordCustomersServed(bigGroup)
        assertEquals(14, stats.getCustomersServed())
    }

    // served and delivered are 2 separate running totals, even for the same group
    @Test
    fun servedAndDeliveredAreTrackedIndependentlyForSameGroup() {
        val stats = RestaurantStatistics()
        val group = RegularCustomerGroup(1, 4, 1, emptyList(), TableType.COMMON, 1, 3, 1)

        group.peopleWaiting = 1
        stats.recordCustomersServed(group)
        stats.recordCustomersDelivered(group)

        assertEquals(3, stats.getCustomersServed())
        assertEquals(3, stats.getCustomersDelivered())
    }

    // 2 different groups should not affect each other's counted amount
    @Test
    fun twoGroupsTrackedIndependently() {
        val stats = RestaurantStatistics()
        val groupA = RegularCustomerGroup(1, 4, 1, emptyList(), TableType.COMMON, 1, 3, 1)
        val groupB = RegularCustomerGroup(2, 6, 1, emptyList(), TableType.COMMON, 1, 3, 1)

        groupA.peopleWaiting = 0
        stats.recordCustomersServed(groupA)
        assertEquals(4, stats.getCustomersServed())

        groupB.peopleWaiting = 2
        stats.recordCustomersServed(groupB)
        // groupA's 4 plus groupB's newly served 4 (6 - 2)
        assertEquals(8, stats.getCustomersServed())
    }

    @Test
    fun revisitAfterResetVisitCountsNewlyServedAgain() {
        val stats = RestaurantStatistics()
        val group = RegularCustomerGroup(1, 4, 1, emptyList(), TableType.COMMON, 1, 3, 1)

        group.peopleWaiting = 0
        stats.recordCustomersServed(group)
        assertEquals(4, stats.getCustomersServed())

        group.resetVisit()
        group.peopleWaiting = 0
        stats.recordCustomersServed(group)
        assertEquals(8, stats.getCustomersServed())
    }
}
