package general.parserTests

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.io.TempDir
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Recipe
import saarlacarte.parser.FoodParser
import saarlacarte.simulation.Supplier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FoodParserTest {

    @TempDir
    // lateint-> will set tmp before it is used
    lateinit var tmp: File

    private val parser = FoodParser()

    // base valid food.json, most tests below just mutate a copy of this
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

    // writes json to a temp file and runs it through the parser
    private fun parseJson(json: String): Pair<Supplier, List<Recipe>>? {
        val file = File(tmp, "food.json")
        file.writeText(json)
        return parser.parse(file.path)
    }

    private fun validJsonObject(): JSONObject {
        return JSONObject(validJson)
    }

    private fun getIngredient(json: JSONObject, index: Int): JSONObject {
        return json.getJSONArray("ingredients").getJSONObject(index)
    }

    private fun getRecipe(json: JSONObject, index: Int): JSONObject {
        return json.getJSONArray("recipes").getJSONObject(index)
    }

    // every caller only ever needs the first ingredient of the recipe
    private fun getRecipeIngredient(json: JSONObject, recipeIndex: Int): JSONObject {
        val recipe = getRecipe(json, recipeIndex)
        return recipe.getJSONArray("ingredients").getJSONObject(0)
    }

    // happy path

    @Test
    fun `valid file returns the supplier`() {
        val result = parseJson(validJson)
        assertNotNull(result)
        val supplier = result.first

        assertEquals(3, supplier.ingredients.size)

        val rice = supplier.ingredients.first { it.getName() == "rice" }
        assertEquals(MeasuringUnit.G, rice.getUnit())
        assertEquals(5, rice.getBestBefore())
        assertEquals(1000, supplier.packagingVolumes["rice"])

        val oil = supplier.ingredients.first { it.getName() == "oil" }
        assertEquals(MeasuringUnit.ML, oil.getUnit())
        assertEquals(500, supplier.packagingVolumes["oil"])

        val egg = supplier.ingredients.first { it.getName() == "egg" }
        assertEquals(MeasuringUnit.X, egg.getUnit())
        assertEquals(10, supplier.packagingVolumes["egg"])
    }

    @Test
    fun `valid file returns the recipes`() {
        val result = parseJson(validJson)
        assertNotNull(result)
        val recipes = result.second

        assertEquals(2, recipes.size)

        val friedRice = recipes.first { it.id == 1 }
        assertEquals("Fried Rice", friedRice.dishName)
        assertEquals(20, friedRice.duration)
        assertEquals(listOf(CookType.EXEC, CookType.SOUS), friedRice.eligibleCookTypes)
        assertEquals(RestaurantType.ASIAN, friedRice.basicDishFor)

        val omelette = recipes.first { it.id == 2 }
        assertEquals("Omelette", omelette.dishName)
        assertNull(omelette.basicDishFor)
    }

    @Test
    fun `boundary field values are accepted`() {
        val json = validJsonObject()
        getIngredient(json, 0).put("packagingVolume", 1)
        getIngredient(json, 0).put("bestBefore", 1)
        getRecipe(json, 0).put("duration", 2)
        getRecipe(json, 1).put("duration", 40)
        getRecipeIngredient(json, 0).put("amount", 1)

        val result = parseJson(json.toString())
        assertNotNull(result)
    }

    // schema rejections

    @Test
    fun `missing ingredients key is rejected`() {
        val json = validJsonObject()
        json.remove("ingredients")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `missing recipes key is rejected`() {
        val json = validJsonObject()
        json.remove("recipes")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `unknown top-level key is rejected`() {
        val json = validJsonObject()
        json.put("extra", 1)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `malformed json is rejected`() {
        assertNull(parseJson("{ not json"))
    }

    // schema rejections - ingredient fields

    @Test
    fun `ingredient without a name is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).remove("name")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `ingredient with an empty name is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).put("name", "")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `ingredient without a unit is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).remove("unit")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `ingredient with an unknown unit is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).put("unit", "kg")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `ingredient without a packaging volume is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).remove("packagingVolume")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `packaging volume of zero is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).put("packagingVolume", 0)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `ingredient without a best before is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).remove("bestBefore")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `best before of zero is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).put("bestBefore", 0)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `ingredient with an unknown extra key is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 0).put("color", "red")
        assertNull(parseJson(json.toString()))
    }

    // schema rejections - recipe fields

    @Test
    fun `recipe without an id is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).remove("id")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `recipe with a negative id is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).put("id", -1)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `recipe without a dish name is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).remove("dishName")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `recipe with an empty dish name is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).put("dishName", "")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `recipe without a duration is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).remove("duration")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `duration below 2 is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).put("duration", 1)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `duration above 40 is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).put("duration", 41)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `recipe without a cook type is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).remove("cookType")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `empty cook type list is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).put("cookType", JSONArray())
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `unknown cook type is rejected`() {
        val json = validJsonObject()
        val badCookTypes = JSONArray()
        badCookTypes.put("MASTER")
        getRecipe(json, 0).put("cookType", badCookTypes)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `duplicate cook type in the same recipe is rejected`() {
        val json = validJsonObject()
        val duplicateCookTypes = JSONArray()
        duplicateCookTypes.put("EXEC")
        duplicateCookTypes.put("EXEC")
        getRecipe(json, 0).put("cookType", duplicateCookTypes)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `recipe without any ingredients key is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).remove("ingredients")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `recipe without any ingredients is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).put("ingredients", JSONArray())
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `ingredient amount of zero is rejected`() {
        val json = validJsonObject()
        getRecipeIngredient(json, 1).put("amount", 0)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `recipe with an unknown extra key is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).put("secretIngredient", "love")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `unknown basic dish for restaurant type is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 0).put("basicDishFor", "MARTIAN")
        assertNull(parseJson(json.toString()))
    }

    // ingredient / recipe validation

    @Test
    fun `empty ingredient list is rejected`() {
        val json = validJsonObject()
        json.put("ingredients", JSONArray())
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `duplicate ingredient name is rejected`() {
        val json = validJsonObject()
        getIngredient(json, 1).put("name", "rice")
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `empty recipe list is rejected`() {
        val json = validJsonObject()
        json.put("recipes", JSONArray())
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `duplicate recipe id is rejected`() {
        val json = validJsonObject()
        getRecipe(json, 1).put("id", 1)
        assertNull(parseJson(json.toString()))
    }

    @Test
    fun `two basic dishes with the same name are rejected`() {
        val json = validJsonObject()
        val secondRecipe = getRecipe(json, 1)
        secondRecipe.put("dishName", "Fried Rice")
        secondRecipe.put("basicDishFor", "ASIAN")
        assertNull(parseJson(json.toString()))
    }

    // cross-validation against ingredients

    @Test
    fun `recipe with an unknown ingredient is rejected`() {
        val json = validJsonObject()
        getRecipeIngredient(json, 1).put("name", "sugar")
        assertNull(parseJson(json.toString()))
    }

    // a few whole files that are broken in more than one place

    @Test
    fun `json that is well formed but violates the schema everywhere is rejected`() {
        // syntactically fine json, basically every field is wrong
        val json = """
            {
              "ingredients": [
                { "name": "", "unit": "kg", "packagingVolume": -5, "bestBefore": 0, "color": "red" }
              ],
              "recipes": [
                { "id": -1, "duration": 100, "cookType": [], "ingredients": [] }
              ]
            }
        """.trimIndent()
        assertNull(parseJson(json))
    }

    @Test
    fun `schema valid file that breaks several validation rules at once is rejected`() {
        // this one passes the schema, it's the ingredient/recipe checks that fail
        val json = """
            {
              "ingredients": [
                { "name": "rice", "unit": "g", "packagingVolume": 1000, "bestBefore": 5 },
                { "name": "rice", "unit": "g", "packagingVolume": 500,  "bestBefore": 4 }
              ],
              "recipes": [
                {
                  "id": 1, "dishName": "Bowl", "duration": 10, "cookType": ["EXEC"],
                  "ingredients": [ { "name": "rice", "amount": 100 } ], "basicDishFor": "ASIAN"
                },
                {
                  "id": 1, "dishName": "Bowl", "duration": 12, "cookType": ["SOUS"],
                  "ingredients": [ { "name": "sugar", "amount": 50 } ], "basicDishFor": "ASIAN"
                }
              ]
            }
        """.trimIndent()
        assertNull(parseJson(json))
    }

    @Test
    fun `file with clean ingredients but broken recipes is rejected`() {
        // ingredients are fine here, it's the recipes: id 5 used twice, and
        // the second one references "butter" which isn't in the list at all
        val json = """
            {
              "ingredients": [
                { "name": "flour", "unit": "g",  "packagingVolume": 1000, "bestBefore": 30 },
                { "name": "milk",  "unit": "mL", "packagingVolume": 500,  "bestBefore": 7 }
              ],
              "recipes": [
                {
                  "id": 5, "dishName": "Pancakes", "duration": 15, "cookType": ["PASTRY"],
                  "ingredients": [
                    { "name": "flour", "amount": 200 },
                    { "name": "milk", "amount": 100 }
                  ]
                },
                {
                  "id": 5, "dishName": "Waffles", "duration": 18, "cookType": ["PASTRY"],
                  "ingredients": [
                    { "name": "flour", "amount": 150 },
                    { "name": "butter", "amount": 50 }
                  ]
                }
              ]
            }
        """.trimIndent()
        assertNull(parseJson(json))
    }
}
