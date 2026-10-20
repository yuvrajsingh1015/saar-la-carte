package general.incidentTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.delivery.Driver
import saarlacarte.enums.CookType
import saarlacarte.enums.StaffType
import saarlacarte.incidents.StaffIncident
import saarlacarte.restaurant.Staff

class StaffIncidentTest {
    private lateinit var staff: Staff

    @BeforeEach
    fun setup() {
        // we have 5 cooks
        val initialCooks = mapOf(
            CookType.EXEC to 1,
            CookType.SOUS to 2,
            CookType.TOURNANT to 0,
            CookType.SAUCE to 1,
            CookType.FISH to 0,
            CookType.ROAST to 0,
            CookType.VEGETABLE to 1,
            CookType.PASTRY to 0
        )
        staff = Staff(3, initialCooks, 0)
        val initialDrivers = listOf(Driver(), Driver())
        // add 2 drivers to the staff
        staff.getDrivers().addAll(initialDrivers)
    }

    // waitstaff
    @Test
    fun hireWaitstaff() {
        val initialWaitstaffSize = staff.getWaiters().size
        val incident = StaffIncident(
            1,
            1,
            2,
            StaffType.WAITSTAFF,
            staff
        )
        incident.execute()
        assertEquals(initialWaitstaffSize + 2, staff.getWaiters().size)
    }

    @Test
    fun fireWaitstaff() {
        val initialWaitstaffSize = staff.getWaiters().size
        val incident = StaffIncident(
            1,
            1,
            -2,
            StaffType.WAITSTAFF,
            staff
        )
        incident.execute()
        assertEquals(initialWaitstaffSize - 2, staff.getWaiters().size)
    }

    @Test
    fun fireUnavailableNumberOfWaitstaff() {
        val incident = StaffIncident(
            1,
            1,
            -20,
            StaffType.WAITSTAFF,
            staff
        )
        incident.execute()
        assertEquals(0, staff.getWaiters().size)
    }

    // cooks
    @Test
    fun hireNonEXECCooks() {
        val initialCookSize = staff.getCooks().size
        val incident = StaffIncident(
            1,
            1,
            2,
            StaffType.COOK,
            staff,
            CookType.SOUS
        )
        incident.execute()
        assertEquals(initialCookSize + 2, staff.getCooks().size)
        // we had 2 SOUS cooks initially
        assertEquals(4, staff.getCooks().count { it.getType() == CookType.SOUS })
    }

    @Test
    fun fireNonEXECCooks() {
        val initialCookSize = staff.getCooks().size
        val incident = StaffIncident(
            1,
            1,
            -2,
            StaffType.COOK,
            staff,
            CookType.SOUS
        )
        incident.execute()
        assertEquals(initialCookSize - 2, staff.getCooks().size)
        // we had 2 SOUS cooks initially
        assertEquals(0, staff.getCooks().count { it.getType() == CookType.SOUS })
    }

    @Test
    fun fireUnavailableNumberOfNonEXECCooks() {
        val initialCookSize = staff.getCooks().size
        val incident = StaffIncident(
            1,
            1,
            -5,
            StaffType.COOK,
            staff,
            CookType.SOUS
        )
        incident.execute()
        assertEquals(initialCookSize - 2, staff.getCooks().size)
        // we had 2 SOUS cooks initially
        assertEquals(0, staff.getCooks().count { it.getType() == CookType.SOUS })
    }

    // this already get handled in the parsing of incidents
//    @Test
//    fun hireEXECCooks() {
//        val initialCookSize = staff.getCooks().size
//        val incident = StaffIncident(
//            1,
//            1,
//            2,
//            StaffType.COOK,
//            staff,
//            CookType.EXEC
//        )
//        incident.execute()
//        // EXEC count can be 0 or 1
//        assertEquals(initialCookSize, staff.getCooks().size)
//        // we had 1 EXEC cook initially
//        assertEquals(1, staff.getCooks().count { it.getType() == CookType.EXEC })
//    }

    @Test
    fun fireEXECCooks() {
        val initialCookSize = staff.getCooks().size
        val incident = StaffIncident(
            1,
            1,
            -1,
            StaffType.COOK,
            staff,
            CookType.EXEC
        )
        incident.execute()
        // EXEC count can be 0 or 1
        assertEquals(initialCookSize - 1, staff.getCooks().size)
        // we had 1 EXEC cook initially
        assertEquals(0, staff.getCooks().count { it.getType() == CookType.EXEC })
    }

    // this is more of the parsing test
//    @Test
//    fun fireNullTypeCooks() {
//        val incident = StaffIncident(
//            1,
//            1,
//            -2,
//            StaffType.COOK,
//            staff,
//            null
//        )
//        incident.execute()
//        assertThrows<IllegalStateException> {
//            incident.execute()
//        }
//    }

    // drivers
    @Test
    fun hireDrivers() {
        val initialDriverSize = staff.getDrivers().size
        val incident = StaffIncident(
            1,
            1,
            2,
            StaffType.DRIVER,
            staff
        )
        incident.execute()
        assertEquals(initialDriverSize + 2, staff.getDrivers().size)
    }

    @Test
    fun fireDrivers() {
        val initialDriverSize = staff.getDrivers().size
        val incident = StaffIncident(
            1,
            1,
            -1,
            StaffType.DRIVER,
            staff
        )
        incident.execute()
        assertEquals(initialDriverSize - 1, staff.getDrivers().size)
    }

    @Test
    fun fireUnavailableNumberOfDrivers() {
        val incident = StaffIncident(
            1,
            1,
            -20,
            StaffType.DRIVER,
            staff
        )
        incident.execute()
        assertEquals(0, staff.getDrivers().size)
    }
}
