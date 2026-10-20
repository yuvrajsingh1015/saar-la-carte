package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableStatus
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.FOHPreparationLogger
import saarlacarte.loggers.FOHSeatingLogger
import saarlacarte.loggers.FOHServingLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.FOHLogger
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.InHouseServing
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

// P03: FOH - Event other actions - waitstaff selection/performance for TAKING_ORDER,
// SERVING and ESCORT on event groups (spec p.14-16, 22)
class EventOtherActionsTest {

    private lateinit var potato: Ingredient

    @BeforeEach
    fun setUp() {
        potato = Ingredient("Potato", MeasuringUnit.G, 5)
        Ticks.init(70)
        Ticks.advanceEvening()
        Ticks.advanceTick()
    }

    private fun eventGroup(id: Int, size: Int) = EventCustomerGroup(
        id,
        size,
        1,
        emptyList(),
        TableType.COMMON,
        emptyList(),
        4,
        mutableMapOf()
    )

    @Test
    fun servingCompletesImmediatelyWhenAllDishesCookedAndCapacitySuffices() {
        val group = eventGroup(1, 2)
        val dish1 = Dish(recipe(1, "Sushi"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val dish2 = Dish(recipe(2, "Pizza"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), mutableListOf(dish1, dish2), false, null, Ticks.getTick())
        group.order = order

        val waiter = Waiter()
        waiter.addCustomerGroup(group)
        val table = Table(1, TableType.COMMON, 4)
        val seatedGroups = mutableMapOf<CustomerGroup, List<Table>>(group to listOf(table))
        val service = InHouseServing(listOf(waiter), seatedGroups)

        val served = service.serveInOrder(listOf(order), null)
        assertEquals(2, served, "served")
        assertEquals(DishStatus.SERVED, dish1.getStatus(), "dish1")
        assertEquals(DishStatus.SERVED, dish2.getStatus(), "dish2")
    }

    @Test
    fun servingPrioritizesBasicDishOverLowerRecipeIdWhenCapacityIsConstrained() {
        val group = eventGroup(1, 2)
        // lower recipe id but not basic
        val plain = Dish(recipe(1, "Sushi"), DishStatus.FINISHED, false)
        // higher recipe id but basic - must still be served first
        val basic = Dish(recipe(5, "BasicRoll", RestaurantType.ASIAN), DishStatus.FINISHED, true)
        plain.setFinishedTick(Ticks.getTick())
        basic.setFinishedTick(Ticks.getTick())
        val order = Order(Order.nextId(), mutableListOf(plain, basic), false, null, Ticks.getTick())
        group.order = order

        Ticks.advanceTick()
        Ticks.advanceTick() // waited 2 ticks -> serve meal by meal even though not all done

        val waiter = Waiter()
        waiter.addCustomerGroup(group)
        waiter.performAction(WaiterAction.SERVING, 9) // only 1 slot of capacity left this tick
        val table = Table(1, TableType.COMMON, 4)
        val seatedGroups = mutableMapOf<CustomerGroup, List<Table>>(group to listOf(table))
        val service = InHouseServing(listOf(waiter), seatedGroups)

        val served = service.serveInOrder(listOf(order), null)
        assertEquals(1, served)
        assertEquals(DishStatus.SERVED, basic.getStatus())
        assertEquals(DishStatus.FINISHED, plain.getStatus())
    }

    @Test
    fun shortCapacityOnTheFinishTickWaitsAndNamesTheWaiterWhoCanServe() {
        val group = eventGroup(1, 4)
        val dishes = (1..4).map { Dish(recipe(it, "Dish$it"), DishStatus.FINISHED) }
        dishes.forEach { it.setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), dishes.toMutableList(), false, null, Ticks.getTick())
        group.order = order

        val waiter1 = waiterWithId(1)
        waiter1.performAction(WaiterAction.SERVING, 10)
        val waiter2 = waiterWithId(2)
        waiter2.performAction(WaiterAction.SERVING, 9)
        val waiter3 = waiterWithId(3)
        waiter3.performAction(WaiterAction.SERVING, 9)
        val table = Table(4, TableType.COMMON, 4)
        val seatedGroups = mutableMapOf<CustomerGroup, List<Table>>(group to listOf(table))
        val service = InHouseServing(listOf(waiter1, waiter2, waiter3), seatedGroups)
        val serving = mock<FOHServingLogger>()

        val served = service.serveInOrder(listOf(order), fohLogger(serving))

        assertEquals(0, served)
        assertEquals(4, dishes.count { it.getStatus() == DishStatus.FINISHED })
        verify(serving).notServing(2, 4, 4)
        verifyNoMoreInteractions(serving)
    }

    @Test
    fun shortCapacityNamesTheFirstWaiterWhenNobodyCanServe() {
        val group = eventGroup(1, 4)
        val dishes = (1..4).map { Dish(recipe(it, "Dish$it"), DishStatus.FINISHED) }
        dishes.forEach { it.setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), dishes.toMutableList(), false, null, Ticks.getTick())
        group.order = order

        val waiter1 = waiterWithId(1)
        val waiter2 = waiterWithId(2)
        waiter1.performAction(WaiterAction.SERVING, 10)
        waiter2.performAction(WaiterAction.SERVING, 10)
        val table = Table(4, TableType.COMMON, 4)
        val seatedGroups = mutableMapOf<CustomerGroup, List<Table>>(group to listOf(table))
        val service = InHouseServing(listOf(waiter1, waiter2), seatedGroups)
        val serving = mock<FOHServingLogger>()

        val served = service.serveInOrder(listOf(order), fohLogger(serving))

        assertEquals(0, served)
        verify(serving).notServing(1, 4, 4)
    }

