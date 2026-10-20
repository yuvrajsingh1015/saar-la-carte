package general.restaurantTests

import org.junit.jupiter.api.Test
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.WaiterAction
import saarlacarte.restaurant.MAX_TICKLOAD
import saarlacarte.restaurant.Waiter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F17: FOH - Staff Management (assigning id, managing workload and tick load per staff).
 *
 * Directly exercises Waiter's tick-load bookkeeping (canPerform/performAction/getTickLoad/
 * resetTickLoad/resetForNextEvening), independent of FrontOfHouse.assignWaiter, which is
 * already covered by FOHStaffManagementTest.kt.
 */
class WaiterActionLimitsTest {

    private fun group(id: Int, size: Int) =
        CasualCustomerGroup(id, size, 1, emptyList(), null, emptyList(), emptyList(), RatingLikelihood.NEVER)

    @Test
    fun `canPerform is true exactly at the tick load boundary`() {
        val waiter = Waiter()
        waiter.performAction(WaiterAction.ESCORT, MAX_TICKLOAD - 2)
        assertTrue(waiter.canPerform(WaiterAction.ESCORT, 2))
    }

    @Test
    fun `canPerform is false one over the tick load boundary`() {
        val waiter = Waiter()
        waiter.performAction(WaiterAction.ESCORT, MAX_TICKLOAD - 2)
        assertFalse(waiter.canPerform(WaiterAction.ESCORT, 3))
    }

    @Test
    fun `each WaiterAction tracks its own tick load independently`() {
        val waiter = Waiter()
        waiter.performAction(WaiterAction.SEAT, 4)
        waiter.performAction(WaiterAction.TAKING_ORDER, 6)
        waiter.performAction(WaiterAction.SERVING, 2)

        assertEquals(4, waiter.getTickLoad(WaiterAction.SEAT))
        assertEquals(6, waiter.getTickLoad(WaiterAction.TAKING_ORDER))
        assertEquals(2, waiter.getTickLoad(WaiterAction.SERVING))
        // untouched action stays at 0
        assertEquals(0, waiter.getTickLoad(WaiterAction.ESCORT))
    }

    @Test
    fun `performAction accumulates across multiple calls for the same action`() {
        val waiter = Waiter()
        waiter.performAction(WaiterAction.SERVING, 3)
        waiter.performAction(WaiterAction.SERVING, 4)
        assertEquals(7, waiter.getTickLoad(WaiterAction.SERVING))
    }

    @Test
    fun `resetTickLoad zeroes every action, not just the ones that were touched`() {
        val waiter = Waiter()
        waiter.performAction(WaiterAction.SEAT, 5)
        waiter.performAction(WaiterAction.TAKING_ORDER, 5)
        waiter.performAction(WaiterAction.SERVING, 5)
        waiter.performAction(WaiterAction.ESCORT, 5)

        waiter.resetTickLoad()

        for (action in WaiterAction.entries) {
            assertEquals(0, waiter.getTickLoad(action))
        }
    }

    @Test
    fun `resetForNextEvening clears id, currentLoad, customers and tick load together`() {
        val waiter = Waiter()
        waiter.assignId(7)
        waiter.addCustomerGroup(group(1, 4))
        waiter.performAction(WaiterAction.ESCORT, 5)

        waiter.resetForNextEvening()

        assertNull(waiter.getId())
        assertEquals(0, waiter.getCurrentLoad())
        assertTrue(waiter.getCustomerGroups().isEmpty())
        assertEquals(0, waiter.getTickLoad(WaiterAction.ESCORT))
    }
}
