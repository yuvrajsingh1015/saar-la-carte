package saarlacarte.delivery

import saarlacarte.enums.DishStatus
import saarlacarte.enums.DriverState
import saarlacarte.orders.Order

private const val CONST_SPEED = 5
private const val CASE_PREPARE = 1
private const val CASE_DELIVERING = 2
private const val CASE_RETURNING_FLAG = 3

/**
delivery driver implementation
 **/
class Driver {
    var id: Int? = null
    private var state: DriverState = DriverState.FREE
    private var ticksRemaining: Int = 0
    private var order: Order? = null

    /** Current driver state. */
    fun getState() = state

    /** Ticks left on the current trip. */
    fun getTicksRemaining() = ticksRemaining

    /**sets driver state to free in prep phase*/
    fun setFree() {
        state = DriverState.FREE
        order = null
        id = null
        ticksRemaining = 0
    }

    /** Order this driver is carrying, if any. */
    fun getOrder() = order

    /** set the order for the driver **/
    fun setOrder(newOrder: Order) {
        order = newOrder
    }

    /** Aborts a trip to the customer; RETURNING is left running. */
    fun abortOutbound(): Order? {
        if (state != DriverState.DELIVERING) {
            return null
        }
        val aborted = order
        state = DriverState.FREE
        ticksRemaining = 0
        order = null
        return aborted
    }

    /** Idle / aborted drivers are home; RETURNING keeps going. */
    fun goHome() {
        // used for logging
        if (state == DriverState.RETURNING) {
            return
        }
        state = DriverState.FREE
        ticksRemaining = 0
        order = null
        id = null
    }

    /**simulating the states of the driver**/
    fun simJourney(): Int {
        if (order == null) return 0
        val distance = order?.getDeliveryDistance() ?: 0
        when (state) {
            DriverState.FREE -> {
                val isFullyServed = order?.getDishes()?.all { it.getStatus() == DishStatus.SERVED } ?: false
                if (!isFullyServed) return 0 // not ready to drive yet
                ticksRemaining = (distance + CONST_SPEED - 1) / CONST_SPEED
                state = DriverState.DELIVERING
                return CASE_PREPARE // driver has all dishes and is ready to drive
            }

            DriverState.DELIVERING -> {
                ticksRemaining--
                // driver has reached their destination
                if (ticksRemaining == 0) {
                    state = DriverState.RETURNING
                    ticksRemaining = (distance + CONST_SPEED - 1) / CONST_SPEED
                }
                return CASE_DELIVERING
            }

            else -> {
                ticksRemaining--
                val arrivedHome = ticksRemaining == 0
                if (arrivedHome) {
                    state = DriverState.FREE
                    order = null
                }
                return if (arrivedHome) CASE_RETURNING_FLAG else 0
            }
        }
    }
}
