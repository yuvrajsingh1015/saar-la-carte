package saarlacarte.incidents

/**
 Base class for all simulation incidents
 **/
abstract class Incident(val id: Int, val evening: Int) {
    /**
     Applies this incident's effect to the simulation
     **/
    abstract fun execute()
}
