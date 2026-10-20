package saarlacarte.orders

import saarlacarte.customer.CustomerGroup
import saarlacarte.enums.DishStatus
import saarlacarte.food.Recipe
import saarlacarte.simulation.Ticks
import kotlin.collections.all
import kotlin.collections.any
import kotlin.collections.filter

/**
An order placed by a customer group
 **/
class Order(
    id: Int,
    dishes: MutableList<Dish>,
    delivery: Boolean,
    deliveryDistance: Int?,
    orderedTick: Int
) {
    private var id: Int = id
    private val dishes: MutableList<Dish> = dishes
    private var delivery: Boolean = delivery
    private var deliveryDistance: Int? = deliveryDistance
    private var orderedTick: Int = orderedTick

    /** Unique Order ids across  **/
    companion object {
        private var id = 1

        /** Increment global Order id **/
        fun nextId(): Int = id++
    }

    /** the unique id of this order **/
    fun getId(): Int = id

    /** sets the id **/
    fun setId(newId: Int) {
        id = newId
    }

    /** the dishes within this order **/
    fun getDishes(): MutableList<Dish> = dishes

    /** replaces the dishes within this order **/
    fun setDishes(newDishes: MutableList<Dish>) {
        dishes.clear()
        dishes.addAll(newDishes)
    }

    /**
     Whether this order is a delivery order
     **/
    fun isDelivery(): Boolean = delivery

    /** sets whether this order is a delivery order **/
    fun setDelivery(newDelivery: Boolean) {
        delivery = newDelivery
    }

    /** distance to the customer, in case this is a delivery order **/
    fun getDeliveryDistance(): Int? = deliveryDistance

    /** sets the delivery distance **/
    fun setDeliveryDistance(newDeliveryDistance: Int?) {
        deliveryDistance = newDeliveryDistance
    }

    /** the tick this order was placed **/
    fun getOrderedTick(): Int = orderedTick

    /** sets the ordered tick **/
    fun setOrderedTick(newOrderedTick: Int) {
        orderedTick = newOrderedTick
    }

    /** Sets the status of every dish of [recipe] within this order **/
    fun setDishStatus(recipe: Recipe, status: DishStatus) {
        for (dish in dishes) {
            if (dish.getRecipe() == recipe) {
                dish.setStatus(status)
                if (status == DishStatus.FINISHED) {
                    dish.setFinishedTick(Ticks.getTick())
                }
            }
        }
    }

    /** Serves up to [maxDishes] finished dishes. Returns the name-to-count just marked served. **/
    fun servePartially(group: CustomerGroup, maxDishes: Int = Int.MAX_VALUE): Map<String, Int> {
        val servedDishToAmount: MutableMap<String, Int> = mutableMapOf()
        val finished = dishes.filter { it.getStatus() == DishStatus.FINISHED }
        val allReady = dishes.all {
            it.getStatus() == DishStatus.FINISHED || it.getStatus() == DishStatus.SERVED
        }
        val waitedTwoTicks = finished.any { Ticks.getTick() - it.getFinishedTick() > 1 }
        val alreadyStarted = dishes.any { it.getStatus() == DishStatus.SERVED }
        val canStartServing = allReady || waitedTwoTicks || alreadyStarted

        if (!canStartServing || maxDishes <= 0) {
            return servedDishToAmount
        }

        finished.sortedWith(
            compareByDescending<Dish> { it.isBasic() }.thenBy { it.getRecipe().id }
        ).take(maxDishes).forEach { dish ->
            dish.setStatus(DishStatus.SERVED)
            val name = dish.getRecipe().dishName
            servedDishToAmount[name] = (servedDishToAmount[name] ?: 0) + 1
        }
        if (servedDishToAmount.isNotEmpty()) {
            group.receiveOrder()
        }
        return servedDishToAmount
    }
}
