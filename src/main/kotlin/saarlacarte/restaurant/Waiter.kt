package saarlacarte.restaurant

import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.enums.WaiterAction

// CONSTANTS
const val MAX_TICKLOAD = 10

/**
Waiter
 **/
class Waiter {
    private var id: Int? = null

    /** Customers this waiter has seated and not yet escorted out. **/
    private var currentLoad: Int = 0
    private val tickLoad: MutableMap<WaiterAction, Int> = mutableMapOf(
        WaiterAction.SEAT to 0,
        WaiterAction.TAKING_ORDER to 0,
        WaiterAction.SERVING to 0,
        WaiterAction.ESCORT to 0
    )
    private val customers: MutableList<CustomerGroup> = mutableListOf()

    // !!! In the class diagram this returned an Int, I removed it for consistency !!!
    /** Assign this waiter's id for the evening **/
    fun assignId(newId: Int) {
        id = newId
    }

    /** Check if this waiter can perform the requested WaiterAction **/
    fun canPerform(action: WaiterAction, amount: Int): Boolean {
        return (tickLoad[action] ?: 0) + amount <= MAX_TICKLOAD
    }

    /** Perform a WaiterAction: update this waiter's tickLoad **/
    fun performAction(action: WaiterAction, amount: Int) {
        tickLoad[action] = (tickLoad[action] ?: 0) + amount
    }

    /** Get this waiter's tickLoad for the specified WaiterAction **/
    fun getTickLoad(action: WaiterAction): Int {
        return tickLoad[action] ?: 0
    }

    // !!! parameter name changed wrt class diagram !!!
    /** Add a customer group to this waiter's assigned customers **/
    fun addCustomerGroup(group: CustomerGroup) {
        this.customers.add(group)
        // [P15; L26]: "EVENTS do not count toward that load"
        if (group !is EventCustomerGroup) currentLoad += group.size
    }

    /** Getter for Waiter id **/
    fun getId(): Int? {
        return id
    }

    /** Getter for Waiter currentLoad **/
    fun getCurrentLoad(): Int {
        return currentLoad
    }

    /** Getter for this Waiter's customers **/
    fun getCustomerGroups(): List<CustomerGroup> {
        return customers
    }

    /** Removes [group] from this waiter. Load is reduced by the caller. */
    fun releaseGroup(group: CustomerGroup) {
        customers.remove(group)
    }

    /** Clears this tick's action counts. */
    fun resetTickLoad() {
        tickLoad[WaiterAction.SEAT] = 0
        tickLoad[WaiterAction.TAKING_ORDER] = 0
        tickLoad[WaiterAction.SERVING] = 0
        tickLoad[WaiterAction.ESCORT] = 0
    }

    /** Next evening starts with no id, load, or tick actions. */
    fun resetForNextEvening() {
        id = null
        currentLoad = 0
        customers.clear()
        resetTickLoad()
    }

    /** reduce current load by**/
    fun reduceCurrentLoad(peopleLeft: Int) {
        currentLoad = (currentLoad - peopleLeft).coerceAtLeast(0)
    }
}
