package saarlacarte.delivery

// import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.enums.DriverState
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.loggers.DriverLogger
import saarlacarte.orders.Order

private const val CONST_SPEED = 5
const val CASE_PREPARING = 1
const val CASE_DELIVERY = 2
const val CASE_ARRIVING = 3
const val CASE_ACCEPT = 4
const val CASE_REJECT = 5
const val CASE_RETURNING = 6

/** handles the delivery cases for a restaurant (one delivery service for each restaurant)*/
data class DeliveryService(
    val drivers: MutableList<Driver>,
) {
    private val customerGroups = mutableListOf<DeliveryCustomerGroup>()

    // driver loggers
    private var loggers: DriverLogger? = null
    private var deliveryCustomerLogger: DeliveryCustomerLogger? = null

    /** add a delivery customer group*/
    fun addGroup(group: DeliveryCustomerGroup) {
        customerGroups.add(group)
        deliveryCustomerLogger?.let { group.setLogger(it) }
    }

    /**removing all groups*/
    fun clearGroups() {
        customerGroups.clear()
    }

    /**processes the delivery phase in each tick */
    fun deliver(): Int {
        val logger = loggers ?: return 0
        val sortedDrivers = drivers.sortedBy { getCustomerGroupIdForDriver(it) }
        val caseDistinction = stepDriverJourney(sortedDrivers)

        for (i in CASE_PREPARING..CASE_REJECT) {
            val driverList = caseDistinction[i] ?: continue
            for (driver in driverList) {
                processDriverCase(i, driver, logger)
            }
        }

        customerGroups.sortedBy { it.id }.forEach { it.giveUpCaseCheck() }
        val returningList = caseDistinction[CASE_RETURNING] ?: return 0
        return returningDrivers(returningList)
    }

    private fun processDriverCase(caseType: Int, driver: Driver, logger: DriverLogger) {
        val id = driver.id ?: return
        val order = driver.getOrder() ?: return
        val orderId = order.getId()
        val customerGroup = customerGroups.find { it.order?.getId() == orderId }
        val groupId = getCustomerGroupIdForDriver(driver)

        when (caseType) {
            CASE_PREPARING -> {
                handlePreparationCase(id, orderId, customerGroup, driver)
            }

            CASE_DELIVERY -> {
                handleDeliveringCase(id, order, driver)
            }

            CASE_ARRIVING -> {
                logger.deliveryArrival(id, groupId, orderId)
            }

            CASE_ACCEPT -> {
                logger.deliveryFinished(id, groupId, orderId)
                customerGroup?.receiveOrder()
            }

            CASE_REJECT -> {
                logger.deliveryFailed(id, groupId, orderId)
            }
        }
    }

    private fun returningDrivers(driverList: MutableList<Driver>): Int {
        var returnedDrivers = 0
        for (driver in driverList) {
            val id = driver.id ?: continue
            returnedDrivers += handleReturningCase(id, driver)
        }
        return returnedDrivers
    }

    private fun stepDriverJourney(drivers: List<Driver>): MutableMap<Int, MutableList<Driver>> {
        val caseDistinction = listOf(
            CASE_PREPARING,
            CASE_DELIVERY,
            CASE_ARRIVING,
            CASE_ACCEPT,
            CASE_REJECT,
            CASE_RETURNING
        ).associateWith { mutableListOf<Driver>() }.toMutableMap()

        for (driver in drivers) {
            stepSingleDriver(driver, caseDistinction)
        }
        return caseDistinction
    }

    private fun stepSingleDriver(
        driver: Driver,
        caseDistinction: MutableMap<Int, MutableList<Driver>>,
    ) {
//        assignDriverIdIfNeeded(driver)
        val order = driver.getOrder() ?: return
        val orderId = order.getId()
        val customerGroup = customerGroups.find { it.order?.getId() == orderId }

        when (driver.simJourney()) {
            // driver got the complete order and start next tick
            CASE_PREPARING -> {
                caseDistinction[CASE_PREPARING]?.add(driver)
            }

            // driver on his way
            CASE_DELIVERY -> {
                caseDistinction[CASE_DELIVERY]?.add(driver)
                if (driver.getState() == DriverState.RETURNING) {
                    caseDistinction[CASE_ARRIVING]?.add(driver)
                    val outcome = if (customerGroup != null &&
                        !customerGroup.checkGaveUp()
                    ) {
                        CASE_ACCEPT
                    } else {
                        CASE_REJECT
                    }
                    caseDistinction[outcome]?.add(driver)
                }
            }
            // driver back to the restaurant
            CASE_ARRIVING -> {
                caseDistinction[CASE_RETURNING]?.add(driver)
            }
        }
    }

    private fun handlePreparationCase(
        driverId: Int,
        orderId: Int,
        customerGroup: DeliveryCustomerGroup?,
        driver: Driver,
    ) {
        val logger = loggers ?: return
        if (customerGroup == null) return
        logger.deliveryPreparation(driverId, orderId, customerGroup.id, driver.getTicksRemaining())
    }

    private fun handleDeliveringCase(
        driverId: Int,
        order: Order,
        driver: Driver,
    ) {
        val logger = loggers ?: return
        val distance = order.getDeliveryDistance() ?: 0
        val ticksDriven = (distance + CONST_SPEED - 1) / CONST_SPEED - driver.getTicksRemaining()
        val remainingTicks =
            if (ticksDriven == 0) {
                0 // the driver drove 0 ticks in the returning journey so no ticks remain to reach the destination
            } else {
                driver.getTicksRemaining() // driver still driving
            }
        val drivenDistance =
            if (ticksDriven == 0) {
                distance // driver has driven all the distance
            } else {
                ticksDriven * CONST_SPEED // driver still on his way
            }
        logger.deliveryDriving(driverId, drivenDistance, remainingTicks)
    }

    // driver on the returning journey log
    private fun handleReturningCase(driverId: Int, driver: Driver): Int {
        val logger = loggers ?: return 0
        // driver has returned
        if (driver.getState() == DriverState.FREE) {
            logger.driverReturned(driverId)
            return 1
        }
        // driver has not yet returned
        return 0
    }

    // getting the id of delivery customer group
    // to sort the drivers and to log
    private fun getCustomerGroupIdForDriver(driver: Driver): Int {
        val order = driver.getOrder() ?: return 0
        val orderId = order.getId()
        return customerGroups.find { it.order?.getId() == orderId }?.id ?: 0
    }

    /** assigns logger for delivery service */
    fun assignLogger(
        driverLog: DriverLogger,
        deliveryCustomerGroupLog: DeliveryCustomerLogger,
    ) {
        loggers = driverLog
        deliveryCustomerLogger = deliveryCustomerGroupLog
    }
}
