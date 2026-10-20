package saarlacarte.incidents

import saarlacarte.delivery.Driver
import saarlacarte.enums.CookType
import saarlacarte.enums.StaffType
import saarlacarte.restaurant.Cook
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Waiter

/**
Incident changing the staff of a restaurant
 **/
class StaffIncident(
    id: Int,
    evening: Int,
    private val number: Int,
    private val staffType: StaffType,
    private val staff: Staff,
    private val cookType: CookType? = null
) : Incident(id, evening) {
    /**
     Applies the staff change
     **/
    override fun execute() {
        if (number > 0) hire(number) else fire(-number)
    }

    private fun hire(number: Int) {
        when (staffType) {
            StaffType.WAITSTAFF -> {
                repeat(number) {
                    staff.getWaiters().add(Waiter())
                }
            }

            StaffType.DRIVER -> {
                repeat(number) {
                    staff.getDrivers().add(Driver())
                }
            }

            StaffType.COOK -> {
                checkNotNull(cookType) { "Cook type to hire is unknown" }
                repeat(number) {
                    staff.getCooks().add(Cook(cookType))
                }
            }
        }
    }

    private fun fire(number: Int) {
        when (staffType) {
            StaffType.WAITSTAFF -> {
                repeat(number) {
                    staff.getWaiters().removeFirstOrNull()
                }
            }

            StaffType.DRIVER -> {
                repeat(number) {
                    staff.getDrivers().removeFirstOrNull()
                }
            }

            StaffType.COOK -> {
                checkNotNull(cookType) { "Cook type to fire is unknown" }
                repeat(number) {
                    val indexRemove = staff.getCooks().indexOfFirst { it.getType() == cookType }
                    if (indexRemove != -1) {
                        staff.getCooks().removeAt(indexRemove)
                    }
                }
            }
        }
    }
}
