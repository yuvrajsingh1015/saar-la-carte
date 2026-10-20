package saarlacarte.incidents

import saarlacarte.simulation.Supplier

/**
 Incident changing an ingredient's packaging volume
 **/
class PackagingIncident(
    id: Int,
    evening: Int,
    private val supplier: Supplier,
    private val ingredientName: String,
    private val packagingVolume: Int
) : Incident(id, evening) {

    /**
     getter ingredient name
     **/
    fun getIngredientName() = ingredientName

    /**
     Applies the packaging change
     **/
    override fun execute() {
        supplier.changePackagingVolume(ingredientName, packagingVolume)
    }
}
