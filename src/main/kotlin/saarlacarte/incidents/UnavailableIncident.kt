package saarlacarte.incidents

import saarlacarte.simulation.Supplier

/**
 Incident making an ingredient temporarily unavailable
 **/
class UnavailableIncident(
    id: Int,
    evening: Int,
    private val supplier: Supplier,
    private val ingredientName: String,
    private val duration: Int
) : Incident(id, evening) {
    /**
     Applies the ingredient unavailability
     **/
    override fun execute() {
        supplier.setUnavailable(ingredientName, evening, duration)
    }

    /**
     getter ingredient name
     **/
    fun getIngredientName() = ingredientName

    /**
     getter duration
     **/
    fun getDuration() = duration
}
