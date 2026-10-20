package saarlacarte.restaurant

import saarlacarte.delivery.Driver
import saarlacarte.enums.CookType

/**
The cooking and waitstaff of a restaurant
 **/
class Staff(waiterCount: Int, cookCounts: Map<CookType, Int>, driversCount: Int) {

    private val waiters: MutableList<Waiter> = mutableListOf()
    private val cooks: MutableList<Cook> = mutableListOf()
    private val drivers: MutableList<Driver> = mutableListOf()

    init {
        var waitersAdded = 0
        while (waitersAdded < waiterCount) {
            waiters.add(Waiter())
            waitersAdded++
        }
        var driversAdded = 0
        while (driversAdded < driversCount) {
            drivers.add(Driver())
            driversAdded++
        }
        for ((type, count) in cookCounts) {
            var cooksAdded = 0
            while (cooksAdded < count) {
                cooks.add(Cook(type))
                cooksAdded++
            }
        }
    }

    /** waiters **/
    fun getWaiters(): MutableList<Waiter> = waiters

    /** cooks **/
    fun getCooks(): MutableList<Cook> = cooks

    /** drivers **/
    fun getDrivers(): MutableList<Driver> = drivers
}
