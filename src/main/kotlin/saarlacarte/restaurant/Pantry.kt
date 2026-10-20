package saarlacarte.restaurant

import saarlacarte.food.Ingredient
import saarlacarte.food.Package
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

/**
Placeholder for a restaurant's pantry
 **/
class Pantry(private val supplier: Supplier) {
    private val supplies: MutableMap<Ingredient, Int> = linkedMapOf()
    private val reserved: MutableMap<Ingredient, Int> = linkedMapOf()
    private val packages: MutableList<Package> = mutableListOf()

    /** clears out all supplies that are older than current evening
     * returns the amounts of ingredients that were thrown out*/
    fun clearOldPackages(): Map<Ingredient, Int> {
        val oldPackages: List<Package> = packages.filter { it.expirationDate < Ticks.getEvening() }
        val returnMap = linkedMapOf<Ingredient, Int>()

        for ((ingredient, amount) in oldPackages) {
            supplies[ingredient] = (supplies[ingredient] ?: 0) - amount
            returnMap.merge(ingredient, amount, Int::plus)
        }

        packages.removeAll { it.expirationDate < Ticks.getEvening() }
        return returnMap
    }

    /** clears out the reserved ingredients from supplies and packages list*/
    fun removeReserved() {
        release(reserved)
    }

    /** gets the entire supplies map*/
    fun getSupplies(): Map<Ingredient, Int> = LinkedHashMap(supplies)

    /**takes out something from pantry*/
    fun reserve(ingredients: Map<Ingredient, Int>): Boolean {
        if (!validateAmounts(ingredients)) return false
        if (ingredients.any { (ingredient, amount) -> amount > 0 && (supplies[ingredient] ?: 0) < amount }) {
            return false
        }
        for ((ingredient, amount) in ingredients) {
            supplies[ingredient] = (supplies[ingredient] ?: 0) - amount
            reserved[ingredient] = (reserved[ingredient] ?: 0) + amount
        }
        return true
    }

    private fun release(ingredients: Map<Ingredient, Int>) {
        if (!validateAmounts(ingredients)) return
        ingredients.filterValues { it > 0 }.forEach { (_, _) ->
        }
        for ((ingredient, amount) in ingredients) {
            var curAmount = amount
            while (curAmount > 0) {
                val targetIndex = packages.indexOfFirst { it.ingredient == ingredient }
                if (targetIndex == -1) {
                    val name = ingredient.getName()
                    throw IllegalArgumentException("releasing more than available of $name")
                }
                if (packages[targetIndex].amount > curAmount) {
                    packages[targetIndex].amount -= curAmount
                    curAmount = 0
                } else {
                    curAmount -= packages[targetIndex].amount
                    packages.removeAt(targetIndex)
                }
            }
            reserved[ingredient] = (reserved[ingredient] ?: 0) - amount
        }
    }

    /**gets how much of an ingredient is available in pantry*/
    fun availableAmount(ingredient: Ingredient): Int = supplies.getOrDefault(ingredient, 0)

    /** Procures every needed amount once, in deterministic ingredient-name order. */
    fun restock(needed: Map<Ingredient, Int>): Map<Ingredient, Int> {
        val procured = linkedMapOf<Ingredient, Int>()
        if (!validateAmounts(needed)) return procured
        needed.entries.sortedBy { it.key.getName() }.forEach { (ingredient, amount) ->
            if (amount == 0) {
                return@forEach
            }
            val actualAmount = supplier.procure(ingredient.getName(), amount)
            if (actualAmount > 0) {
                packages.add(Package(ingredient, actualAmount, ingredient.getBestBefore() + Ticks.getEvening() - 1))
                val stocked = supplies.getOrDefault(ingredient, 0).toLong() + actualAmount
                require(stocked <= Int.MAX_VALUE) { "Pantry amount overflows Int" }
                supplies[ingredient] = stocked.toInt()
                procured[ingredient] = actualAmount
            }
        }
        return procured
    }

    /** get the supplier of the restaurant*/
    fun getSupplier(): Supplier = supplier

    private fun validateAmounts(ingredients: Map<Ingredient, Int>): Boolean {
        return ingredients.values.all { it >= 0 }
    }

    /**setter for supplies (only for testing)**/
    fun setSupplies(supplies: MutableMap<Ingredient, Int>) {
        this.supplies.clear()
        this.supplies.putAll(supplies)
    }

    /**setter for packages (only for testing)**/
    fun setPackages(packages: MutableList<Package>) {
        this.packages.clear()
        this.packages.addAll(packages)
    }

    /**getter for packages (only for testing)**/
    fun getPackages() = this.packages

    /**setter for reserved (only for testing)**/
    fun setReserved(reserved: MutableMap<Ingredient, Int>) {
        this.reserved.clear()
        this.reserved.putAll(reserved)
    }
}
