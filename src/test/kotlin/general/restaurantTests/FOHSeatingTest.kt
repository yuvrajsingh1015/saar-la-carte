package general.restaurantTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.enums.TableStatus
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Ticks
import kotlin.test.assertEquals
// testing selection of waitstaff
// testing actual seating of non-event customers

class FOHSeatingTest {

    private val waiter1: Waiter = mock()
    private val waiter2: Waiter = mock()
    private val waiter3: Waiter = mock()
    private val casuals = mock<CasualCustomerGroup>()
    private val fakeMenu: Menu = mock()
    private val table1 = Table(1, TableType.COMMON, 10)
    private val table2 = Table(2, TableType.COMMON, 20)
    private val table3 = Table(3, TableType.COMMON, 5)
    private val table4 = Table(4, TableType.BAR, 10)
    private val table5 = Table(5, TableType.SEPARATED, 7)
    lateinit var foh: FrontOfHouse

    @BeforeEach
    fun setup() {
        foh = FrontOfHouse(
            listOf(table1, table2, table3, table4, table5),
            mutableListOf(waiter1, waiter2, waiter3),
            null,
            fakeMenu
        )
    }

    @Test
    fun seatCasualWaiterSelectionDifferentLoad() {
        // can't seat 7 people
        whenever(waiter1.getId()).thenReturn(1)
        whenever(waiter1.getTickLoad(WaiterAction.SEAT)).thenReturn(5)
        whenever(waiter1.getCurrentLoad()).thenReturn(5)
        whenever(waiter1.canPerform(any(), any())).thenReturn(false)

        // should be chosen (current load<10 and more customers than waiter 3
        whenever(waiter2.getId()).thenReturn(2)
        whenever(waiter2.getTickLoad(WaiterAction.SEAT)).thenReturn(0)
        whenever(waiter2.getCurrentLoad()).thenReturn(2)
        whenever(waiter2.canPerform(any(), any())).thenReturn(true)

        // not chosen as current load less than of waiter 2
        whenever(waiter3.getId()).thenReturn(3)
        whenever(waiter3.getTickLoad(WaiterAction.SEAT)).thenReturn(1)
        whenever(waiter3.getCurrentLoad()).thenReturn(1)
        whenever(casuals.id).thenReturn(1)
        whenever(casuals.size).thenReturn(7)
        whenever(casuals.preferredTableType).thenReturn(TableType.COMMON)

        assertEquals(waiter2, foh.assignWaiter(casuals))
    }

