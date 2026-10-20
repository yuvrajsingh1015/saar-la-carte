package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.loggers.FOHServingLogger
import saarlacarte.restaurant.FOHLogger
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import kotlin.test.assertTrue
import kotlin.to

// Really hard to work on unit tests,
//  maybe leave for component/system tests ?

/** Tests for F21: FOH - Escorting **/
class FOHEscortingTest {
    private lateinit var mockServingLogger: FOHServingLogger
    private lateinit var mockFOHLogger: FOHLogger

    private val casualGroup1 = CasualCustomerGroup(
        1,
        5,
        5,
        listOf(),
        null,
        listOf(),
        listOf(),
        RatingLikelihood.NEVER
    )

    private val regularGroup2 = RegularCustomerGroup(
        2,
        8,
        10,
        listOf(),
        TableType.BAR,
        0,
        0,
        0
    )

    private val eventGroup3 = EventCustomerGroup(
        3,
        30,
        15,
        listOf(),
        TableType.COMMON,
        listOf(),
        0,
        mutableMapOf()
    )

    private val table1 = Table(1, TableType.COMMON, 10)
    private val table2 = Table(2, TableType.COMMON, 20)
    private val table3 = Table(3, TableType.COMMON, 5)
    private val table4 = Table(4, TableType.BAR, 10)
    private val table5 = Table(5, TableType.SEPARATED, 7)

    private val mockMenu = mock<Menu>()

    private val waiter1 = Waiter()
    private val waiter2 = Waiter()
    private val waiter3 = Waiter()

    private lateinit var foh: FrontOfHouse

    @BeforeEach
    fun setUp() {
        mockServingLogger = mock()
        mockFOHLogger = mock()
        whenever(mockFOHLogger.loggerServing).thenReturn(mockServingLogger)

        waiter1.assignId(1)
        waiter1.addCustomerGroup(casualGroup1)
        waiter2.assignId(2)
        waiter2.addCustomerGroup(regularGroup2)
        waiter3.assignId(3)
        // waiter3.addCustomerGroup(eventGroup3)

        foh = FrontOfHouse(
            listOf(table1, table2, table3, table4, table5),
            mutableListOf(waiter1, waiter2, waiter3),
            null,
            mockMenu
        )
        foh.seatedGroups.putAll(
            mutableMapOf(
                casualGroup1 to listOf(table3),
                regularGroup2 to listOf(table4),
                eventGroup3 to listOf(table1, table2)
            )
        )
        foh.assignLoggers(mockFOHLogger)
    }

    /*
    RULES:
        - Order: ascending customer type, then group id
        - Can be performed also partially in case the waitstaff
            doesn’t have enough capacity left
        - Tables cannot be changed until the group sitting on them has
            left completely
        - For EVENT groups, the waitstaff manager prioritizes waiters
            with the lowest current load.
        - Tiebreaker: lowest id
    */
    @Test
    fun `escort no customers if they are still eating`() {
        casualGroup1.peopleFinishedEating = 0
        regularGroup2.peopleFinishedEating = 0
        eventGroup3.peopleFinishedEating = 0
        assertTrue(foh.escortCustomers().isEmpty())
    }

    /*
    @Test
    fun `escort everyone who finished eating`() {
        casualGroup1.peopleFinishedEating = 5 // their size
        regularGroup2.peopleFinishedEating = 8 // their size
        eventGroup3.peopleFinishedEating = 0
        assertEquals(listOf(casualGroup1, regularGroup2), foh.escortCustomers())
    }
    */

    @Test
    fun `escort event group prioritizes lowest current load over who served them`() {
        // waiter1 (id 1) is still carrying casualGroup1's load (5), waiter2 (id 2) is
        // still carrying regularGroup2's load (8), waiter3 (id 3) never seated anyone and
        // never touched this event, so its current load is 0. Neither waiter1 nor waiter2
        // ever served eventGroup3 in this fixture.
        eventGroup3.isSeated = true
        eventGroup3.peopleFinishedEating = 30 // whole group ready to leave

        foh.escortCustomers()

        // spec p.24: "For EVENT groups, the waitstaff manager prioritizes waiters with
        // the lowest current load" - the manager picks by load alone, not by who served
        // the group, so waiter3 (load 0) must go first, then waiter1 (load 5), then
        // waiter2 (load 8), each escorting up to their 10-action limit.
        assertEquals(10, waiter3.getTickLoad(WaiterAction.ESCORT))
        assertEquals(10, waiter1.getTickLoad(WaiterAction.ESCORT))
        assertEquals(10, waiter2.getTickLoad(WaiterAction.ESCORT))
    }
}
