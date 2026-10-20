package saarlacarte.simulation

/**
 * Global singleton: simulation advances evening/tick; other classes read from here.
 *
 * Within an evening, [tick] stays in 1..24 (forum clarification). [evening] starts at 1.
 * [maxTicks] caps how many serving ticks run in total across all evenings.
 */
object Ticks {
    private var evening: Int = 0
    private var tick: Int = 0
    private var maxTicks: Int = 0
    private var totalTicksElapsed: Int = 0

    /** Configures the tick limit and resets counters. */
    fun init(maxTicksLimit: Int) {
        maxTicks = maxTicksLimit
        evening = 0
        tick = 0
        totalTicksElapsed = 0
    }

    /** Current evening number (starts at 1 after the first [advanceEvening]). */
    fun getEvening(): Int = evening

    /** Current serving tick within the evening (1..24). */
    fun getTick(): Int = tick

    /** Maximum number of serving ticks for the whole simulation. */
    fun getMaxTicks(): Int = maxTicks

    /** Whether another serving tick may still be simulated under [maxTicks]. */
    fun canRunMoreTicks(): Boolean = maxTicks > 0 && totalTicksElapsed < maxTicks

    /** Starts the next evening; the next [advanceTick] begins at tick 1. */
    fun advanceEvening() {
        evening++
        tick = 0
    }

    /**
     * Starts the next serving tick within the current evening.
     *
     * @return false if [maxTicks] is reached and the simulation must stop
     */
    fun advanceTick(): Boolean {
        if (maxTicks == 0 || totalTicksElapsed >= maxTicks) {
            return false
        }
        totalTicksElapsed++
        tick = if (tick == 0) 1 else tick + 1
        return true
    }
}
