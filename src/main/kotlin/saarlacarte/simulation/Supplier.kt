package saarlacarte.simulation

import saarlacarte.food.Ingredient

const val ONE = 1

/**
Placeholder for the global ingredient supplier
 **/
data class Supplier(val ingredients: List<Ingredient>, val packagingVolumes: MutableMap<String, Int>) {
    private val unavailableUntil: MutableMap<String, Int> = mutableMapOf()

    /**getting the amount from supplier in round up packages*/
    fun procure(name: String, requiredAmount: Int): Int {
        if (!isAvailable(name)) return 0
        val volume = packagingVolumes[name] ?: return 0
        // rounding up the number of packages needed
        val neededPackages = (requiredAmount + volume - ONE) / volume
        return neededPackages * volume
    }

    /** Checking availability of stuff*/
    fun isAvailable(name: String): Boolean {
        val until = unavailableUntil[name] ?: return true
        return Ticks.getEvening() > until
    }

    /** Change packaging Volume incident*/
    fun changePackagingVolume(name: String, amount: Int) {
        if (!ingredients.any { it.getName() == name }) return
        packagingVolumes[name] = amount
    }

    /**Unavailable Ingredient Incident*/
    fun setUnavailable(name: String, fromEvening: Int, duration: Int) {
        if (!ingredients.any { it.getName() == name }) return
        unavailableUntil[name] = fromEvening + duration - ONE
    }
}
