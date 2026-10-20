package general.customerTests

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import saarlacarte.customer.FoodPreferences
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.TableType
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks
import kotlin.test.Test

// import saarlacarte.food.Ingredient
// import saarlacarte.food.Recipe
// import saarlacarte.enums.CookType
// import saarlacarte.enums.MeasuringUnit
// import saarlacarte.enums.RestaurantType
class RegularCustomerGroupTest {
    val group = RegularCustomerGroup(
        1,
        1,
        1,
        listOf(FoodPreferences(1, emptyList(), emptyList(), listOf("Potato Soup"))),
        TableType.COMMON,
        1,
        1,
        1
    )

    @BeforeEach
    fun resetTicks() {
        Ticks.init(50)
    }

    @AfterEach
    fun clearTicks() {
        Ticks.init(50)
    }

    @Test
    fun checkVisitingAfterFrustration() {
        group.quitVisiting = true
        Ticks.advanceTick()
        assertFalse { group.isArriving() }
        Ticks.advanceEvening()
        Ticks.advanceTick()
        assertFalse { group.isArriving() }
    }

    @Test
    fun checkPeriodicVisiting() {
        val group2 = RegularCustomerGroup(
            1,
            1,
            1,
            listOf(FoodPreferences(1, emptyList(), emptyList(), listOf("Potato Soup"))),
            TableType.COMMON,
            1,
            2,
            1
        )
        Ticks.advanceEvening()
        for (evening in 1..20) {
            Ticks.advanceTick()
            if (evening % 2 == 1) {
                assertTrue(
                    group2.isVisiting(),
                    "visit was expected on evening ${Ticks.getEvening()} on tick ${Ticks.getTick()}"
                )
                assertTrue(
                    group2.isArriving(),
                    "visit was expected on evening ${Ticks.getEvening()} on tick ${Ticks.getTick()}"
                )
                Ticks.advanceTick()
                assertFalse(group2.isArriving())
            } else {
                assertFalse { group2.isVisiting() }
            }
            Ticks.advanceEvening()
        }
    }

    @Test
    fun assigningTheSameOrderTwiceDoesNotDuplicateLast3() {
        val order = Order(1, mutableListOf(), false, null, 3)
        group.order = order
        group.order = order
        assertEquals(1, group.last3.size)
        assertEquals(order, group.last3.single())
    }
}