    @Test
    fun orderThatBecomesCompleteThisTickStillWaitsWhenCapacityIsShort() {
        val group = eventGroup(1, 4)
        val earlier = (1..2).map { Dish(recipe(it, "Dish$it"), DishStatus.FINISHED) }
        earlier.forEach { it.setFinishedTick(Ticks.getTick()) }
        Ticks.advanceTick()
        val justDone = (3..4).map { Dish(recipe(it, "Dish$it"), DishStatus.FINISHED) }
        justDone.forEach { it.setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), (earlier + justDone).toMutableList(), false, null, Ticks.getTick())
        group.order = order

        val waiter = waiterWithId(1)
        waiter.performAction(WaiterAction.SERVING, 8)
        val table = Table(4, TableType.COMMON, 4)
        val seatedGroups = mutableMapOf<CustomerGroup, List<Table>>(group to listOf(table))
        val service = InHouseServing(listOf(waiter), seatedGroups)
        val serving = mock<FOHServingLogger>()

        val served = service.serveInOrder(listOf(order), fohLogger(serving))

        assertEquals(0, served)
        verify(serving).notServing(1, 4, 4)
        verifyNoMoreInteractions(serving)
    }

    @Test
    fun followingTickServesWhatFitsAndNamesTheLastWaiter() {
        val group = eventGroup(1, 4)
        val dishes = (1..4).map { Dish(recipe(it, "Dish$it"), DishStatus.FINISHED) }
        dishes.forEach { it.setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), dishes.toMutableList(), false, null, Ticks.getTick())
        group.order = order
        Ticks.advanceTick()

        val waiter1 = waiterWithId(1)
        waiter1.performAction(WaiterAction.SERVING, 9)
        val waiter2 = waiterWithId(2)
        waiter2.performAction(WaiterAction.SERVING, 9)
        val waiter3 = waiterWithId(3)
        waiter3.performAction(WaiterAction.SERVING, 9)
        val table = Table(4, TableType.COMMON, 4)
        val seatedGroups = mutableMapOf<CustomerGroup, List<Table>>(group to listOf(table))
        val service = InHouseServing(listOf(waiter1, waiter2, waiter3), seatedGroups)
        val serving = mock<FOHServingLogger>()

        val served = service.serveInOrder(listOf(order), fohLogger(serving))

        assertEquals(3, served)
        assertEquals(DishStatus.FINISHED, dishes[3].getStatus())
        verify(serving).notServing(3, 1, 4)
    }

    @Test
    fun servingAnEventTableAcrossMultipleWaitersExhaustsBasicDishesFirst() {
        val group = eventGroup(1, 16)
        val basicDishes = (1..14).map {
            Dish(recipe(1, "BasicDish", RestaurantType.ASIAN), DishStatus.FINISHED, true).apply {
                setFinishedTick(Ticks.getTick())
            }
        }
        val plainDishes = (1..2).map {
            Dish(recipe(2, "PlainDish"), DishStatus.FINISHED, false).apply {
                setFinishedTick(Ticks.getTick())
            }
        }
        val order = Order(Order.nextId(), (basicDishes + plainDishes).toMutableList(), false, null, Ticks.getTick())
        group.order = order

        val waiter1 = waiterWithId(1)
        val waiter2 = waiterWithId(2)
        waiter1.addCustomerGroup(group)
        waiter2.addCustomerGroup(group)
        val table = Table(1, TableType.COMMON, 16)
        val seatedGroups = mutableMapOf<CustomerGroup, List<Table>>(group to listOf(table))
        val service = InHouseServing(listOf(waiter1, waiter2), seatedGroups)
        val serving = mock<FOHServingLogger>()

        val served = service.serveInOrder(listOf(order), fohLogger(serving))

        assertEquals(16, served)
        // assertEquals(2, waitstaff)
        // spec p.13-14: splitting a table across several waiters still "prioritizes meals
        // of basic dishes" first - the first waiter must exhaust all 14 basic dishes it can
        // fit before either plain dish is touched, even though only 10 fit in one waiter
        verify(serving).waitstaffServes(1, mapOf("BasicDish" to 10), 1, 0)
        verify(serving).waitstaffServes(2, mapOf("BasicDish" to 4, "PlainDish" to 2), 1, 0)
    }

    @Test
    fun eventGroupLeavesAndTableStaysReservedAtOpeningEnd() {
        // spec p.24: at the end of the opening time, remaining customers are escorted outside;
        // for REGULAR/EVENT groups the table stays reserved rather than becoming free
        val group = eventGroup(1, 2)
        group.isSeated = true

        val waiter = Waiter()
        waiter.addCustomerGroup(group)
        val table = Table(1, TableType.COMMON, 4)
        table.setStatus(TableStatus.RESERVED)

        val foh = FrontOfHouse(listOf(table), mutableListOf(waiter), null, menuWithStock())
        foh.seatedGroups[group] = listOf(table)

        foh.escortRemainingWithoutWaiters()

        assertFalse(group.isSeated)
        assertEquals(TableStatus.RESERVED, table.getStatus())
    }

    private fun waiterWithId(id: Int): Waiter {
        val waiter = Waiter()
        waiter.assignId(id)
        return waiter
    }

    private fun fohLogger(serving: FOHServingLogger): FOHLogger {
        return FOHLogger(mock<FOHPreparationLogger>(), mock<FOHSeatingLogger>(), serving)
    }

    private fun recipe(id: Int, name: String, basicFor: RestaurantType? = null) =
        Recipe(id, name, 10, mutableMapOf(potato to 1), listOf(CookType.EXEC), basicFor)

    private fun menuWithStock(): Menu {
        val supplier = Supplier(listOf(potato), mutableMapOf("Potato" to 1000))
        val menu = Menu(supplier, listOf(recipe(1, "Sushi")), RestaurantType.ASIAN)
        menu.pantry.setSupplies(mutableMapOf(potato to 1000))
        return menu
    }
}
