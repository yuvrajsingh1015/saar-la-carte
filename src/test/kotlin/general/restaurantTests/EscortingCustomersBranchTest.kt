package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.TableStatus
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.loggers.FOHServingLogger
import saarlacarte.restaurant.EscortingCustomers
import saarlacarte.restaurant.FOHLogger
import saarlacarte.restaurant.MAX_TICKLOAD
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * F21: FOH - Escorting.
 *
 * Exercises EscortingCustomers directly (rather than through FrontOfHouse) to close branches
 * that FOHEscortingTest.kt's fixtures never reach: a waiter that already has no remaining
 * ESCORT capacity, a waiter with only *partial* remaining capacity (partial escort across
 * ticks), a group that is fully escorted and consequently demerged/leaves, and the two
 * outcomes of demergeTables itself (FREE for casual groups, RESERVED for regular/event ones).
 */
class EscortingCustomersBranchTest {

    private fun casual(id: Int, size: Int) =
        CasualCustomerGroup(id, size, 1, listOf(), null, listOf(), listOf(), RatingLikelihood.NEVER)

    private fun regular(id: Int, size: Int) =
        RegularCustomerGroup(id, size, 1, listOf(), TableType.BAR, 0, 0, 0)

    private fun mockLoggers(): Pair<FOHLogger, FOHServingLogger> {
        val fohLogger = mock(FOHLogger::class.java)
        val servingLogger = mock(FOHServingLogger::class.java)
        whenever(fohLogger.loggerServing).thenReturn(servingLogger)
        return fohLogger to servingLogger
    }

    @Test
    fun `a waiter already at the escort tick limit escorts nobody`() {
        val group = casual(1, 4)
        group.isSeated = true
        group.peopleFinishedEating = group.howManySitting() // everyone ready to leave

        val waiter = Waiter()
        waiter.assignId(1)
        waiter.addCustomerGroup(group)
        waiter.performAction(WaiterAction.ESCORT, MAX_TICKLOAD) // no capacity left

        val table = Table(1, TableType.COMMON, 10)
        val seatedGroups: MutableMap<CustomerGroup, List<Table>> = mutableMapOf(group to listOf(table))
        val escorting = EscortingCustomers(mutableListOf(waiter), seatedGroups)
        val (fohLogger, servingLogger) = mockLoggers()
        escorting.loggers = fohLogger

        val left = escorting.escortCustomers()

        assertTrue(left.isEmpty())
        assertEquals(0, group.peopleEscorted)
        assertTrue(seatedGroups.containsKey(group))
        assertEquals(MAX_TICKLOAD, waiter.getTickLoad(WaiterAction.ESCORT))
        verify(servingLogger, never()).escorted(any(), any(), any(), any())
    }

    @Test
    fun `a waiter with only partial escort capacity escorts up to that capacity and leaves the rest seated`() {
        val group = casual(2, 5)
        group.isSeated = true
        group.peopleFinishedEating = group.howManySitting()

        val waiter = Waiter()
        waiter.assignId(1)
        waiter.addCustomerGroup(group)
        waiter.performAction(WaiterAction.ESCORT, MAX_TICKLOAD - 2) // only 2 escort slots left

        val table = Table(3, TableType.COMMON, 10)
        val seatedGroups: MutableMap<CustomerGroup, List<Table>> = mutableMapOf(group to listOf(table))
        val escorting = EscortingCustomers(mutableListOf(waiter), seatedGroups)
        val (fohLogger, servingLogger) = mockLoggers()
        escorting.loggers = fohLogger

        val left = escorting.escortCustomers()

        assertTrue(left.isEmpty()) // group is not fully gone yet
        assertEquals(2, group.peopleEscorted)
        assertTrue(seatedGroups.containsKey(group))
        assertEquals(MAX_TICKLOAD, waiter.getTickLoad(WaiterAction.ESCORT))
        verify(servingLogger).escorted(1, 2, group.id, 3)
    }

    @Test
    fun `a fully escorted casual group leaves and frees its tables`() {
        val group = casual(3, 3)
        group.isSeated = true
        group.peopleFinishedEating = group.howManySitting()

        val waiter = Waiter()
        waiter.assignId(1)
        waiter.addCustomerGroup(group)

        val table = Table(4, TableType.COMMON, 10)
        val seatedGroups: MutableMap<CustomerGroup, List<Table>> = mutableMapOf(group to listOf(table))
        val escorting = EscortingCustomers(mutableListOf(waiter), seatedGroups)
        val (fohLogger, _) = mockLoggers()
        escorting.loggers = fohLogger

        val left = escorting.escortCustomers()

        assertEquals(listOf(group), left)
        assertFalse(seatedGroups.containsKey(group))
        assertEquals(TableStatus.FREE, table.getStatus())
        assertFalse(group.isSeated)
        assertTrue(group.hasLeftVisit)
        // the waiter's assignment to the now-departed group was released
        assertTrue(waiter.getCustomerGroups().isEmpty())
    }

    @Test
    fun `demergeTables keeps regular and event tables reserved but frees casual ones`() {
        val regularGroup = regular(4, 2)
        val regularTable = Table(5, TableType.BAR, 10)
        regularTable.setStatus(TableStatus.OCCUPIED)

        val casualGroup = casual(5, 2)
        val casualTable = Table(6, TableType.COMMON, 10)
        casualTable.setStatus(TableStatus.OCCUPIED)

        val seatedGroups = mutableMapOf(
            regularGroup to listOf(regularTable),
            casualGroup to listOf(casualTable)
        )
        val escorting = EscortingCustomers(mutableListOf(), seatedGroups)

        escorting.demergeTables(regularGroup)
        escorting.demergeTables(casualGroup)

        assertEquals(TableStatus.RESERVED, regularTable.getStatus())
        assertEquals(TableStatus.FREE, casualTable.getStatus())
        assertFalse(seatedGroups.containsKey(regularGroup))
        assertFalse(seatedGroups.containsKey(casualGroup))
    }

    @Test
    fun `demergeTables on an unseated group is a no-op`() {
        val group = casual(6, 2)
        val seatedGroups = mutableMapOf<CustomerGroup, List<Table>>()
        val escorting = EscortingCustomers(mutableListOf(), seatedGroups)

        // group was never in seatedGroups; must not throw
        escorting.demergeTables(group)
        assertNull(seatedGroups[group])
    }
}
