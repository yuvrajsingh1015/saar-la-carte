package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.WaiterAction
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Waiter

// F17: waiter-assignment rules for REGULAR/CASUAL groups, FrontOfHouse.assignWaiter (spec p.14-15)
class FOHStaffManagementTest {

    private fun group(id: Int, size: Int) =
        CasualCustomerGroup(id, size, 1, emptyList(), null, emptyList(), emptyList(), RatingLikelihood.NEVER)

    private fun foh(waiters: MutableList<Waiter>) = FrontOfHouse(emptyList(), waiters, null, mock<Menu>())

    @Test
    fun noFreeWaiterReturnsNull() {
        val waiter = Waiter()
        waiter.performAction(WaiterAction.SEAT, 10)
        val chosen = foh(mutableListOf(waiter)).assignWaiter(group(1, 1))
        assertNull(chosen)
    }

    @Test
    fun ruleOneExcludesWaiterThatWouldExceedTickLimit() {
        val wouldExceed = Waiter()
        wouldExceed.performAction(WaiterAction.SEAT, 8)
        val fitsExactly = Waiter()
        fitsExactly.performAction(WaiterAction.SEAT, 5)

        // group of 5 -> wouldExceed hits 13 > 10 and is excluded, fitsExactly hits exactly 10
        val chosen = foh(mutableListOf(wouldExceed, fitsExactly)).assignWaiter(group(1, 5))
        assertSame(fitsExactly, chosen)
    }

    @Test
    fun mostCustomersWaiterIsPickedNotMostGroups() {
        val fewGroupsManyCustomers = Waiter()
        fewGroupsManyCustomers.addCustomerGroup(group(1, 8)) // 1 group, currentLoad 8

        val manyGroupsFewCustomers = Waiter()
        manyGroupsFewCustomers.addCustomerGroup(group(2, 1))
        manyGroupsFewCustomers.addCustomerGroup(group(3, 1))
        manyGroupsFewCustomers.addCustomerGroup(group(4, 1)) // 3 groups, currentLoad 3

        val chosen = foh(mutableListOf(fewGroupsManyCustomers, manyGroupsFewCustomers)).assignWaiter(group(5, 1))

        // spec p.14 rule 2: "the waiter with the most customers is selected"
        assertSame(fewGroupsManyCustomers, chosen)
    }

    @Test
    fun ruleTwoTieBreaksOnLowestId() {
        val waiterHigh = Waiter()
        waiterHigh.assignId(5)
        val waiterLow = Waiter()
        waiterLow.assignId(2)

        // both untouched: tied on currentLoad (0) and customer count (0)
        val chosen = foh(mutableListOf(waiterHigh, waiterLow)).assignWaiter(group(1, 1))

        // spec p.15 rule 4: "In case of a tie, the waiter with the lowest id is selected."
        assertEquals(2, chosen?.getId())
    }

    @Test
    fun fallsBackToLeastCurrentLoadWhenNoWaiterBelowTen() {
        val heavier = Waiter()
        repeat(15) { heavier.addCustomerGroup(group(it, 1)) } // currentLoad 15
        val lighter = Waiter()
        repeat(10) { lighter.addCustomerGroup(group(it + 100, 1)) } // currentLoad 10

        val chosen = foh(mutableListOf(heavier, lighter)).assignWaiter(group(1, 1))

        // spec p.15 rule 3: "the waiter with the least current load is selected"
        assertSame(lighter, chosen)
    }

    @Test
    fun ruleThreeTieBreaksOnLowestId() {
        val waiterHigh = Waiter()
        waiterHigh.assignId(7)
        repeat(10) { waiterHigh.addCustomerGroup(group(it, 1)) } // currentLoad 10, no low-load candidate
        val waiterLow = Waiter()
        waiterLow.assignId(1)
        repeat(10) { waiterLow.addCustomerGroup(group(it + 100, 1)) } // currentLoad 10, tied with waiterHigh

        val chosen = foh(mutableListOf(waiterHigh, waiterLow)).assignWaiter(group(1, 1))

        assertEquals(1, chosen?.getId())
    }
}
