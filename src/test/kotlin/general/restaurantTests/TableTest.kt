package general.restaurantTests

import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableStatus
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.collections.iterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
TABLE MERGING RULES [P14-15]:
1. All occupied or reserved tables are ignored, so that there is at most one group per
    (merged) table.
2. In case the group size fits a table perfectly, they are assigned that table.
3. In case there are tables with a larger size available, if the group size is at least 3/4
    of the table’s size, they are assigned that table.
4. In case there are smaller tables, those smaller tables will be merged from the smallest
    to the largest table until the group size is met. In case the final table size is above
    the group size, the smallest table in this merge is removed iteratively (in order of
    ascending id), so long as the table size remains greater or equal to the group size.
    The group must cover at least 3/4 of the resulting merged table.
 */
/** Unit tests for table logic **/
class TableTest {
    val waiters = mutableListOf(Waiter())
    val potato = Ingredient("potato", MeasuringUnit.X, 5)
    val supplier = Supplier(
        ingredients = listOf(potato),
        packagingVolumes = mutableMapOf("potato" to 2)
    )
    val recipes = listOf(
        Recipe(
            1,
            "double potato",
            0,
            mutableMapOf(potato to 2),
            listOf(CookType.SOUS)
        )
    )
    val menu = Menu(supplier, recipes, RestaurantType.AFRICAN)
    val regGroup = RegularCustomerGroup(
        1,
        4,
        1,
        emptyList(),
        TableType.COMMON,
        1,
        3,
        1
    )
    val eventGroupSeparated = EventCustomerGroup(
        2,
        15,
        1,
        emptyList(),
        TableType.SEPARATED,
        listOf(RestaurantType.AFRICAN),
        4,
        mutableMapOf(RestaurantType.AFRICAN to "chicken rice")
    )
    val eventGroupBar = EventCustomerGroup(
        2,
        11,
        1,
        emptyList(),
        TableType.BAR,
        listOf(RestaurantType.AFRICAN),
        4,
        mutableMapOf(RestaurantType.AFRICAN to "chicken rice")
    )
    val casualGroupCommon = CasualCustomerGroup(
        3,
        8,
        1,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.EUROPEAN, RestaurantType.AMERICAN),
        listOf(1, 4, 9),
        RatingLikelihood.NEVER
    )
    val casualGroupBar = CasualCustomerGroup(
        4,
        6,
        1,
        emptyList(),
        TableType.BAR,
        listOf(RestaurantType.EUROPEAN, RestaurantType.AMERICAN),
        listOf(1, 4, 5),
        RatingLikelihood.SOME
    )

    // can create FOH with a custom list of tables
    private fun dummyFoh(tables: List<Table>): FrontOfHouse {
        return FrontOfHouse(tables, waiters, null, menu)
    }

    /* takes list of sizes of tables for each type and creates these tables
    id assigned starting from 1 increasing by 1 in the same order as given in the function call */
    private fun dummyTables(sizesPerType: Map<TableType, List<Int>>): List<Table> {
        val tables = mutableListOf<Table>()
        var i = 1
        for ((type, list) in sizesPerType) {
            for (size in list) {
                tables.add(Table(i, type, size))
                i++
            }
        }
        return tables
    }

    // always consider tick 1 of evening 4
    @BeforeEach
    fun resetTicks() {
        Ticks.init(10)
        repeat(4) {
            Ticks.advanceEvening()
        }
    }

    // tests of
    @Test
    fun `findTable regular merge`() {
        val tables = dummyTables(mapOf(TableType.BAR to listOf(4), TableType.COMMON to listOf(1, 2, 3)))
        val foh = dummyFoh(tables)
        val found = foh.findTables(regGroup)
        // should be tables with ids 3,4 -> 3 (sizes 2+3=5)
        val table3 = tables.find { it.getId() == 3 }
        val table4 = tables.find { it.getId() == 4 }
        assert(found.sortedBy { it.getId() } == listOf(table3, table4))
    }

    @Test
    fun `managerAssignTable regular merge no bigger table`() {
        val tables = dummyTables(mapOf(TableType.BAR to listOf(4), TableType.COMMON to listOf(1, 2, 3)))
        val foh = dummyFoh(tables)
        foh.managerAssignTables(listOf(regGroup))
        // should be reserved tables with ids 3,4 -> 3 (sizes 2+3=5), no others
        tables.forEach {
            if (it.getId() == 3 || it.getId() == 4) {
                assert(it.getStatus() == TableStatus.RESERVED)
            } else {
                assert(it.getStatus() == TableStatus.FREE)
            }
        }
    }

    @Test
    fun `findTable regular no merge bigger single table`() {
        val tables = dummyTables(mapOf(TableType.BAR to listOf(4), TableType.COMMON to listOf(1, 2, 3, 5)))
        val foh = dummyFoh(tables)
        val found = foh.findTables(regGroup)
        // exists a bigger single table with 3/4 (size 5, id 5)
        val table5 = tables.find { it.getId() == 5 }
        assert(found == listOf(table5))
    }

    @Test
    fun `managerAssignTable regular merge only big tables ignore occupied`() {
        val tables = dummyTables(mapOf(TableType.COMMON to listOf(4, 1, 2, 3, 6)))
        val foh = dummyFoh(tables)
        // make table 1 (perfect match) occupied
        val table1 = tables[0]
        table1.setStatus(TableStatus.OCCUPIED)
        foh.managerAssignTables(listOf(regGroup))
        // should be reserved tables with ids 3,4 -> 3 (sizes 2+3=5)
        tables.forEach {
            if (it.getId() == 3 || it.getId() == 4) {
                assert(it.getStatus() == TableStatus.RESERVED)
            } else if (it.getId() == 1) {
                assert(it.getStatus() == TableStatus.OCCUPIED)
            } else {
                assert(it.getStatus() == TableStatus.FREE)
            }
        }
    }

    @Test
    fun `findTable event separated exactly on 3 4 border suitable`() {
        val tables = dummyTables(mapOf(TableType.SEPARATED to listOf(20)))
        val foh = dummyFoh(tables)
        // group size 15 (20*3/4=15)
        val found = foh.findTables(eventGroupSeparated)
        assert(found == tables)
    }

    @Test
    fun `findTable event separated merge many small tables`() {
        val tables = dummyTables(mapOf(TableType.SEPARATED to listOf(1, 2, 1, 1, 1, 2, 1, 2, 3, 1, 2)))
        val foh = dummyFoh(tables)
        // group size 15
        val foundIds = foh.findTables(eventGroupSeparated).map { it.getId() }.sorted()
        assert(foundIds == listOf(4, 5, 7, 10, 2, 6, 8, 11, 9).sorted())
    }

    @Test
    fun `findTable event bar tiebreaker for multiple tables fulfilling condition`() {
        val tables = dummyTables(mapOf(TableType.SEPARATED to listOf(12), TableType.BAR to listOf(14, 13, 12, 13, 12)))
        val foh = dummyFoh(tables)
        // group size: 11
        val found = foh.findTables(eventGroupBar)
        // table 4 with size 12 should be chosen according to tiebreaker
        val table4 = tables.find { it.getId() == 4 }
        assert(found == listOf(table4))
    }

    @Test
    fun `findTable event separated beyond 3 4 rule single table found`() {
        val tables = dummyTables(mapOf(TableType.SEPARATED to listOf(21, 10, 11)))
        val foh = dummyFoh(tables)
        // group size: 15
        val found = foh.findTables(eventGroupSeparated)
        // table 1 with size 21 should be chosen in step 5
        val table1 = tables.find { it.getId() == 1 }
        assert(found == listOf(table1))
    }

    @Test
    fun `findTable regular beyond 3 4 rule merge`() {
        val tables = dummyTables(mapOf(TableType.COMMON to listOf(3, 3)))
        val foh = dummyFoh(tables)
        // group size: 4
        val found = foh.findTables(regGroup)
        // tables 1, 2 (all tables) with size 3+3=6 should be chosen in step 6
        assert(found.sortedBy { it.getId() } == tables)
    }

    @Test
    fun `findTable event bar not found cannot merge bar`() {
        val tables = dummyTables(mapOf(TableType.SEPARATED to listOf(5, 5), TableType.BAR to listOf(10, 1)))
        val foh = dummyFoh(tables)
        // group size: 11
        val found = foh.findTables(eventGroupBar)
        // no single bar table fulfilling condition
        assert(found.isEmpty())
    }

    @Test
    fun `managerAssignTable regular cannot find table`() {
        val tables = dummyTables(mapOf(TableType.COMMON to listOf(1, 1, 1)))
        val foh = dummyFoh(tables)
        // group size 4
        foh.managerAssignTables(listOf(regGroup))
        // can't find table -> all should be free
        tables.forEach {
            assert(it.getStatus() == TableStatus.FREE)
        }
    }

    @Test
    fun `findTable event separated not found`() {
        val tables = dummyTables(
            mapOf(
                TableType.SEPARATED to listOf(5, 5, 1, 1, 1, 1),
                TableType.COMMON to listOf(10, 5)
            )
        )
        val foh = dummyFoh(tables)
        // group size: 15
        val found = foh.findTables(eventGroupSeparated)
        assert(found.isEmpty())
    }

    // table merging tests

    @Test
    fun `findTables ignores occupied&reserved tables`() {
        val mockTable1 = mock<Table>()
        val mockTable2 = mock<Table>()
        whenever(mockTable1.getStatus()).thenReturn(TableStatus.OCCUPIED)
        whenever(mockTable2.getStatus()).thenReturn(TableStatus.RESERVED)

        val foh = dummyFoh(listOf(mockTable1, mockTable2))
        val found = foh.findTables(casualGroupCommon)
        assertTrue(found.isEmpty())
    }

    @Test
    fun `casualGroup COMMON three-quarters rule table found`() {
        val table1 = Table(1, TableType.COMMON, 5)
        val table2 = Table(2, TableType.COMMON, 9)

        val foh = dummyFoh(listOf(table1, table2))
        val found = foh.findTables(casualGroupCommon)
        assertEquals(listOf(table2), found)
    }

    @Test
    fun `casualGroup COMMON three-quarters rule table not found`() {
        val mockTable1 = mock<Table>()
        whenever(mockTable1.getSize()).thenReturn(5)
        whenever(mockTable1.getType()).thenReturn(TableType.COMMON)

        val foh = dummyFoh(listOf(mockTable1))
        val found = foh.findTables(casualGroupCommon)
        assertTrue(found.isEmpty())
    }

    @Test
    fun `casualGroup COMMON find merged table`() {
        val table1 = Table(1, TableType.COMMON, 5)
        val table2 = Table(2, TableType.COMMON, 5)

        val foh = dummyFoh(listOf(table1, table2))
        val found = foh.findTables(casualGroupCommon)
        assertEquals(listOf(table1, table2), found)
    }

    @Test
    fun `casualGroup COMMON merged table not found`() {
        val mockTable1 = mock<Table>()
        val mockTable2 = mock<Table>()
        whenever(mockTable1.getSize()).thenReturn(5)
        whenever(mockTable1.getType()).thenReturn(TableType.COMMON)
        whenever(mockTable2.getSize()).thenReturn(6)
        whenever(mockTable2.getType()).thenReturn(TableType.COMMON)

        val foh = dummyFoh(listOf(mockTable1, mockTable2))
        val found = foh.findTables(casualGroupCommon)
        assertTrue(found.isEmpty())
    }

    @Test
    fun `casualGroup BAR tables not merged`() {
        val mockTable1 = mock<Table>()
        val mockTable2 = mock<Table>()
        whenever(mockTable1.getSize()).thenReturn(5)
        whenever(mockTable1.getType()).thenReturn(TableType.BAR)
        whenever(mockTable2.getSize()).thenReturn(5)
        whenever(mockTable2.getType()).thenReturn(TableType.BAR)

        val foh = dummyFoh(listOf(mockTable1, mockTable2))
        val found = foh.findTables(casualGroupCommon)
        assertTrue(found.isEmpty())
    }

    @Test
    fun `freeSeatCount test`() {
        val mockTable1 = mock<Table>()
        whenever(mockTable1.getSize()).thenReturn(5)
        whenever(mockTable1.getStatus()).thenReturn(TableStatus.FREE)
        val mockTable2 = mock<Table>()
        whenever(mockTable2.getSize()).thenReturn(5)
        whenever(mockTable2.getStatus()).thenReturn(TableStatus.FREE)
        val mockTable3 = mock<Table>()
        whenever(mockTable3.getSize()).thenReturn(5)
        whenever(mockTable3.getStatus()).thenReturn(TableStatus.RESERVED)

        val foh = dummyFoh(listOf(mockTable1, mockTable2))
        val seatCount = foh.freeSeatCount()
        assertEquals(10, seatCount)
    }

    @Test
    fun `freeSeatCount test no free tables`() {
        val mockTable1 = mock<Table>()
        whenever(mockTable1.getSize()).thenReturn(5)
        whenever(mockTable1.getStatus()).thenReturn(TableStatus.OCCUPIED)
        val mockTable2 = mock<Table>()
        whenever(mockTable2.getSize()).thenReturn(5)
        whenever(mockTable2.getStatus()).thenReturn(TableStatus.OCCUPIED)
        val mockTable3 = mock<Table>()
        whenever(mockTable3.getSize()).thenReturn(5)
        whenever(mockTable3.getStatus()).thenReturn(TableStatus.RESERVED)

        val foh = dummyFoh(listOf(mockTable1, mockTable2))
        val seatCount = foh.freeSeatCount()
        assertEquals(0, seatCount)
    }

    @Test
    fun `availableSeats test`() {
        val table1 = Table(1, TableType.COMMON, 5)
        table1.setStatus(TableStatus.RESERVED)
        val table2 = Table(2, TableType.BAR, 4)
        val table3 = Table(3, TableType.BAR, 6)
        val table4 = Table(4, TableType.COMMON, 2)
        table4.setStatus(TableStatus.OCCUPIED)
        val table5 = Table(5, TableType.SEPARATED, 1)
        val table6 = Table(6, TableType.SEPARATED, 3)
        table6.setStatus(TableStatus.RESERVED)
        val table7 = Table(7, TableType.SEPARATED, 8)
        val tables = listOf(table1, table2, table3, table4, table5, table6, table7)

        val foh = dummyFoh(tables)
        val commonSeats = foh.availableSeats(TableType.COMMON)
        val barSeats = foh.availableSeats(TableType.BAR)
        val separatedSeats = foh.availableSeats(TableType.SEPARATED)

        assertEquals(0, commonSeats)
        assertEquals(10, barSeats)
        assertEquals(9, separatedSeats)
    }

    // TODO
    //  - demergeTables(group: CustomerGroup): integration test?
}
