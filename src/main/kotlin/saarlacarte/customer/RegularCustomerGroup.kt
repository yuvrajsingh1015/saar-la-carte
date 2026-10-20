package saarlacarte.customer

import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.TableType
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks

private const val LAST3_SIZE = 3

/**
 * regular customer group
 */
class RegularCustomerGroup(
    id: Int,
    size: Int,
    visitingTick: Int,
    customerPreferences: List<FoodPreferences>,
    preferredTableType: TableType,
    private val visitingStart: Int,
    private val visitingPeriod: Int,
    private val restaurant: Int
) : CustomerGroup(id, size, visitingTick, customerPreferences, preferredTableType) {
    // if last Experience is true, then the regular customer had a bad experience last time
    // if last Experience is true and in the current evening, negative stuff happens,
    // the regular doesn't ever visit the restaurant again
    var lastExperience: Boolean = false
    private val mutableLast3: MutableList<Order> = mutableListOf()
    val last3: List<Order> get() = mutableLast3
    var quitVisiting: Boolean = false

    // isVisiting is run only at the start of each evening so cannot decide only based on lastExperience
    // added a new quitRestaurant attribute
    override fun isVisiting(): Boolean {
        if (quitVisiting) return false
        return Ticks.getEvening() >= visitingStart && (Ticks.getEvening() - visitingStart).mod(visitingPeriod) == 0
    }

    /**is a regular customer group*/
    override fun isRegular(): Boolean {
        return true
    }

    /**
     * if positive or neutral experience, return 1
     * else return -1
     */
    override fun giveRating(): Int {
        return when (customerExperience) {
            CustomerExperience.POSITIVE, CustomerExperience.NEUTRAL -> {
                lastExperience = false
                1
            }

            CustomerExperience.NEGATIVE -> {
                if (lastExperience) {
                    // regular has had second bad experience
                    quitVisiting = true
                } else {
                    // regular has had first bad experience
                    lastExperience = true
                }
                -1
            }
        }
    }

    /** Keeps the 3 most recent orders, used to restock ahead of this regular's next visit (F13). */
    override fun onOrderPlaced(order: Order) {
        mutableLast3.add(order)
        if (mutableLast3.size > LAST3_SIZE) {
            mutableLast3.removeAt(0)
        }
    }

    // getters
    /**
     * visiting start
     */
    fun getVisitingStart(): Int {
        return visitingStart
    }

    /**
     * visiting period
     */
    fun getVisitingPeriod(): Int {
        return visitingPeriod
    }

    /**
     * unlike what the name actually states, it is restaurant id
     * bad naming ig
     */
    fun getRestaurant(): Int {
        return restaurant
    }

    override fun isArriving(): Boolean {
        if (quitVisiting) return false
        return super.isArriving()
    }
}
