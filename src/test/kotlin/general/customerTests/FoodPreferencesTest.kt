package general.customerTests

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
import kotlin.test.Test
import kotlin.test.assertEquals

// test applying preferences in ordering (non-delivery: selectDish)
// test applying preferences in ordering (delivery: selectDeliveryDishes)
//             test applying preferences in browsing

class FoodPreferencesTest {
    val chicken = Ingredient("chicken", MeasuringUnit.G, 2)
    val beef = Ingredient("beef", MeasuringUnit.G, 2)
    val pork = Ingredient("pork", MeasuringUnit.G, 2)
    val rice = Ingredient("rice", MeasuringUnit.G, 2)
    val pasta = Ingredient("pasta", MeasuringUnit.G, 2)
    val water = Ingredient("water", MeasuringUnit.G, 2)
    val cheese = Ingredient("cheese", MeasuringUnit.G, 2)
    val flour = Ingredient("flour", MeasuringUnit.G, 2)
    val egg = Ingredient("egg", MeasuringUnit.G, 2)

    // note: purposefully not sorted by id (1, 3 swapped)
    val availableRecipes = mutableListOf(
        Recipe(
            3,
            "chicken rice",
            10,
            mutableMapOf(chicken to 100, rice to 100),
            listOf(CookType.EXEC),
            null
        ),
        Recipe(
            2,
            "beef pasta",
            10,
            mutableMapOf(beef to 100, pasta to 100),
            listOf(CookType.EXEC),
            RestaurantType.AFRICAN
        ),
        Recipe(
            1,
            "cheese egg",
            10,
            mutableMapOf(cheese to 100, egg to 100),
            listOf(CookType.EXEC),
            RestaurantType.EUROPEAN
        ),
        Recipe(
            4,
            "pastry",
            20,
            mutableMapOf(flour to 100, egg to 100, water to 100),
            listOf(CookType.EXEC),
            RestaurantType.AMERICAN
        ),
        Recipe(
            5,
            "pork rice",
            30,
            mutableMapOf(pork to 100, rice to 100),
            listOf(CookType.EXEC),
            RestaurantType.EUROPEAN
        ),
        Recipe(
            6,
            "meat soup",
            15,
            mutableMapOf(water to 300, chicken to 100, beef to 100, pork to 100),
            listOf(CookType.EXEC),
            RestaurantType.ASIAN
        ),
    )

    /** a function that creates a dummy event customer group**/
    private fun dummyEvent(
        prefs: List<FoodPreferences>,
        favoriteBasicDish: MutableMap<RestaurantType, String>
    ): EventCustomerGroup {
        return EventCustomerGroup(
            1,
            4,
            1,
            prefs,
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            1,
            favoriteBasicDish
        )
    }

    /** a function that creates a dummy delivery customer group**/
    private fun dummyDelivery(
        prefs: List<FoodPreferences>,
        size: Int
    ): CustomerGroup {
        return DeliveryCustomerGroup(
            1,
            size,
            1,
            prefs,
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS,
            1
        )
    }

    /** a function that creates a dummy regular or casual customer group**/
    private fun dummyCustomerGroup(
        prefs: List<FoodPreferences>,
        isRegular: Boolean
    ): CustomerGroup {
        if (isRegular) {
            return RegularCustomerGroup(
                1,
                1,
                1,
                prefs,
                TableType.COMMON,
                1,
                1,
                1
            )
        } else {
            return CasualCustomerGroup(
                1,
                1,
                1,
                prefs,
                TableType.COMMON,
                listOf(RestaurantType.ASIAN),
                listOf(1),
                RatingLikelihood.ALWAYS
            )
        }
    }

