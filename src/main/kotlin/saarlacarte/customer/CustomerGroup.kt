package saarlacarte.customer

import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.DishStatus
import saarlacarte.enums.TableType
import saarlacarte.food.Recipe
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks

private const val EXPECT_FOOD_TICKS = 4

/** Spec p.22: after the first meal, unserved guests wait this many more further ticks. */
private const val PARTIAL_GRACE_TICKS = 2

/**
 * Abstract base for all customer group types (REGULAR, EVENT, CASUAL).
 */
abstract class CustomerGroup(
    val id: Int,
    val size: Int,
    val visitingTick: Int,
    val customerPreferences: List<FoodPreferences>,
    val preferredTableType: TableType?
) {
    var peopleSelectingDish = 0
    var isSeated: Boolean = false

    // key 3: customers who were just served this tick, not yet processed by eat() call
    // key 2: customers who have completed 1 shift, 1 more eat() call until they are finished eating
    // key 1: customers who will move on to key 0 this tick
    // key 0: total of customers who are done eating
    protected val eatingTicksLeft: MutableMap<Int, Int> = mutableMapOf(3 to 0, 2 to 0, 1 to 0, 0 to 0)

    // group's current order for this visit or null before ordering
    // assigning a new order triggers onOrderPlaced for other classes that need to react
    var order: Order? = null
        set(value) {
            if (field === value) return
            field = value
            if (value != null) onOrderPlaced(value)
        }
    protected var customerExperience: CustomerExperience = CustomerExperience.NEUTRAL
    var peopleLeftFrustrated: Int = 0
    var peopleWaiting: Int = size // how many are waiting for their food
    protected var waitingTicksLeft: Int = 2
    var peopleEscorted: Int = 0
    var peopleFinishedEating: Int = 0 // in total including escorted
    var hasLeftVisit: Boolean = false

    /** Bumped in [resetVisit] so restaurant stats can count a later visit. */
    var visitGeneration: Int = 0
        protected set

    /** Hook for subclasses that need to react to a newly placed order (F13). */
    protected open fun onOrderPlaced(order: Order) {}

    /**
     * Advances the group's eating countdown by one tick
     * returns <how many are currently eating, how many finished eating>
     */
    open fun eat(): Pair<Int, Int> {
        val newlyFinished = eatingTicksLeft[1] ?: 0
        // (2,x) -> (1, x)
        eatingTicksLeft[1] = eatingTicksLeft[2] ?: 0 //  2-> 1
        eatingTicksLeft[2] = eatingTicksLeft[3] ?: 0 // 3 -> 2
        eatingTicksLeft[3] = 0 // 3 cleared (receiveOrder refills it)
        eatingTicksLeft[0] = (eatingTicksLeft[0] ?: 0) + newlyFinished
        peopleFinishedEating = peopleFinishedEating + newlyFinished
        val stillEating = howManyEating()
        return Pair(stillEating, peopleFinishedEating)
    }

    /** Spec p.24: groups that did not finish eating see the restaurant closing as a negative experience. */
    fun applyEndOfOpeningExperience() {
        if (peopleFinishedEating < size - peopleLeftFrustrated) {
            customerExperience = CustomerExperience.NEGATIVE
        }
    }

    /** Next evening starts from a clean experience; last visit already rated. */
    fun clearVisitExperience() {
        customerExperience = CustomerExperience.NEUTRAL
    }

    /**
     * Clears this visit's seating/order/eating/waiting counters
     * increments visitGeneration so restaurants can distinguish this visit from next one
     **/
    open fun resetVisit() {
        isSeated = false
        eatingTicksLeft[3] = 0
        eatingTicksLeft[2] = 0
        eatingTicksLeft[1] = 0
        eatingTicksLeft[0] = 0
        peopleSelectingDish = 0
        order = null
        peopleLeftFrustrated = 0
        peopleWaiting = size
        peopleEscorted = 0
        peopleFinishedEating = 0
        waitingTicksLeft = 2
        visitGeneration++
        hasLeftVisit = false
    }

    /**
     * Ends the group's current visit
     * marks hasLeftVisit so the simulation knows this group has departed for this evening
     */
    open fun leave() {
        resetVisit()
        hasLeftVisit = true
    }

    /** Seating-fail or no-order is a negative visit. */
    fun abandonVisit() {
        customerExperience = CustomerExperience.NEGATIVE
        leave()
    }

    /** Partial no-order still makes the whole group's later rating negative. */
    fun markNegativeExperience() {
        customerExperience = CustomerExperience.NEGATIVE
    }

    /**
     * isVisiting is true for regular customer if the visiting start is the same as the
     * visitingStart + visitingEvening is the current evening
     * true for casual customers if current evening in their list visitingEvenings
     * true for event customers if current evening equal to event Evening
     */
    abstract fun isVisiting(): Boolean

    /**
     * Whether the group is arriving at the restaurant in the current tick.
     */
    open fun isArriving(): Boolean {
        return visitingTick == Ticks.getTick()
    }

    /**
     * Orders food-preference subgroups by descending number of excluded ingredients,
     * then ascending number of favorite dishes, so that the pickiest customers within
     * a group order first
     */
    private val preferenceComparator = compareByDescending<FoodPreferences> {
        it.excludedIngredients?.size ?: 0
    }.thenBy {
        it.favoriteDishes?.size ?: 0
    }

    /**
     *  Selects a dish for the next customer in this group who has not yet ordered.
     * the customers with the most excluded ingredients orders first and then the ones with fewest
     * favorite dishes and in a tie, food preference object listed first has precedence
     * Customers without any matching food preference subgroup order without preferences.
     */
    open fun selectDish(availableDishes: List<Recipe>): Recipe? {
        val subgroupWithPreferences = customerPreferences.sumOf { it.size }
        if (customerPreferences.isEmpty() || peopleSelectingDish >= subgroupWithPreferences) {
            peopleSelectingDish++
            return selectDishForEach(availableDishes, null)
        }
        val sortedCustomerPreferences = customerPreferences.sortedWith(preferenceComparator)
        var accumulatedCount = 0
        var matchedPreference: FoodPreferences? = null
        for (pref in sortedCustomerPreferences) {
            accumulatedCount += pref.size
            if (peopleSelectingDish < accumulatedCount) {
                matchedPreference = pref
                break
            }
        }
        peopleSelectingDish++
        return selectDishForEach(availableDishes, matchedPreference)
    }

    /**
     * called by the selectDish
     * this separation helps with detekt
     * A customer selects dish
     * 1. without any excluded ingredient
     * 2. first matching favorite dishes
     * 3. dish with most preferred ingredients
     * in a tie, highest recipe id
     */
    open fun selectDishForEach(recipes: List<Recipe>, preferences: FoodPreferences?): Recipe? {
        if (preferences != null) {
            val excluded = preferences.excludedIngredients ?: emptyList()
            val preferred = preferences.preferredIngredients ?: emptyList()
            val favoriteDishes = preferences.favoriteDishes ?: emptyList()

            val goodDishes = mutableListOf<Recipe>()

            for (recipe in recipes) {
                val usedIngredients = recipe.ingredients.keys.map { it.getName() }
                if (excluded.intersect(usedIngredients).isEmpty()) {
                    goodDishes.add(recipe)
                }
            }

            if (goodDishes.isEmpty()) return null

            // choose their first matching dish
            for (fav in favoriteDishes) {
                val match = goodDishes.find { it.dishName == fav }
                if (match != null) return match
            }
            return preferredTie(goodDishes, preferred)
        } else {
            return recipes.maxByOrNull { recipe -> recipe.id }
        }
    }

    /**
     * for the customers who are willing to eat but has no favorite dish
     */
    private fun preferredTie(goodDishes: MutableList<Recipe>, preferred: List<String>): Recipe? {
        // choose the dish that contains most preferred
        if (preferred.isNotEmpty()) {
            var maximumMatch = 0
            val bestCandidateDish = mutableListOf<Recipe>()

            for (recipe in goodDishes) {
                // how many ingredients are in this recipe that are also in preferred
                val currentMatchCount = recipe.ingredients.keys.count { ingredient ->
                    ingredient.getName() in preferred
                }

                if (currentMatchCount > maximumMatch) {
                    maximumMatch = currentMatchCount
                    bestCandidateDish.clear()
                    bestCandidateDish.add(recipe)
                } else if (currentMatchCount == maximumMatch && currentMatchCount > 0) {
                    bestCandidateDish.add(recipe)
                }
            }

            val candidates = if (bestCandidateDish.isNotEmpty()) bestCandidateDish else goodDishes
            return candidates.maxByOrNull { recipe -> recipe.id }
        } else {
            return goodDishes.maxByOrNull { recipe -> recipe.id }
        }
    }

    /**
     * what do the delivery customers order
     * (was kept here in case the delivery customer group wanted to use it)
     */
    open fun selectDeliveryDishes(available: List<Recipe>): Map<Recipe, Int> = emptyMap()

    /**
     * this gets triggered by serve action from the waitstaff or
     * for deliveries when the driver brings the food
     * in both, the dish status will be SERVED
     */
    open fun receiveOrder() {
        val totalServedDishes = order?.getDishes()?.count { it.getStatus() == DishStatus.SERVED } ?: 0
        val alreadyServed = size - peopleLeftFrustrated - peopleWaiting
        val newlyServed = totalServedDishes - alreadyServed
        if (newlyServed > 0) {
            peopleWaiting -= newlyServed
            eatingTicksLeft[3] = (eatingTicksLeft[3] ?: 0) + newlyServed
            if (!isDelivery() && peopleWaiting > 0 && alreadyServed == 0) {
                // One-time: the rest of the table waits 2 ticks from this serve, not from the order.
                waitingTicksLeft += PARTIAL_GRACE_TICKS
            } else if (!isDelivery()) {
                applyOnTimeExperience()
            }
        }
    }

    /** Spec p.25: all food before the 4-tick window is positive; on the deadline neutral; later negative. */
    private fun applyOnTimeExperience() {
        if (customerExperience == CustomerExperience.NEGATIVE) {
            return
        }
        val orderedTick = order?.getOrderedTick() ?: return
        val delay = Ticks.getTick() - orderedTick
        customerExperience = when {
            delay < EXPECT_FOOD_TICKS -> CustomerExperience.POSITIVE
            delay == EXPECT_FOOD_TICKS -> CustomerExperience.NEUTRAL
            else -> CustomerExperience.NEGATIVE
        }
    }

    /**
     * computes how many of the group's members are still eating
     */
    open fun howManyEating(): Int {
        return (eatingTicksLeft[3] ?: 0) + (eatingTicksLeft[2] ?: 0) + (eatingTicksLeft[1] ?: 0)
    }

    /**
     * how many people do we have on the table currently to escort
     */
    open fun howManySitting(): Int {
        if (isSeated) return size - peopleLeftFrustrated - peopleEscorted else return 0
    }

    /**
     * Determines the rating this group gives the restaurant for the current visit,
     * different customer groups give rating in different ways!!
     * 1: positive rating, 0: no rating, -1: negative rating
     */
    abstract fun giveRating(): Int

    /**
     * true only for the delivery customer group
     */
    open fun isDelivery(): Boolean {
        return false
    }

    /**
     * true only for the event group
     */
    open fun isEvent(): Boolean {
        return false
    }

    /**true only for regular customers*/
    open fun isRegular(): Boolean {
        return false
    }

    // master the art of giving up
    /**
     * Nobody served: the group waits 4 ticks from the order, then leaves.
     * Someone served: The 2 ticks are added to the remaining original waiting time
     * A customer who could not order has already left.
     */
    open fun waitAndGiveUp(): Int {
        waitingTicksLeft--
        val orderTick = order?.getOrderedTick()
        if (orderTick == null || peopleWaiting == 0) {
            return 0
        }

        val tickDifference = Ticks.getTick() - orderTick

        val nobodyServed = peopleFinishedEating == 0 && howManyEating() == 0
        // has been 4 ticks, no one has been served, whole group leaves
        if (tickDifference >= EXPECT_FOOD_TICKS && nobodyServed) {
            val leaves = peopleWaiting
            peopleLeftFrustrated += leaves
            customerExperience = CustomerExperience.NEGATIVE
            leave()
            return leaves
        }
        // 2-tick window starts only after someone has food (partial serve)
        if (nobodyServed) {
            return 0
        }
        if (peopleWaiting > 0 && waitingTicksLeft <= 0) {
            val leaves = peopleWaiting
            peopleLeftFrustrated += leaves
            customerExperience = CustomerExperience.NEGATIVE
            peopleWaiting = 0
            return leaves
        }

        return 0
    }

    /**
     * Regular/casual groups leave once unseated past the visitingTick and the following tick;
     * event groups only try to seat within the arrival tick itself.
     */
    open fun leaveUnseated() {
        val currentTick = Ticks.getTick()
        if (!isSeated && currentTick > visitingTick + 1) {
            customerExperience = CustomerExperience.NEGATIVE
            leave()
        }
    }

    /**Start to wait for Food to be received*/
    open fun startWaiting4Food() {
        waitingTicksLeft = EXPECT_FOOD_TICKS + 1
    }
}
