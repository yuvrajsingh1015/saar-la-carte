package general.incidentTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.CookType
import saarlacarte.enums.StaffType
import saarlacarte.incidents.StaffIncident
import saarlacarte.restaurant.Staff

/**
 * F31: Incident - Staff Change.
 *
 * StaffIncidentTest.kt (existing, by a teammate) thoroughly covers hire/fire for every
 * StaffType with positive/negative counts. Two branches it does not touch:
 *  - `execute()`'s `if (number > 0) hire(number) else fire(-number)`: a `number == 0` incident
 *    takes the `else` branch and calls `fire(0)`, which must be a genuine no-op rather than a
 *    crash (StaffIncidentTest.kt's `fireUnavailableNumberOfWaitstaff`/`...Cooks` cases all use
 *    a strictly negative `number`, never exactly 0).
 *  - Whether a StaffIncident can hire an EXEC cook at all is a *parser* rule (ScenarioParser
 *    rejects "cookType": "EXEC" for STAFF incidents, see IncidentParserTest.avoidCookTypeExecSI
 *    and FoodParser's cook-type handling), not something StaffIncident.hire() itself forbids.
 *    StaffIncidentTest.kt leaves its `hireEXECCooks` case commented out with the note "this
 *    already get handled in the parsing of incidents"; this test documents that the class
 *    itself has no such restriction, clarifying the class/parser responsibility boundary.
 */
class StaffIncidentZeroAndExecBranchTest {
    private lateinit var staff: Staff

    @BeforeEach
    fun setup() {
        val initialCooks = mapOf(
            CookType.EXEC to 1,
            CookType.SOUS to 1,
            CookType.TOURNANT to 0,
            CookType.SAUCE to 0,
            CookType.FISH to 0,
            CookType.ROAST to 0,
            CookType.VEGETABLE to 0,
            CookType.PASTRY to 0
        )
        staff = Staff(3, initialCooks, 0)
    }

    @Test
    fun `a staff incident with number zero fires nobody and changes nothing`() {
        val initialWaitstaffSize = staff.getWaiters().size
        val incident = StaffIncident(1, 1, 0, StaffType.WAITSTAFF, staff)
        incident.execute()
        assertEquals(initialWaitstaffSize, staff.getWaiters().size)
    }

    @Test
    fun `a driver incident with number zero is also a no-op`() {
        val initialDriverSize = staff.getDrivers().size
        val incident = StaffIncident(1, 1, 0, StaffType.DRIVER, staff)
        incident.execute()
        assertEquals(initialDriverSize, staff.getDrivers().size)
    }

    @Test
    fun `StaffIncident itself allows hiring an EXEC cook, that restriction lives in the parser`() {
        val initialCookSize = staff.getCooks().size
        val incident = StaffIncident(1, 1, 1, StaffType.COOK, staff, CookType.EXEC)
        incident.execute()
        assertEquals(initialCookSize + 1, staff.getCooks().size)
        assertEquals(2, staff.getCooks().count { it.getType() == CookType.EXEC })
    }
}