    // tests
    @Test
    fun `casual selectDish only excluded`() {
        val pref = FoodPreferences(size = 1, excludedIngredients = listOf("chicken", "beef", "pork"))
        val groupCasual = dummyCustomerGroup(
            listOf(pref),
            false
        )
        // highest id among non-meat foods -> pastry
        assertEquals(
            groupCasual.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "pastry" }
        )
    }

    @Test
    fun `regular selectDish no preference`() {
        val groupReg = dummyCustomerGroup(
            listOf(),
            true
        )
        // highest id
        assertEquals(
            groupReg.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "meat soup" }
        )
    }

    @Test
    fun `casual selectDish only preferred`() {
        val pref = FoodPreferences(size = 1, preferredIngredients = listOf("water", "egg"))
        val groupCasual = dummyCustomerGroup(
            listOf(pref),
            false
        )
        // highest id among non-meat foods -> pastry
        assertEquals(
            groupCasual.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "pastry" }
        )
    }

    @Test
    fun `casual selectDish only favourite dishes`() {
        val pref = FoodPreferences(
            size = 1,
            favoriteDishes = listOf(
                "potato soup",
                "cheese egg",
                "chicken rice"
            )
        )
        val groupCasual = dummyCustomerGroup(
            listOf(pref),
            false
        )
        // chicken rice is the first that matches in the list
        assertEquals(
            groupCasual.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "cheese egg" }
        )
    }

    @Test
    fun `casual selectDish excluded and preferred`() {
        val pref = FoodPreferences(
            size = 1,
            excludedIngredients = listOf("pork"),
            preferredIngredients = listOf("rice", "pasta")
        )
        val groupCasual = dummyCustomerGroup(
            listOf(pref),
            false
        )
        // chicken rice is the first that matches in the list
        assertEquals(
            groupCasual.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "chicken rice" }
        )
    }

    @Test
    fun `regular selectDish`() {
        val pref = FoodPreferences(
            size = 1,
            excludedIngredients = listOf("chicken"),
            preferredIngredients = listOf("rice", "pasta"),
            favoriteDishes = listOf("cheese egg", "chicken rice")
        )
        val groupCasual = dummyCustomerGroup(
            listOf(pref),
            true
        )
        // chicken rice has chicken, so only cheese egg is eligible
        assertEquals(
            groupCasual.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "cheese egg" }
        )
    }

    @Test
    fun `event selectDish excluded and preferred`() {
        val pref1 = FoodPreferences(
            size = 1,
            excludedIngredients = listOf("pork"),
            preferredIngredients = listOf("cheese", "egg", "chicken", "rice")
        )
        val event = dummyEvent(
            listOf(pref1),
            mutableMapOf(
                RestaurantType.EUROPEAN to "pork rice",
                RestaurantType.ASIAN to "meat soup"
            )
        )
        // chicken rice and cheese egg both match, but chicken rice has a higher id
        assertEquals(
            event.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "chicken rice" }
        )
    }

    // TO UNCOMMENT WHEN EVENT ORDERING LOGIC IS IMPLEMENTED
    /*@Test
    fun `event selectDish favourite dishes`() {
        val pref2 = FoodPreferences(
            size = 1,
            favoriteDishes = listOf("chicken rice", "cheese egg")
        )

        val event = dummyEvent(
            listOf(pref2),
            mutableMapOf(
                RestaurantType.EUROPEAN to "pork rice",
                RestaurantType.ASIAN to "meat soup"
            )
        )
        // chicken rice is the first that matches in the list
        assertEquals(
            event.selectDish(availableRecipes, pref2),
            availableRecipes.find { it.dishName == "pork rice" }
        )
    }*/

// preference tests
    @Test
    fun `delivery selectDish applies preferences the same way as casual`() {
        // DeliveryCustomerGroup extends CasualCustomerGroup and does not override selectDish
        // or selectDishForEach, so it must apply excluded/preferred/favorite preferences
        // exactly like a casual group placing an in-house order.
        val pref = FoodPreferences(size = 1, excludedIngredients = listOf("chicken", "beef", "pork"))
        val groupDelivery = dummyDelivery(listOf(pref), 1)

        // highest id among non-meat foods -> pastry (same result as `casual selectDish only excluded`)
        assertEquals(
            groupDelivery.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "pastry" }
        )
    }

    @Test
    fun `delivery selectDish respects favorite dishes over plain preferred ingredients`() {
        val pref = FoodPreferences(
            size = 1,
            preferredIngredients = listOf("water", "egg"),
            favoriteDishes = listOf("meat soup")
        )
        val groupDelivery = dummyDelivery(listOf(pref), 1)

        assertEquals(
            groupDelivery.selectDish(availableRecipes),
            availableRecipes.find { it.dishName == "meat soup" }
        )
    }

    @Test
    fun `selectDeliveryDishes is currently an unused stub that always returns an empty map`() {
        // CustomerGroup.selectDeliveryDishes(...) is `open fun ... = emptyMap()` and is not
        // overridden anywhere (delivery dish selection actually goes through the normal
        // selectDish/takeOrder pipeline instead, see FrontOfHouse.takeOrder). This test pins
        // down that current, surprising behaviour so a future override does not silently
        // change semantics: today it ignores both the available recipes and the group's
        // preferences.
        val pref = FoodPreferences(size = 1, preferredIngredients = listOf("rice"))
        val groupDelivery = dummyDelivery(listOf(pref), 1)

        assertEquals(emptyMap<Recipe, Int>(), groupDelivery.selectDeliveryDishes(availableRecipes))
        assertEquals(emptyMap<Recipe, Int>(), groupDelivery.selectDeliveryDishes(emptyList()))
    }
}
