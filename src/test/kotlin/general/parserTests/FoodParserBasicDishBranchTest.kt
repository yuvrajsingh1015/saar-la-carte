package general.parserTests

import org.json.JSONObject
import org.junit.jupiter.api.io.TempDir
import saarlacarte.food.Recipe
import saarlacarte.parser.FoodParser
import saarlacarte.simulation.Supplier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * F03: Parse Ingredients & Recipes.
 *
 * FoodParser.validateRecipe only enforces a *global* uniqueness rule on basic-dish names
 * (`basicDishes.map { it.dishName }.toSet().size == basicDishes.size`, see FoodParser.kt).
 * The alternative, stricter-by-nothing "per restaurant type" version of that check is present
 * in the source only as a commented-out block, so two basic dishes that share a name but
 * target *different* restaurant types are rejected exactly the same way as if they targeted
 * the same type. This was not covered by the existing `two basic dishes with the same name
 * are rejected` test (which only ever uses the same restaurant type for both), so this test
 * pins down the actually-shipped (global, cross-type) behaviour.
 */
class FoodParserBasicDishBranchTest {

    @TempDir
    lateinit var tmp: File

    private val parser = FoodParser()

    private val validJson = """
        {
          "ingredients": [
            { "name": "rice", "unit": "g",  "packagingVolume": 1000, "bestBefore": 5 },
            { "name": "oil",  "unit": "mL", "packagingVolume": 500,  "bestBefore": 10 },
            { "name": "egg",  "unit": "X",  "packagingVolume": 10,   "bestBefore": 3 }
          ],
          "recipes": [
            {
              "id": 1,
              "dishName": "Fried Rice",
              "duration": 20,
              "cookType": ["EXEC", "SOUS"],
              "ingredients": [
                { "name": "rice", "amount": 200 },
                { "name": "oil",  "amount": 20 }
              ],
              "basicDishFor": "ASIAN"
            },
            {
              "id": 2,
              "dishName": "Omelette",
              "duration": 10,
              "cookType": ["VEGETABLE"],
              "ingredients": [ { "name": "egg", "amount": 3 } ]
            }
          ]
        }
    """.trimIndent()

    private fun parseJson(json: String): Pair<Supplier, List<Recipe>>? {
        val file = File(tmp, "food.json")
        file.writeText(json)
        return parser.parse(file.path)
    }

    private fun validJsonObject(): JSONObject = JSONObject(validJson)

    private fun getRecipe(json: JSONObject, index: Int): JSONObject =
        json.getJSONArray("recipes").getJSONObject(index)

    @Test
    fun `two basic dishes with the same name but different restaurant types are still rejected`() {
        val json = validJsonObject()
        val secondRecipe = getRecipe(json, 1)
        // same dishName as recipe 0 ("Fried Rice"), but a *different* basicDishFor type
        secondRecipe.put("dishName", "Fried Rice")
        secondRecipe.put("basicDishFor", "EUROPEAN")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `two basic dishes with different names but the same restaurant type are accepted`() {
        val json = validJsonObject()
        val secondRecipe = getRecipe(json, 1)
        secondRecipe.put("basicDishFor", "ASIAN")
        // dishName stays "Omelette", distinct from "Fried Rice"
        val result = parseJson(json.toString())
        assertNotNull(result)
    }

    @Test
    fun `a non basic dish sharing its name with a basic dish is accepted`() {
        val json = validJsonObject()
        val secondRecipe = getRecipe(json, 1)
        // recipe 1 has no basicDishFor at all, so it must not collide with recipe 0's
        // "Fried Rice" even though it now shares its dishName
        secondRecipe.put("dishName", "Fried Rice")
        val result = parseJson(json.toString())
        assertNotNull(result)
    }
}
