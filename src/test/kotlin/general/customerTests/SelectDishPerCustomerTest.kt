package general.customerTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe

/**
 * Spec: each person in the group calls selectDish; leftover size has no prefs (max recipe id).
 */
class SelectDishPerCustomerTest {
    private val rice = Ingredient("rice", MeasuringUnit.G, 2)
    private val potato = Ingredient("potato", MeasuringUnit.G, 2)
    private val pasta = Ingredient("pasta", MeasuringUnit.G, 2)

    private val menu = listOf(
        Recipe(1, "Rice Bowl", 10, mutableMapOf(rice to 50), listOf(CookType.EXEC)),
        Recipe(2, "Tomato Pasta", 10, mutableMapOf(pasta to 50), listOf(CookType.EXEC)),
        Recipe(3, "Potato Soup", 10, mutableMapOf(potato to 50), listOf(CookType.EXEC))
    )

    /** 2 exclude potato → pasta (highest id they can eat); 4 uncovered → soup. */
    private val prefs = listOf(
        FoodPreferences(size = 2, excludedIngredients = listOf("potato"))
    )

    @Test
    fun regularCasualDeliveryAndEventEachPersonSelectsOnce() {
        for (group in allGroupTypes()) {
            val counts = tallySelectDish(group)
            assertEquals(2, counts["Tomato Pasta"], group::class.simpleName)
            assertEquals(4, counts["Potato Soup"], group::class.simpleName)
            assertEquals(6, counts.values.sum(), group::class.simpleName)
        }
    }

    private fun tallySelectDish(group: CustomerGroup): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        var placed = 0
        for (pref in group.customerPreferences) {
            repeat(pref.size) {
                val dish = group.selectDish(menu)
                counts[dish!!.dishName] = (counts[dish.dishName] ?: 0) + 1
                placed++
            }
        }
        repeat(group.size - placed) {
            val dish = group.selectDish(menu)
            counts[dish!!.dishName] = (counts[dish.dishName] ?: 0) + 1
        }
        return counts
    }

    private fun allGroupTypes(): List<CustomerGroup> {
        return listOf(
            RegularCustomerGroup(
                1,
                6,
                1,
                prefs,
                TableType.COMMON,
                1,
                1,
                1
            ),
            CasualCustomerGroup(
                2,
                6,
                1,
                prefs,
                TableType.COMMON,
                listOf(RestaurantType.ASIAN),
                listOf(1),
                RatingLikelihood.ALWAYS
            ),
            DeliveryCustomerGroup(
                3,
                6,
                1,
                prefs,
                TableType.COMMON,
                listOf(RestaurantType.ASIAN),
                listOf(1),
                RatingLikelihood.ALWAYS,
                5
            ),
            EventCustomerGroup(
                4,
                6,
                1,
                prefs,
                TableType.COMMON,
                listOf(RestaurantType.ASIAN),
                4,
                mutableMapOf()
            )
        )
    }
}
