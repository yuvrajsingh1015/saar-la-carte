package general.simulationTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.LogLevel
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.incidents.PackagingIncident
import saarlacarte.incidents.RecipeIncident
import saarlacarte.incidents.UnavailableIncident
import saarlacarte.loggers.SimulationLogger
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.Simulation
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.readLines
import kotlin.test.Test

private const val TICKS_PER_EVENING = 24
private const val EVENT_VISIT_EVENING = 4
private const val TICKS_UNTIL_EVENT_EVENING = TICKS_PER_EVENING * 3 + 1

/** F01: Simulation attach / decide / incident-type branches. */
class SimulationCoverageTest {

    private lateinit var simulation: Simulation
    private lateinit var logFile: Path
    private lateinit var rice: Ingredient
    private lateinit var riceBowl: Recipe

    @BeforeEach
    fun setUp() {
        logFile = createTempFile(prefix = "simulation-coverage", suffix = ".log")
        simulation = Simulation(SimulationLogger(LogLevel.DEBUG, logFile.toString()))
        Ticks.init(TICKS_PER_EVENING)
        rice = Ingredient("rice", MeasuringUnit.G, 3)
        riceBowl = Recipe(1, "Rice Bowl", 10, mutableMapOf(rice to 1), listOf(CookType.EXEC))
    }

    @Test
    fun logsEachNamedIncidentTypeOnEveningOne() {
        val supplier = Supplier(listOf(rice), mutableMapOf("rice" to 1))
        simulation.incidents = listOf(
            RecipeIncident(1, 1, listOf(riceBowl), "rice", 0),
            PackagingIncident(2, 1, supplier, "rice", 2),
            UnavailableIncident(3, 1, supplier, "rice", 1)
        )
        val log = runSimulation()
        assertTrue(log.any { it.contains("Incident 1 of type RECIPE") })
        assertTrue(log.any { it.contains("Incident 2 of type PACKAGING") })
        assertTrue(log.any { it.contains("Incident 3 of type UNAVAILABLE") })
    }

    @Test
    fun attachesVisitingRegularAndSkipsEveryoneElseAtPrep() {
        val restaurant = capableRestaurant(1)
        val visiting = regular(id = 1, restaurantId = 1, visitingStart = 1)
        val wrongRestaurant = regular(id = 2, restaurantId = 99, visitingStart = 1)
        val laterRegular = regular(id = 3, restaurantId = 1, visitingStart = 2)
        val event = eventGroup(id = 4, eventEvening = EVENT_VISIT_EVENING)
        val casual = casual(id = 5)
        restaurant.addCustomerGroup(visiting)
        simulation.restaurants = listOf(restaurant)
        simulation.customers = listOf(visiting, wrongRestaurant, laterRegular, event, casual)
        simulation.assignLoggers()
        runSimulation()
        assertTrue(visiting in restaurant.customerGroups || visiting.hasLeftVisit)
        assertFalse(wrongRestaurant in restaurant.customerGroups)
        assertFalse(laterRegular in restaurant.customerGroups)
        assertFalse(event in restaurant.customerGroups)
        assertFalse(casual in restaurant.customerGroups)
    }

    @Test
    fun casualsDecideInTypeOrderAndEventsAreNotAttachedUntilVisitEvening() {
        Ticks.init(TICKS_UNTIL_EVENT_EVENING)
        val restaurant = capableRestaurant(1)
        val event = eventGroup(id = 20, eventEvening = EVENT_VISIT_EVENING)
        val casual = casual(id = 1, visitingTick = 1)
        simulation.restaurants = listOf(restaurant)
        simulation.customers = listOf(casual, event)
        simulation.assignLoggers()
        val log = runSimulation()
        val eventDecide = log.indexOfFirst { it.contains("Group 20 decided on restaurant 1") }
        val casualDecide = log.indexOfFirst { it.contains("Group 1 decided on restaurant 1") }
        assertTrue(eventDecide in 0..<casualDecide)
        assertEquals(restaurant, event.getReservedRestaurant())
        assertTrue(event in restaurant.customerGroups || event.hasLeftVisit || event.isSeated)
    }

    @Test
    fun logsNoDecisionWhenNoRestaurantMatches() {
        val restaurant = capableRestaurant(1)
        val casual = CasualCustomerGroup(
            7,
            1,
            1,
            emptyList(),
            TableType.COMMON,
            listOf(RestaurantType.EUROPEAN),
            listOf(1),
            RatingLikelihood.ALWAYS
        )
        simulation.restaurants = listOf(restaurant)
        simulation.customers = listOf(casual)
        simulation.assignLoggers()
        val log = runSimulation()
        assertTrue(log.any { it.contains("Group 7 could not decide for a restaurant") })
        assertFalse(casual in restaurant.customerGroups)
    }

    @Test
    fun deliveryDecidesWhenADriverIsFree() {
        Ticks.init(6)
        val restaurant = capableRestaurant(1, drivers = 1)
        val group = DeliveryCustomerGroup(
            8,
            1,
            6,
            emptyList(),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS,
            5
        )
        simulation.restaurants = listOf(restaurant)
        simulation.customers = listOf(group)
        simulation.assignLoggers()
        val log = runSimulation()
        assertTrue(log.any { it.contains("Group 8 decided on restaurant 1") })
        assertTrue(group in restaurant.customerGroups || group.hasLeftVisit)
    }

    @Test
    fun logStatisticsWithNoRestaurantLoggerDoesNotCrash() {
        simulation.restaurants = listOf(capableRestaurant(1))
        runSimulation()
        assertEquals(0, simulation.restaurants[0].statistics.getMealsCooked())
    }

    private fun runSimulation(): List<String> {
        simulation.run()
        return logFile.readLines()
    }

    private fun capableRestaurant(id: Int, drivers: Int = 0): Restaurant {
        val staff = Staff(1, mapOf(CookType.EXEC to 1), drivers)
        val supplier = Supplier(listOf(rice), mutableMapOf("rice" to 1))
        val menu = Menu(supplier, listOf(riceBowl), RestaurantType.ASIAN)
        menu.pantry.restock(mapOf(rice to 20))
        return Restaurant(
            id,
            "R$id",
            RestaurantType.ASIAN,
            1,
            TICKS_PER_EVENING,
            true,
            staff,
            0,
            0,
            FrontOfHouse(listOf(Table(1, TableType.COMMON, 8)), staff.getWaiters(), null, menu),
            Kitchen(staff.getCooks(), menu),
            drivers
        )
    }

    private fun regular(id: Int, restaurantId: Int, visitingStart: Int) = RegularCustomerGroup(
        id,
        1,
        1,
        emptyList(),
        TableType.COMMON,
        visitingStart,
        1,
        restaurantId
    )

    private fun casual(id: Int, visitingTick: Int = 1) = CasualCustomerGroup(
        id,
        1,
        visitingTick,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        listOf(1),
        RatingLikelihood.ALWAYS
    )

    private fun eventGroup(id: Int, eventEvening: Int) = EventCustomerGroup(
        id,
        2,
        1,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        eventEvening,
        mutableMapOf()
    )
}
