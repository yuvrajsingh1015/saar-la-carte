package general.deliveryTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.delivery.CASE_DELIVERY
import saarlacarte.delivery.CASE_PREPARING
import saarlacarte.delivery.DeliveryService
import saarlacarte.delivery.Driver
import saarlacarte.enums.DishStatus
import saarlacarte.enums.DriverState
import saarlacarte.food.Recipe
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.loggers.DriverLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks
import kotlin.test.assertEquals

class DeliveryServiceTest {
    private lateinit var deliveryService: DeliveryService
    private lateinit var driver: Driver

    private lateinit var driverLogger: DriverLogger
    private lateinit var deliveryCustomerLogger: DeliveryCustomerLogger

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()

        driverLogger = mock()
        deliveryCustomerLogger = mock()

        driver = Driver()
        driver.id = 1

        deliveryService = DeliveryService(mutableListOf(driver))
    }

    @Test
    fun `addGroup registers delivery group`() {
        deliveryService.assignLogger(driverLogger, deliveryCustomerLogger)

        val group = mock<DeliveryCustomerGroup>()
        deliveryService.addGroup(group)

        verify(group).setLogger(deliveryCustomerLogger)
    }

    @Test
    fun `clearGroups removes all registered groups`() {
        deliveryService.assignLogger(driverLogger, deliveryCustomerLogger)

        val group = mock<DeliveryCustomerGroup>()
        deliveryService.addGroup(group)
        deliveryService.clearGroups()

        val result = deliveryService.deliver()
        assertEquals(0, result)

        verify(group, never()).checkGaveUp()
    }

    @Test
    fun `deliver returns 0 if no loggers are assigned`() {
        val group = mock<DeliveryCustomerGroup>()
        deliveryService.addGroup(group)

        val count = deliveryService.deliver()
        assertEquals(0, count)

        verify(group, never()).giveUpCaseCheck()
    }

    @Test
    fun `delivery fails when group gives up waiting`() {
        deliveryService.assignLogger(driverLogger, deliveryCustomerLogger)

        val recipe = mock<Recipe>()
        val dish = Dish(recipe, DishStatus.SERVED)
        val order = Order(15, mutableListOf(dish), true, 5, 1)
        driver.setOrder(order)

        val group = mock<DeliveryCustomerGroup>()
        whenever(group.id).thenReturn(12)
        whenever(group.order).thenReturn(order)
        whenever(group.checkGaveUp()).thenReturn(true)

        deliveryService.addGroup(group)

        deliveryService.deliver()

        deliveryService.deliver()

        verify(driverLogger).deliveryArrival(1, 12, 15)
        verify(driverLogger).deliveryFailed(1, 12, 15)

        verify(group, never()).receiveOrder()
    }

    @Test
    fun `giveUpCaseCheck on multiple groups`() {
        deliveryService.assignLogger(driverLogger, deliveryCustomerLogger)

        val group1 = mock<DeliveryCustomerGroup>()
        whenever(group1.id).thenReturn(10)

        val group2 = mock<DeliveryCustomerGroup>()
        whenever(group2.id).thenReturn(5)

        deliveryService.addGroup(group1)
        deliveryService.addGroup(group2)

        deliveryService.deliver()

        verify(group1).giveUpCaseCheck()
        verify(group2).giveUpCaseCheck()
    }

    @Test
    fun `deliver handles multiple drivers`() {
        deliveryService.assignLogger(driverLogger, deliveryCustomerLogger)

        repeat(3) { Ticks.advanceTick() } // 1+3 = 4
        // currently tick 4

        // driver1: drives 10km
        val driver1 = Driver()
        driver1.id = 1
        val recipe1 = mock<Recipe>()
        val dish1 = Dish(recipe1, DishStatus.SERVED)
        val order1 = Order(15, mutableListOf(dish1), true, 10, 3)
        driver1.setOrder(order1)

        val group1 = mock<DeliveryCustomerGroup>()
        whenever(group1.id).thenReturn(10)
        whenever(group1.order).thenReturn(order1)
        whenever(group1.checkGaveUp()).thenReturn(false)

        val driver2 = Driver()
        driver2.id = 2
        val recipe2 = mock<Recipe>()
        val dish2 = Dish(recipe2, DishStatus.SERVED)
        val order2 = Order(16, mutableListOf(dish2), true, 5, 3)
        driver2.setOrder(order2)

        val group2 = mock<DeliveryCustomerGroup>()
        whenever(group2.id).thenReturn(20)
        whenever(group2.order).thenReturn(order2)
        whenever(group2.checkGaveUp()).thenReturn(false)

        val currentDeliveryService = DeliveryService(mutableListOf(driver1, driver2))
        currentDeliveryService.assignLogger(driverLogger, deliveryCustomerLogger)

        currentDeliveryService.addGroup(group1)
        currentDeliveryService.addGroup(group2)

        val returnTick1 = currentDeliveryService.deliver()
        assertEquals(0, returnTick1)
        assertEquals(DriverState.DELIVERING, driver1.getState())
        assertEquals(DriverState.DELIVERING, driver2.getState())

        verify(driverLogger).deliveryPreparation(1, 15, 10, 2)
        verify(driverLogger).deliveryPreparation(2, 16, 20, 1)

        // next tick
        // group 2 gets the order

        val returnedTick2 = currentDeliveryService.deliver()
        assertEquals(0, returnedTick2)
        assertEquals(DriverState.DELIVERING, driver1.getState())
        assertEquals(DriverState.RETURNING, driver2.getState())

        verify(driverLogger).deliveryDriving(1, 5, 1)
        verify(driverLogger).deliveryArrival(2, 20, 16)
        verify(driverLogger).deliveryFinished(2, 20, 16)
        verify(group2).receiveOrder()

        // driver2 returns to free
        // driver 1 arrives at customers place

        val returnedTick3 = currentDeliveryService.deliver()
        assertEquals(1, returnedTick3) // Driver 2 returned home
        assertEquals(DriverState.RETURNING, driver1.getState())
        assertEquals(DriverState.FREE, driver2.getState())

        verify(driverLogger).deliveryArrival(1, 10, 15)
        verify(driverLogger).deliveryFinished(1, 10, 15)
        verify(group1).receiveOrder()
        verify(driverLogger).driverReturned(2)

        // driver 1 has to travel 2 ticks so not at restaurant yet
        val returnedTick4 = currentDeliveryService.deliver()
        assertEquals(0, returnedTick4)

        // by now, he is there at restaurant
        val returnedTick5 = currentDeliveryService.deliver()
        assertEquals(1, returnedTick5)
        assertEquals(DriverState.FREE, driver1.getState())
        verify(driverLogger).driverReturned(1)
    }

    /** testing order of logging drivers **/
    @Test
    fun `test correct order of logging drivers`() {
        deliveryService.assignLogger(driverLogger, deliveryCustomerLogger)

        // Driver 1: Group 30, Preparing
        val driver1 = mock<Driver>()
        val order1 = Order(40, mutableListOf(), true, 5, 1)
        whenever(driver1.id).thenReturn(2)
        whenever(driver1.getOrder()).thenReturn(order1)
        whenever(driver1.simJourney()).thenReturn(CASE_PREPARING)
        whenever(driver1.getTicksRemaining()).thenReturn(2)

        val group1 = mock<DeliveryCustomerGroup>()
        whenever(group1.id).thenReturn(30)
        whenever(group1.order).thenReturn(order1)

        // Driver 2: Group 10, Preparing
        val driver2 = mock<Driver>()
        val order2 = Order(33, mutableListOf(), true, 5, 1)
        whenever(driver2.id).thenReturn(3)
        whenever(driver2.getOrder()).thenReturn(order2)
        whenever(driver2.simJourney()).thenReturn(CASE_PREPARING)
        whenever(driver2.getTicksRemaining()).thenReturn(2)

        val group2 = mock<DeliveryCustomerGroup>()
        whenever(group2.id).thenReturn(10)
        whenever(group2.order).thenReturn(order2)

        // Driver 3: Group 20, Arriving / Accepting
        val driver3 = mock<Driver>()
        val order3 = Order(21, mutableListOf(), true, 5, 1)
        whenever(driver3.id).thenReturn(1)
        whenever(driver3.getOrder()).thenReturn(order3)
        whenever(driver3.simJourney()).thenReturn(CASE_DELIVERY)
        whenever(driver3.getState()).thenReturn(DriverState.RETURNING)

        val group3 = mock<DeliveryCustomerGroup>()
        whenever(group3.id).thenReturn(20)
        whenever(group3.order).thenReturn(order3)
        whenever(group3.checkGaveUp()).thenReturn(false)

        val multiDeliveryService = DeliveryService(mutableListOf(driver1, driver2, driver3))
        multiDeliveryService.assignLogger(driverLogger, deliveryCustomerLogger)
        multiDeliveryService.addGroup(group1)
        multiDeliveryService.addGroup(group2)
        multiDeliveryService.addGroup(group3)

        multiDeliveryService.deliver()

        val inOrder = org.mockito.Mockito.inOrder(driverLogger)

        // State 1: CASE_PREPARING. Should be sorted by Group ID (Group 10 then Group 30)
        inOrder.verify(driverLogger).deliveryPreparation(3, 33, 10, 2)
        inOrder.verify(driverLogger).deliveryPreparation(2, 40, 30, 2)

        // State 2: CASE_DELIVERY / ARRIVING / ACCEPT. (Group 20)
        inOrder.verify(driverLogger).deliveryDriving(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any()
        )
        inOrder.verify(driverLogger).deliveryArrival(1, 20, 21)
        inOrder.verify(driverLogger).deliveryFinished(1, 20, 21)
    }

    @Test
    fun `test correct rounding of distance`() {
        deliveryService.assignLogger(driverLogger, deliveryCustomerLogger)
        // Total ticks should be ceil(12/5) = 3 ticks.
        val driver = mock<Driver>()
        val order = Order(55, mutableListOf(), true, 12, 1)

        whenever(driver.id).thenReturn(1)
        whenever(driver.getOrder()).thenReturn(order)
        whenever(driver.simJourney()).thenReturn(CASE_DELIVERY)
        whenever(driver.getState()).thenReturn(DriverState.DELIVERING)

        // Simulating the 2nd tick of driving (1 tick remaining of the 3 total)
        whenever(driver.getTicksRemaining()).thenReturn(1)

        val group = mock<DeliveryCustomerGroup>()
        whenever(group.id).thenReturn(5)
        whenever(group.order).thenReturn(order)

        val service = DeliveryService(mutableListOf(driver))
        service.assignLogger(driverLogger, deliveryCustomerLogger)
        service.addGroup(group)
        service.deliver()
        verify(driverLogger).deliveryDriving(1, 10, 1)
    }
}
