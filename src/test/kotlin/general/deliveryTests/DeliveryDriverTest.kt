package general.deliveryTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.delivery.Driver
import saarlacarte.enums.DriverState
import saarlacarte.orders.Order
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeliveryDriverTest {
    private lateinit var driver1: Driver
    private lateinit var driver2: Driver
    private lateinit var driver3: Driver

    private var driverList = mutableListOf<Driver>()

    @BeforeEach
    fun setup() {
        driver1 = Driver()
        driver2 = Driver()
        driver3 = Driver()
        driverList = mutableListOf(driver1, driver2, driver3)
    }

    @Test
    fun testInitialDriverListAndStates() {
        assertEquals(3, driverList.size)
        assertTrue(driverList.all { it.getState() == DriverState.FREE })
    }

    @Test
    fun testAssignDriverAnId() {
        val freeDriver = driverList.find { it.getState() == DriverState.FREE }

        assertNotNull(freeDriver)
        assertEquals(DriverState.FREE, freeDriver.getState())

        freeDriver.id = 1
        assertEquals(1, freeDriver.id)
    }

    @Test
    fun testDriverTravelTicks() {
        val mockOrder: Order = mock()
        whenever(mockOrder.getDeliveryDistance()).thenReturn(12)
        whenever(mockOrder.getId()).thenReturn(101)

        val driver = driver1
        driver.id = 1
        driver.setOrder(mockOrder)

        val caseResult = driver.simJourney()

        assertEquals(1, caseResult)
        assertEquals(DriverState.DELIVERING, driver.getState())
        assertEquals(3, driver.getTicksRemaining())
        verify(mockOrder).getDeliveryDistance()
    }

    @Test
    fun testAbortOutbound() {
        val mockOrder: Order = mock()
        whenever(mockOrder.getId()).thenReturn(101)

        val driver = driver2
        driver.id = 2
        driver.setOrder(mockOrder)

        driver.simJourney()
        assertEquals(DriverState.DELIVERING, driver.getState())

        val abortedOrder = driver.abortOutbound()

        assertNotNull(abortedOrder)
        assertEquals(101, abortedOrder.getId())
        assertEquals(DriverState.FREE, driver.getState())
        assertNull(driver.getOrder())
    }

    @Test
    fun resetDriversForNextEvening() {
        val mockOrder: Order = mock()
        whenever(mockOrder.getDeliveryDistance()).thenReturn(5)

        val driver = driver1
        driver.id = 1
        driver.setOrder(mockOrder)

        driverList.forEach { it.goHome() }
        assertEquals(DriverState.FREE, driver.getState())
        assertNull(driver1.getOrder())
        assertEquals(0, driver.getTicksRemaining())
    }

    @Test
    fun testSimJourneyWithoutOrder() {
        assertEquals(DriverState.FREE, driver1.getState())
        assertNull(driver1.getOrder())

        val result = driver1.simJourney()

        assertEquals(0, result)
        assertEquals(DriverState.FREE, driver1.getState())
    }

    @Test
    fun testSimJourneyOutboundDrivingInProgress() {
        val mockOrder: Order = mock()
        whenever(mockOrder.getDeliveryDistance()).thenReturn(12)

        val driver = driver1
        driver.id = 1
        driver.setOrder(mockOrder)

        // preparation -> delivering
        driver.simJourney()
        assertEquals(3, driver.getTicksRemaining())

        // outbound driving in progress
        val caseResult = driver.simJourney()

        assertEquals(2, caseResult)
        assertEquals(DriverState.DELIVERING, driver.getState())
        assertEquals(2, driver.getTicksRemaining())
    }

    @Test
    fun testSimJourneyArrivalAtCustomer() {
        val mockOrder: Order = mock()
        whenever(mockOrder.getDeliveryDistance()).thenReturn(8)

        val driver = driver1
        driver.id = 1
        driver.setOrder(mockOrder)

        driver.simJourney() // prep
        driver.simJourney() // delivering

        assertEquals(1, driver.getTicksRemaining())
        assertEquals(DriverState.DELIVERING, driver.getState())

        val caseResult = driver.simJourney() // transition to returning and arrange return tick

        assertEquals(2, caseResult)
        assertEquals(DriverState.RETURNING, driver.getState())
        assertEquals(2, driver.getTicksRemaining())
    }

    @Test
    fun testSimJourneyReturn() {
        val mockOrder: Order = mock()
        whenever(mockOrder.getDeliveryDistance()).thenReturn(8)

        val driver = driver1
        driver.id = 1
        driver.setOrder(mockOrder)

        driver.simJourney()
        driver.simJourney()
        driver.simJourney() // reached the customer. so should be returning after this

        assertEquals(DriverState.RETURNING, driver.getState())
        assertEquals(2, driver.getTicksRemaining())

        val caseResult = driver.simJourney()

        assertEquals(DriverState.RETURNING, driver.getState())
        assertEquals(1, driver.getTicksRemaining())
    }

    @Test
    fun testSimJourneyReturnedToRestaurant() {
        val mockOrder: Order = mock()
        whenever(mockOrder.getDeliveryDistance()).thenReturn(8)

        val driver = driver1
        driver.id = 1
        driver.setOrder(mockOrder)

        driver.simJourney() // prep, ticks = 2
        driver.simJourney() // driving outbound
        driver.simJourney() // arrive at customer
        driver.simJourney() // return drive

        assertEquals(DriverState.RETURNING, driver.getState())

        val returnArrivalResult = driver.simJourney()

        assertEquals(3, returnArrivalResult)
        assertEquals(DriverState.FREE, driver.getState())
        assertNull(driver.getOrder())
        assertEquals(0, driver.getTicksRemaining())
    }
}
