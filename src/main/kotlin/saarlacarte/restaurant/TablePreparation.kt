package saarlacarte.restaurant

import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.TableStatus
import saarlacarte.enums.TableType

private const val THREE_QUARTER_NUMERATOR = 3
private const val THREE_QUARTER_DENOMINATOR = 4

/**
 * Table lookup, reservation, and merging - preparation-phase work plus the table-side of
 * seating (F14/F15). Split out of FrontOfHouse to keep it under detekt's LargeClass/
 * TooManyFunctions limits. [seatedGroups] is the same map instance FrontOfHouse uses
 * elsewhere (seating, escorting, end of evening), shared by reference, not copied.
 */
class TablePreparation(
    private val tables: List<Table>,
    private val seatedGroups: MutableMap<CustomerGroup, List<Table>>
) {
    /** Serving phase: find a suitable table for a customer group or merge them to make one **/
    fun findTables(group: CustomerGroup): List<Table> {
        val type = group.preferredTableType ?: TableType.COMMON
        // table assignment rule 1
        val candidates = mutableListOf<Table>()
        for (table in tables) {
            if (table.getType() == type && table.getStatus() == TableStatus.FREE) {
                candidates.add(table)
            }
        }

        // table assignment rule 2-3
        findSingleTable(candidates, group.size, requireThreeQuarter = true)?.let { return listOf(it) }
        // table assignment rule 4
        findMerge(candidates, group.size, type, requireThreeQuarter = true)?.let { return it }

        if (group !is RegularCustomerGroup && group !is EventCustomerGroup) {
            return emptyList()
        }
        // table assignment rule 5
        findSingleTable(candidates, group.size, requireThreeQuarter = false)?.let { return listOf(it) }
        // table assignment rule 6
        return findMerge(candidates, group.size, type, requireThreeQuarter = false) ?: emptyList()
    }

    /** rules 2/5: exact fit, else smallest larger table **/
    private fun findSingleTable(candidates: List<Table>, groupSize: Int, requireThreeQuarter: Boolean): Table? {
        // exact fit + tie-breaker (1)
        val exactFits = mutableListOf<Table>()
        for (table in candidates) {
            if (table.getSize() == groupSize) {
                exactFits.add(table)
            }
        }
        exactFits.minWithOrNull(compareBy({ it.getSize() }, { it.getId() }))?.let { return it }

        // larger depending on flag (either 3 or 5)
        val larger = mutableListOf<Table>()
        for (table in candidates) {
            if (table.getSize() > groupSize) {
                larger.add(table)
            }
        }
        val eligible = mutableListOf<Table>()
        for (table in larger) {
            val meetsThreeQuarter = groupSize * THREE_QUARTER_DENOMINATOR >= table.getSize() * THREE_QUARTER_NUMERATOR
            if (!requireThreeQuarter || meetsThreeQuarter) {
                eligible.add(table)
            }
        }
        return eligible.minWithOrNull(compareBy({ it.getSize() }, { it.getId() }))
    }

    /** rules 4/6: merge smaller tables smallest-to-largest, then trim **/
    private fun findMerge(
        candidates: List<Table>,
        groupSize: Int,
        type: TableType,
        requireThreeQuarter: Boolean
    ): List<Table>? {
        // BAR tables never merge
        if (type == TableType.BAR) return null
        // smaller tables, smallest first
        val smallerCandidates = candidates.filter { it.getSize() < groupSize }
        val smaller = smallerCandidates.sortedWith(compareBy({ it.getSize() }, { it.getId() }))
        val merge = mutableListOf<Table>()
        var sum = 0
        // add tables till we hit group size
        for (table in smaller) {
            merge.add(table)
            sum += table.getSize()
            if (sum >= groupSize) break
        }
        // drop smallest while still enough
        while (sum >= groupSize) {
            val smallest = merge.minWithOrNull(compareBy<Table>({ it.getSize() }, { it.getId() }))
            if (smallest == null || sum - smallest.getSize() < groupSize) break
            merge.remove(smallest)
            sum -= smallest.getSize()
        }
        // too small, or doesn't cover 3/4 unless lifted
        return if (sum < groupSize) {
            null
        } else if (requireThreeQuarter && groupSize * THREE_QUARTER_DENOMINATOR < sum * THREE_QUARTER_NUMERATOR) {
            null
        } else {
            merge
        }
    }

    /** Preparation phase: reserve tables for regular/event customers **/
    fun managerAssignTables(customers: List<CustomerGroup>, loggers: FOHLogger?) {
        // event groups first, then regular - spec order
        val eventGroups = mutableListOf<EventCustomerGroup>()
        val regularGroups = mutableListOf<RegularCustomerGroup>()
        for (customer in customers) {
            if (customer is EventCustomerGroup) {
                eventGroups.add(customer)
            } else if (customer is RegularCustomerGroup) {
                regularGroups.add(customer)
            }
        }
        val event = eventGroups.sortedBy { it.id }
        val regular = regularGroups.sortedBy { it.id }

        // concatenation so first event groups are processed and then the regulars
        for (group in event + regular) {
            val assigned = findTables(group)
            if (assigned.isEmpty()) {
                loggers?.loggerPreparation?.tableNotReserved(group.id)
                continue
            }
            for (table in assigned) {
                table.setStatus(TableStatus.RESERVED)
            }
            seatedGroups[group] = assigned
        }
    }

    /** Preparation phase: count the free seats left after reserving tables **/
    fun freeSeatCount(): Int {
        var total = 0
        for (table in tables) {
            if (table.getStatus() == TableStatus.FREE) {
                total += table.getSize()
            }
        }
        return total
    }

    /** Serving phase: count all available seats **/
    fun availableSeats(tableType: TableType): Int {
        var total = 0
        for (table in tables) {
            if (table.getType() == tableType && table.getStatus() == TableStatus.FREE) {
                total += table.getSize()
            }
        }
        return total
    }

    /** total tables per type **/
    fun totalTablesCount(tableType: TableType): Int {
        return tables.filter { it.getType() == tableType }.sumOf { it.getSize() }
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
