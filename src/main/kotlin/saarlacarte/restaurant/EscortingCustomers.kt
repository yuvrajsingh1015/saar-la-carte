package saarlacarte.restaurant

import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.TableStatus
import saarlacarte.enums.WaiterAction
import kotlin.math.min

/** a class for escorting people (making FrontOfHouse smaller **/
class EscortingCustomers(val waiters: MutableList<Waiter>, val seatedGroups: MutableMap<CustomerGroup, List<Table>>) {

    var loggers: FOHLogger? = null
    private var lastEscortWaitstaff = 0
    private var lastEscortCustomers = 0

    /** escort the customers that need to be escorted**/
    fun escortCustomers(): List<CustomerGroup> {
        val customersToEscort = getEscortCandidates()
        var escortedPeople = 0
        val activeWaiters = mutableListOf<Waiter>()
        val left = mutableListOf<CustomerGroup>()

        for (group in customersToEscort) {
            val waitersAssigned = getWaitersForEscort(group)
            escortedPeople += escortGroupWithWaiters(group, waitersAssigned, activeWaiters)
            if (group !in seatedGroups) {
                left.add(group)
            }
        }

        lastEscortWaitstaff = activeWaiters.distinct().size
        lastEscortCustomers = escortedPeople
        return left
    }

    private fun getEscortCandidates(): List<CustomerGroup> {
        return seatedGroups.keys.filter {
            // size = peopleFinishedEating + leftFrustrated + how many eating
            // size = howManySeating + leftFrustrated + escorted
            // size = peopleWaiting + peopleFinishedEating + PeopleEating + leftFrustrated
            // here we need: everyone who is seating finished eating
            it.peopleFinishedEating == it.howManySitting() + it.peopleEscorted &&
                it.isSeated &&
                !it.isDelivery()
        }.sortedWith(
            compareBy(
                { group ->
                    when (group) {
                        is RegularCustomerGroup -> 1
                        is EventCustomerGroup -> 2
                        else -> 3
                    }
                },
                { it.id }
            )
        )
    }

    private fun getWaitersForEscort(group: CustomerGroup): List<Waiter> {
        if (group is EventCustomerGroup) {
            return waiters.filter { it.getTickLoad(WaiterAction.ESCORT) < MAX_TICKLOAD }
                .sortedWith(
                    compareBy<Waiter> { it.getCurrentLoad() }
                        .thenBy { it.getId() ?: Int.MAX_VALUE }
                )
        }
        return waiters.filter { group in it.getCustomerGroups() }
    }

    /** takes group, assigned waiters and returns how many people were escorted **/
    private fun escortGroupByWaiter(group: CustomerGroup, waiter: Waiter): Int {
        val howManyCanPerform = MAX_TICKLOAD - waiter.getTickLoad(WaiterAction.ESCORT)
        if (howManyCanPerform > 0) {
            val toEscort = group.howManySitting()
            val actuallyEscorted = min(howManyCanPerform, toEscort)
            waiter.performAction(
                WaiterAction.ESCORT,
                actuallyEscorted
            )
            group.peopleEscorted += actuallyEscorted
            if (actuallyEscorted > 0) {
                assignWaiterIdIfNeeded(waiter, waiters)
                loggers?.loggerServing?.escorted(
                    waiter.getId() ?: 0,
                    actuallyEscorted,
                    group.id,
                    seatedGroups[group]?.minOf { table -> table.getId() } ?: 0
                )
            }
            // if no one from group remains seated
            if (group.size - group.peopleLeftFrustrated - group.peopleEscorted <= 0) {
                demergeTables(group)
                group.leave()
                // Releases from all waiters who seated/escorted them
                waiters.forEach { it.releaseGroup(group) }
            }
            return actuallyEscorted
        }
        return 0
    }

    /** DEBUG escorting status; call after escort actions and before Rating. */
    fun logEscortingStatus() {
        loggers?.loggerServing?.escortingStatus(lastEscortWaitstaff, lastEscortCustomers)
        lastEscortWaitstaff = 0
        lastEscortCustomers = 0
    }

    private fun escortGroupWithWaiters(
        group: CustomerGroup,
        waitersAssigned: List<Waiter>,
        activeWaiters: MutableList<Waiter>
    ): Int {
        var escortedForGroup = 0
        for (waiter in waitersAssigned) {
            if (group.howManySitting() <= 0) break
            val actuallyEscorted = escortGroupByWaiter(group, waiter)
            if (actuallyEscorted > 0) {
                if (group !is EventCustomerGroup) {
                    waiter.reduceCurrentLoad(actuallyEscorted)
                }
                activeWaiters.add(waiter)
                escortedForGroup += actuallyEscorted
            }
        }
        return escortedForGroup
    }

    /** frees casual tables; regular/event tables stay reserved (i.e no change) **/
    fun demergeTables(group: CustomerGroup) {
        val groupTables = seatedGroups.remove(group) ?: return
        val staysReserved = group is RegularCustomerGroup || group is EventCustomerGroup
        val newStatus = if (staysReserved) TableStatus.RESERVED else TableStatus.FREE
        for (table in groupTables) {
            table.setStatus(newStatus)
        }
    }
}
