package general.integration

import org.mockito.Mockito.mock
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Here we test how a few tick simulations pass in restaurant **/
class ExtendedRestaurantTest {
    val tables = dummyTables(
        mapOf(
            TableType.COMMON to listOf(4, 6, 3, 7),
            TableType.SEPARATED to listOf(4, 3, 7, 8, 10),
            TableType.BAR to listOf(3, 6, 5, 7)
        )
    )
    val staff = Staff(2, mapOf(CookType.SOUS to 1, CookType.SAUCE to 1), 1)
    val casual1 = CasualCustomerGroup(
        1,
        5,
        2,
        listOf(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        listOf(1),
        RatingLikelihood.ALWAYS
    )
    val casual2 = CasualCustomerGroup(
        2,
        7,
        1,
        listOf(),
        TableType.SEPARATED,
        listOf(RestaurantType.ASIAN),
        listOf(1),
        RatingLikelihood.SOME
    )
    val rice = Ingredient("rice", MeasuringUnit.G, 3)
    val chicken = Ingredient("chicken", MeasuringUnit.G, 2)
    val chickenRice = Recipe(
        2,
        "chicken rice",
        20,
        mutableMapOf(rice to 2, chicken to 1),
        listOf(CookType.SAUCE, CookType.SOUS)
    )
    val menu = Menu(mock(), listOf(chickenRice), RestaurantType.ASIAN)
    val foh = FrontOfHouse(tables, staff.getWaiters(), staff.getDrivers(), menu)
    val kitchen = Kitchen(staff.getCooks(), menu)
    val restaurant = Restaurant(
        1,
        "Software Restaurant",
        RestaurantType.ASIAN,
        1,
        7,
        true,
        staff,
        0,
        0,
        foh,
        kitchen,
        1,
        mutableListOf(casual1, casual2)
    )
    val pantry = menu.pantry

    fun dummyTables(sizesPerType: Map<TableType, List<Int>>): List<Table> {
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

    @Test
    fun testSeveralTicksOfRestaurant() {
        Ticks.init(10)
        Ticks.advanceEvening()
        // 11 times chicken rice available
        pantry.setSupplies(mutableMapOf(chicken to 12, rice to 23))
        Ticks.advanceTick()
        testTick1()
        Ticks.advanceTick()
        testTick2()
        Ticks.advanceTick()
        testTick3()
        Ticks.advanceTick()
        testTick4()
        Ticks.advanceTick()
        testTick5()
        checkStats()
    }

    fun testTick1() {
        restaurant.runServingPhaseTick()
        assertEquals(
            tables.filter { it.getId() == 7 },
            foh.seatedGroups[casual2],
            "group 2 wrong table"
        )
        assertEquals(
            1,
            foh.seatedGroups.values.filterNot { it == listOf<Table>() }.size,
            "too many groups seated"
        )
        assertEquals(
            7,
            getWaiterWithId1()?.getCurrentLoad() ?: 0,
            "wrong current load of a waiter"
        )
        assertTrue(staff.getWaiters().any { it.getId() == null }, "second waiter doesn't need id")
        assertEquals(7, casual2.order?.getDishes()?.size ?: 0, "wrong amount of dishes ordered")
        assertEquals(
            CookType.SAUCE,
            staff.getCooks().find { !it.isFree() }?.getType(),
            "wrong cook assigned"
        )
        assertEquals(
            7,
            staff.getCooks().find { it.getId() == 1 }?.getMealsNumber(),
            "wrong number of dishes assigned to cook"
        )
    }

    fun testTick2() {
        restaurant.runServingPhaseTick()
        // casual 1 arrives, order 1 is cooked and served, order 2 starts being cooked
        assertEquals(
            tables.filter { it.getId() == 2 },
            foh.seatedGroups[casual1],
            "group 1 wrong table"
        )
        assertEquals(
            11,
            getWaiterWithId1()?.getCurrentLoad() ?: 0,
            "wrong current load of a waiter"
        )
        assertEquals(4, casual1.order?.getDishes()?.size ?: 0, "can only order 4 (pantry)")
        assertEquals(1, casual1.peopleLeftFrustrated, "one person couldn't order")
        assertEquals(
            CookType.SAUCE,
            staff.getCooks().find { it.isFree() }?.getType(),
            "sauce finished cooking and is free"
        )
        assertEquals(
            CookType.SOUS,
            staff.getCooks().find { !it.isFree() }?.getType(),
            "sous is cooking order 2"
        )
        assertEquals(
            chickenRice,
            staff.getCooks().find { it.getId() == 2 }?.getCurrentRecipe(),
            "sous is cooking chicken rice"
        )
        assertEquals(
            0,
            staff.getCooks().find { it.getId() == 1 }?.getMealsNumber(),
            "cook 1 is not cooking anything anymore"
        )
        assertEquals(7, casual2.howManyEating(), "7 people eating group 2")
        assertEquals(7, casual2.howManySitting(), "7 people sitting group 2")
        assertEquals(0, casual1.howManyEating(), "no one is eating in group 1 yet")
        assertEquals(4, casual1.peopleWaiting, "4 people are waiting for food")
        assertFalse(
            getWaiterWithId1()?.canPerform(WaiterAction.SERVING, 4) ?: true,
            "waiter 1 can't serve 4 more"
        )
        assertTrue(
            getWaiterWithId1()?.canPerform(WaiterAction.SEAT, 5) ?: false,
            "waiter 1 has capability to seat 5"
        )
    }
    fun testTick3() {
        restaurant.runServingPhaseTick()
        // food for group 1 is ready
        assertEquals(
            4,
            getWaiterWithId1()?.getTickLoad(WaiterAction.SERVING),
            "wrong tick load for serving"
        )
        assertEquals(7, casual2.howManyEating(), "7 people eating group 2")
        assertEquals(4, casual1.howManyEating(), "4 people are eating in group 1")
        assertEquals(0, casual1.peopleWaiting, "no one is waiting anymore")
        assertEquals(2, getWaiterWithId1()?.getCustomerGroups()?.size, "waiter has wrong groups")
    }
    fun testTick4() {
        restaurant.runServingPhaseTick()
        // group 2 is escorted and they rate POSITIVE
        assertFalse(casual2.isSeated, "group 2 should be escorted")
        assertEquals(
            4,
            getWaiterWithId1()?.getCurrentLoad(),
            "one of the groups of the waiter should be released"
        )
        assertEquals(
            casual1,
            getWaiterWithId1()?.getCustomerGroups()?.first(),
            "one of the groups of the waiter should be released"
        )
        assertEquals(1, restaurant.positiveRatings, "positive rating group 2")
    }
    fun testTick5() {
        restaurant.runServingPhaseTick()
        // group 1 is escorted and they rate NEGATIVE
        assertFalse(casual1.isSeated, "group 1 should be escorted")
        assertEquals(
            0,
            getWaiterWithId1()?.getCurrentLoad(),
            "all waiter's groups left"
        )
        assertEquals(1, restaurant.negativeRatings, "negative rating group 1")
    }
    fun checkStats() {
        val stats = restaurant.statistics
        assertEquals(2, stats.getRatingsReceived(), "wrong rating stats")
        assertEquals(0, stats.getCustomersDelivered(), "wrong delivery stats")
        assertEquals(11, stats.getCustomersServed())
        assertEquals(11, stats.getMealsCooked())
    }

    fun getWaiterWithId1(): Waiter? {
        return staff.getWaiters().find { it.getId() == 1 }
    }
}
