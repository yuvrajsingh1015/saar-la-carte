package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableStatus
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals

class EventSeatingTest {
    val tables = listOf(
        Table(1, TableType.COMMON, 5),
        Table(2, TableType.COMMON, 5),
        Table(3, TableType.COMMON, 5),
        Table(4, TableType.COMMON, 5),
        Table(5, TableType.COMMON, 5)
    )
    val waiters = mutableListOf<Waiter>()
    lateinit var frontOfHouse: FrontOfHouse

    fun createEvent(id: Int, tick: Int, size: Int): EventCustomerGroup {
        return EventCustomerGroup(
            id,
            size,
            tick,
            listOf(),
            TableType.COMMON,
            listOf(RestaurantType.AFRICAN),
            5,
            mutableMapOf(RestaurantType.AFRICAN to "interesting dish"),
        )
    }

    fun assignAllWaiterIds() {
        var id = 1
        for (waiter in waiters) {
            waiter.assignId(id)
            id++
        }
    }

    @BeforeEach
    fun setupFOH() {
        waiters.clear()
        repeat(2) {
            waiters.add(Waiter())
        }
        frontOfHouse = FrontOfHouse(
            tables,
            waiters,
            null,
            mock<Menu>()
        )
        frontOfHouse.endOfEvening()
        Ticks.init(1000)
        repeat(5) {
            Ticks.advanceEvening()
        }
    }

    @Test
    fun `test seating event groups to correct reserved tables`() {
        val event1 = createEvent(1, 2, 9)
        val event2 = createEvent(2, 1, 7)
        frontOfHouse.managerAssignTables(listOf(event1, event2))
        Ticks.advanceTick()
        // event 1 reserved 1,2 tables; event 2 reserved 3,4 tables
        frontOfHouse.seatCustomers(listOf(event2))
        tables.forEach {
            if (it.getId() == 3 || it.getId() == 4) {
                assertEquals(TableStatus.OCCUPIED, it.getStatus())
            } else if (it.getId() == 5) {
                assertEquals(TableStatus.FREE, it.getStatus())
            } else {
                assertEquals(TableStatus.RESERVED, it.getStatus())
            }
        }
        Ticks.advanceTick()
        frontOfHouse.seatCustomers(listOf(event1))
        tables.forEach {
            if (it.getId() != 5) {
                assertEquals(TableStatus.OCCUPIED, it.getStatus())
            } else {
                assertEquals(TableStatus.FREE, it.getStatus())
            }
        }
    }

    @Test
    fun `test correct selection of waiters for event groups in same tick`() {
        val event1 = createEvent(1, 10, 9)
        val event2 = createEvent(2, 10, 7)
        frontOfHouse.managerAssignTables(listOf(event1, event2))
        repeat(10) {
            Ticks.advanceTick()
        }
        assignAllWaiterIds()

        frontOfHouse.seatCustomers(listOf(event1, event2))
        // waiter 1 seated the whole event1 and 1 person from event 2 (tiebreaker is always lowest id)
        assertEquals(
            10,
            waiters.find { it.getId() == 1 }?.getTickLoad(WaiterAction.SEAT) ?: 0
        )

        // waiter 2 seats remaining 6 people from event2
        assertEquals(
            6,
            waiters.find { it.getId() == 2 }?.getTickLoad(WaiterAction.SEAT) ?: 0
        )

        // shouldn't count to currentLoad
        waiters.forEach { assertEquals(0, it.getCurrentLoad()) }
    }

    @Test
    fun `test correct selection of waiters for a big event group`() {
        val event1 = createEvent(1, 10, 15)
        frontOfHouse.managerAssignTables(listOf(event1))
        repeat(10) {
            Ticks.advanceTick()
        }
        // one waiter was busy but his customers left, while one hasn't been active yet
        waiters.first().assignId(1)

        frontOfHouse.seatCustomers(listOf(event1))
        // waiter 1 seated the 10 people from event1 (chosen before the waiter with null)
        assertEquals(
            10,
            waiters.find { it.getId() == 1 }?.getTickLoad(WaiterAction.SEAT) ?: 0
        )

        // waiter with null seats remaining 5 people and obtains id 2
        assertEquals(
            5,
            waiters.find { it.getId() == 2 }?.getTickLoad(WaiterAction.SEAT) ?: 0
        )
    }

    @Test
    fun `test correct selection of waiters different current load`() {
        val event1 = createEvent(1, 10, 15)
        val previousGroup1 = mock<CasualCustomerGroup>()
        whenever(previousGroup1.size).thenReturn(4)
        frontOfHouse.managerAssignTables(listOf(event1))
        repeat(10) {
            Ticks.advanceTick()
        }
        // waiter 2 has a higher current load -> should be prioritized
        assignAllWaiterIds()

        waiters.find { it.getId() == 2 }?.performAction(WaiterAction.SEAT, 4)
        waiters.find { it.getId() == 2 }?.addCustomerGroup(previousGroup1)

        frontOfHouse.seatCustomers(listOf(event1))
        // waiter 2 seated the 6 people reaching max tick load
        assertEquals(
            10,
            waiters.find { it.getId() == 2 }?.getTickLoad(WaiterAction.SEAT) ?: 0
        )

        // waiter 1 seats the remaining 9 people
        assertEquals(
            9,
            waiters.find { it.getId() == 1 }?.getTickLoad(WaiterAction.SEAT) ?: 0
        )
    }