    @Test
    fun seatCasualWaiterSelectionBusyWaiters() {
        // all waiters have at least 10 customers

        // should be chosen (lowest current load)
        whenever(waiter1.getId()).thenReturn(1)
        whenever(waiter1.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(2)
        whenever(waiter1.getCurrentLoad()).thenReturn(11)
        whenever(waiter1.canPerform(any(), any())).thenReturn(true)

        whenever(waiter2.getId()).thenReturn(2)
        whenever(waiter2.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(0)
        whenever(waiter2.getCurrentLoad()).thenReturn(12)
        whenever(waiter2.canPerform(any(), any())).thenReturn(true)

        whenever(waiter3.getId()).thenReturn(3)
        whenever(waiter3.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(1)
        whenever(waiter3.getCurrentLoad()).thenReturn(15)
        whenever(waiter3.canPerform(any(), any())).thenReturn(true)

        whenever(casuals.id).thenReturn(1)
        whenever(casuals.size).thenReturn(7)
        whenever(casuals.preferredTableType).thenReturn(TableType.COMMON)

        assertEquals(waiter1, foh.assignWaiter(casuals))
    }

    @Test
    fun seatCasualWaiterSelectionIdTiebreaker() {
        // all waiters have at least 10 customers

        // current load > 10 -> no
        whenever(waiter1.getId()).thenReturn(1)
        whenever(waiter1.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(0)
        whenever(waiter1.getCurrentLoad()).thenReturn(11)
        whenever(waiter1.canPerform(eq(WaiterAction.SEAT), eq(7))).thenReturn(true)

        // tie -> should be chosen by id despite lower seating load
        whenever(waiter2.getId()).thenReturn(2)
        whenever(waiter2.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(1)
        whenever(waiter2.getCurrentLoad()).thenReturn(10)
        whenever(waiter2.canPerform(eq(WaiterAction.SEAT), eq(7))).thenReturn(true)

        whenever(waiter3.getId()).thenReturn(3)
        whenever(waiter3.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(2)
        whenever(waiter3.getCurrentLoad()).thenReturn(10)
        whenever(waiter3.canPerform(eq(WaiterAction.SEAT), eq(7))).thenReturn(true)

        whenever(casuals.id).thenReturn(1)
        whenever(casuals.size).thenReturn(7)
        whenever(casuals.preferredTableType).thenReturn(TableType.COMMON)

        assertEquals(waiter2, foh.assignWaiter(casuals))
    }

    @Test
    fun seatCasualWaiterSelectionNoWaiters() {
        // all waiters have at least 10 customers

        // current load > 10 -> no
        whenever(waiter1.getId()).thenReturn(1)
        whenever(waiter1.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(9)
        whenever(waiter1.getCurrentLoad()).thenReturn(9)
        whenever(waiter1.canPerform(eq(WaiterAction.SEAT), eq(7))).thenReturn(false)

        // tie -> should be chosen by id despite lower seating load
        whenever(waiter2.getId()).thenReturn(2)
        whenever(waiter2.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(8)
        whenever(waiter2.getCurrentLoad()).thenReturn(10)
        whenever(waiter2.canPerform(eq(WaiterAction.SEAT), eq(7))).thenReturn(false)

        whenever(waiter3.getId()).thenReturn(3)
        whenever(waiter3.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(10)
        whenever(waiter3.getCurrentLoad()).thenReturn(10)
        whenever(waiter3.canPerform(eq(WaiterAction.SEAT), eq(7))).thenReturn(false)

        whenever(casuals.id).thenReturn(1)
        whenever(casuals.size).thenReturn(7)
        whenever(casuals.preferredTableType).thenReturn(TableType.COMMON)

        assertEquals(null, foh.assignWaiter(casuals))
    }

    @Test
    fun seatCasualWaiterSelectionJustOpened() {
        // all waiters have at least 10 customers

        // current load > 10 -> no
        whenever(waiter1.getId()).thenReturn(null)
        whenever(waiter1.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(0)
        whenever(waiter1.getCurrentLoad()).thenReturn(0)
        whenever(waiter1.canPerform(eq(WaiterAction.SEAT), eq(7))).thenReturn(true)

        whenever(casuals.id).thenReturn(1)
        whenever(casuals.size).thenReturn(7)
        whenever(casuals.preferredTableType).thenReturn(TableType.COMMON)

        assertEquals(waiter1, foh.assignWaiter(casuals))
    }

    @Test
    fun seatCasualWaiterSelectionLowLoadTiebreaker() {
        // Waiter 1 and Waiter 2 both have currentLoad < 10 and are tied with a load of 4
        whenever(waiter1.getId()).thenReturn(1)
        whenever(waiter1.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(0)
        whenever(waiter1.getCurrentLoad()).thenReturn(4)
        whenever(waiter1.canPerform(any(), any())).thenReturn(true)

        whenever(waiter2.getId()).thenReturn(2)
        whenever(waiter2.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(0)
        whenever(waiter2.getCurrentLoad()).thenReturn(4)
        whenever(waiter2.canPerform(any(), any())).thenReturn(true)

        // Waiter 3 has a lower load (2), so they will not be in the maximum load pool
        whenever(waiter3.getId()).thenReturn(3)
        whenever(waiter3.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(0)
        whenever(waiter3.getCurrentLoad()).thenReturn(2)
        whenever(waiter3.canPerform(any(), any())).thenReturn(true)

        whenever(casuals.id).thenReturn(1)
        whenever(casuals.size).thenReturn(5)
        whenever(casuals.preferredTableType).thenReturn(TableType.COMMON)

        assertEquals(waiter1, foh.assignWaiter(casuals))
    }

    @Test
    fun seatCasualActualSeatingSuccessful() {
        // Serving phase tick 1 setup
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()

        whenever(casuals.id).thenReturn(10)
        whenever(casuals.size).thenReturn(5)
        whenever(casuals.visitingTick).thenReturn(1)
        whenever(casuals.hasLeftVisit).thenReturn(false)
        whenever(casuals.isSeated).thenReturn(false)
        whenever(casuals.isArriving()).thenReturn(true)
        whenever(casuals.preferredTableType).thenReturn(TableType.COMMON)

        // Waiter 1 is available and eligible to perform the seating
        whenever(waiter1.getId()).thenReturn(1)
        whenever(waiter1.getTickLoad(eq(WaiterAction.SEAT))).thenReturn(0)
        whenever(waiter1.getCurrentLoad()).thenReturn(0)
        whenever(waiter1.canPerform(eq(WaiterAction.SEAT), eq(5))).thenReturn(true)

        whenever(waiter2.canPerform(any(), any())).thenReturn(false)
        whenever(waiter3.canPerform(any(), any())).thenReturn(false)

        assertEquals(TableStatus.FREE, table3.getStatus())

        // Execute the actual seating logic
        foh.seatCustomers(listOf(casuals))

        // Table 3 (size 5, COMMON) is an exact fit and must be marked OCCUPIED
        assertEquals(TableStatus.OCCUPIED, table3.getStatus())
        assertEquals(listOf(table3), foh.seatedGroups[casuals])

        // Waiter 1 must receive the SEAT action tick load and be assigned the customer group
        verify(waiter1).performAction(WaiterAction.SEAT, 5)
        verify(waiter1).addCustomerGroup(casuals)
    }
}