    /*@Test
    fun `test failed seating of event group`() {
        val event1 = createEvent(1, 10, 5)
        val previousGroup1 = mock<CasualCustomerGroup>()
        val previousGroup2 = mock<CasualCustomerGroup>()
        whenever(previousGroup1.size).thenReturn(9)
        whenever(previousGroup2.size).thenReturn(9)
        frontOfHouse.managerAssignTables(listOf(event1))
        repeat(10) {
            Ticks.advanceTick()
        }

        // making waiters too busy to seat the event group
        assignAllWaiterIds()
        waiters.forEach { it.performAction(WaiterAction.SEAT, 9) }
        waiters[0].addCustomerGroup(previousGroup1)
        waiters[1].addCustomerGroup(previousGroup2)

        frontOfHouse.seatCustomers(listOf(event1))
        // waiters still perform partial seating, but later event leaves (forum post)
        waiters.forEach { assertEquals(10, it.getTickLoad(WaiterAction.SEAT)) }

        // tables for event are not occupied
        assertEquals(TableStatus.RESERVED, tables.find { it.getId() == 1 }?.getStatus() ?: TableStatus.OCCUPIED)
    }*/
    @Test
    fun `event seating prioritizes waiters with highest current load and breaks ties by lowest id`() {
        waiters.add(Waiter()) // 3 waiters total
        assignAllWaiterIds() // ID 1, 2, 3

        val casualLow = mock<CasualCustomerGroup>()
        val casualHigh1 = mock<CasualCustomerGroup>()
        val casualHigh2 = mock<CasualCustomerGroup>()
        whenever(casualLow.size).thenReturn(2)
        whenever(casualHigh1.size).thenReturn(6)
        whenever(casualHigh2.size).thenReturn(6)

        waiters[0].addCustomerGroup(casualLow) // Waiter 1: currentLoad = 2
        waiters[1].addCustomerGroup(casualHigh1) // Waiter 2: currentLoad = 6
        waiters[2].addCustomerGroup(casualHigh2) // Waiter 3: currentLoad = 6

        // Size 13 will merge tables 1, 2, and 3 (total size 15)
        val event = createEvent(id = 10, tick = 1, size = 13)
        frontOfHouse.managerAssignTables(listOf(event))
        Ticks.advanceTick()

        frontOfHouse.seatCustomers(listOf(event))

        // Waiter 2 & 3 tie at currentLoad = 6; Waiter 2 (lower ID) is picked first and takes 10
        assertEquals(10, waiters[1].getTickLoad(WaiterAction.SEAT))
        // Waiter 3 takes the remaining 3
        assertEquals(3, waiters[2].getTickLoad(WaiterAction.SEAT))
        // Waiter 1 with lower currentLoad is not used
        assertEquals(0, waiters[0].getTickLoad(WaiterAction.SEAT))

        // Confirm group is seated and tables are occupied
        assertTrue(event.isSeated)
        assertEquals(TableStatus.OCCUPIED, tables[0].getStatus())
        assertEquals(TableStatus.OCCUPIED, tables[1].getStatus())
        assertEquals(TableStatus.OCCUPIED, tables[2].getStatus())

        // Event customers must not increase waiters' currentLoad
        assertEquals(2, waiters[0].getCurrentLoad())
        assertEquals(6, waiters[1].getCurrentLoad())
        assertEquals(6, waiters[2].getCurrentLoad())
    }

    @Test
    fun `event seating fills remaining capacity of waiters with existing tick load`() {
        assignAllWaiterIds() // ID 1, ID 2 (both currentLoad = 0)

        // Waiter 1 already used 4 seating capacity this tick (6 remaining)
        waiters[0].performAction(WaiterAction.SEAT, 4)

        // Event of size 11 requires both waiters
        val event = createEvent(id = 20, tick = 1, size = 11)
        frontOfHouse.managerAssignTables(listOf(event))
        Ticks.advanceTick()

        frontOfHouse.seatCustomers(listOf(event))

        // Waiter 1 fills remaining 6 to reach the tick limit of 10
        assertEquals(10, waiters[0].getTickLoad(WaiterAction.SEAT))
        // Waiter 2 handles the remaining 5
        assertEquals(5, waiters[1].getTickLoad(WaiterAction.SEAT))

        assertTrue(event.isSeated)
        assertEquals(TableStatus.OCCUPIED, tables[0].getStatus())
        assertEquals(TableStatus.OCCUPIED, tables[1].getStatus())
        assertEquals(TableStatus.OCCUPIED, tables[2].getStatus())
    }

    @Test
    fun `test P02 - event seating fails completely if total waitstaff capacity cannot seat entire group`() {
        assignAllWaiterIds() // 2 waiters available (max combined seating capacity = 20)

        // Event size 22 exceeds combined capacity of 20
        val event = createEvent(id = 30, tick = 1, size = 22)
        frontOfHouse.managerAssignTables(listOf(event))
        Ticks.advanceTick()

        frontOfHouse.seatCustomers(listOf(event))

        // Group must not be seated
        assertFalse(event.isSeated)

        // Reserved tables must remain RESERVED, not OCCUPIED
        tables.forEach { table ->
            assertEquals(TableStatus.RESERVED, table.getStatus())
        }

        // Forum: partial SEATING actions still happen and are not rolled back when the
        // overall EVENT seating attempt fails. Both waiters are maxed out (10 + 10 = 20 < 22).
        waiters.forEach { waiter ->
            assertEquals(10, waiter.getTickLoad(WaiterAction.SEAT))
        }
    }
}
