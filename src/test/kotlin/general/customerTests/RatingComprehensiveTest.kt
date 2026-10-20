package general.customerTests

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.DishStatus
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Recipe
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.loggers.DriverLogger
import saarlacarte.loggers.RestaurantLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

class RatingComprehensiveTest {

    @BeforeEach
    fun resetTicks() {
        Ticks.init(100)
    }

    @AfterEach
    fun clearTicks() {
        Ticks.init(100)
    }

    // -------------------------------------------------------------------------
    // Helper Factories & Reflection Utilities
    // -------------------------------------------------------------------------

    private fun setExperience(group: CustomerGroup, experience: CustomerExperience) {
        val field = CustomerGroup::class.java.getDeclaredField("customerExperience")
        field.isAccessible = true
        field.set(group, experience)
    }

    private fun getExperience(group: CustomerGroup): CustomerExperience {
        val field = CustomerGroup::class.java.getDeclaredField("customerExperience")
        field.isAccessible = true
        return field.get(group) as CustomerExperience
    }

    private fun invokeRate(restaurant: Restaurant, customers: List<CustomerGroup>) {
        val method = Restaurant::class.java.getDeclaredMethod("rate", List::class.java)
        method.isAccessible = true
        method.invoke(restaurant, customers)
    }

    private fun regularGroup(
        id: Int = 1,
        size: Int = 2,
        visitingTick: Int = 5,
        visitingStart: Int = 1,
        visitingPeriod: Int = 2,
        restaurantId: Int = 1
    ): RegularCustomerGroup {
        return RegularCustomerGroup(
            id,
            size,
            visitingTick,
            emptyList(),
            TableType.COMMON,
            visitingStart,
            visitingPeriod,
            restaurantId
        )
    }

    private fun eventGroup(
        id: Int = 2,
        size: Int = 10,
        visitingTick: Int = 5,
        eventEvening: Int = 4
    ): EventCustomerGroup {
        return EventCustomerGroup(
            id,
            size,
            visitingTick,
            emptyList(),
            TableType.COMMON,
            listOf(RestaurantType.EUROPEAN),
            eventEvening,
            mutableMapOf(RestaurantType.EUROPEAN to "Pizza")
        )
    }

    private fun casualGroup(
        id: Int = 3,
        size: Int = 2,
        visitingTick: Int = 5,
        ratingLikelihood: RatingLikelihood = RatingLikelihood.ALWAYS,
        visitingEvenings: List<Int> = listOf(1),
        preferences: List<FoodPreferences> = emptyList()
    ): CasualCustomerGroup {
        return CasualCustomerGroup(
            id,
            size,
            visitingTick,
            preferences,
            TableType.COMMON,
            listOf(RestaurantType.EUROPEAN),
            visitingEvenings,
            ratingLikelihood
        )
    }

    private fun deliveryGroup(
        id: Int = 4,
        size: Int = 1,
        visitingTick: Int = 10,
        ratingLikelihood: RatingLikelihood = RatingLikelihood.ALWAYS,
        deliveryDistance: Int = 5,
        visitingEvenings: List<Int> = listOf(1)
    ): DeliveryCustomerGroup {
        return DeliveryCustomerGroup(
            id,
            size,
            visitingTick,
            emptyList(),
            null,
            listOf(RestaurantType.EUROPEAN),
            visitingEvenings,
            ratingLikelihood,
            deliveryDistance
        )
    }

    private fun createRestaurant(
        id: Int = 1,
        openingStart: Int = 1,
        openingEnd: Int = 24,
        positiveRatings: Int = 10,
        negativeRatings: Int = 5,
        logger: RestaurantLogger? = null,
        foh: FrontOfHouse = mock(),
        kitchen: Kitchen = mock()
    ): Restaurant {
        val staff = mock<Staff>()
        whenever(staff.getDrivers()).thenReturn(mutableListOf())
        whenever(staff.getWaiters()).thenReturn(mutableListOf())

        val restaurant = Restaurant(
            id = id,
            name = "TestBistro",
            type = RestaurantType.EUROPEAN,
            openingTickStart = openingStart,
            openingTickEnd = openingEnd,
            canHostEvent = true,
            staff = staff,
            positiveRatings = positiveRatings,
            negativeRatings = negativeRatings,
            frontOfHouse = foh,
            kitchen = kitchen,
            deliveryDriverCount = 0
        )
        restaurant.logger = logger
        return restaurant
    }

    // -------------------------------------------------------------------------
    // 1. Rating Conversion & Customer Types
    // -------------------------------------------------------------------------

    @Test
    fun regularGroupRatesAndQuitsAfterTwoConsecutiveNegatives() {
        val group = regularGroup(visitingStart = 1, visitingPeriod = 2)

        Ticks.advanceEvening() // Evening 1
        assertTrue(group.isVisiting())

        // Positive / neutral -> rates 1
        setExperience(group, CustomerExperience.POSITIVE)
        assertEquals(1, group.giveRating())
        assertFalse(group.lastExperience)

        setExperience(group, CustomerExperience.NEUTRAL)
        assertEquals(1, group.giveRating())
        assertFalse(group.lastExperience)

        // 1st bad experience -> rates -1, records lastExperience
        setExperience(group, CustomerExperience.NEGATIVE)
        assertEquals(-1, group.giveRating())
        assertTrue(group.lastExperience)
        assertFalse(group.quitVisiting)

        // Intervening positive resets consecutive counter
        setExperience(group, CustomerExperience.POSITIVE)
        assertEquals(1, group.giveRating())
        assertFalse(group.lastExperience)

        // Two bad experiences in a row
        setExperience(group, CustomerExperience.NEGATIVE)
        assertEquals(-1, group.giveRating())
        assertTrue(group.lastExperience)

        setExperience(group, CustomerExperience.NEGATIVE)
        assertEquals(-1, group.giveRating())
        assertTrue(group.quitVisiting) // permanently quits

        // Scheduled visit does not occur after quitting
        Ticks.advanceEvening()
        Ticks.advanceEvening()
        assertFalse(group.isVisiting())
    }

    @Test
    fun eventGroupAlwaysRates() {
        val group = eventGroup()

        setExperience(group, CustomerExperience.POSITIVE)
        assertEquals(1, group.giveRating())

        setExperience(group, CustomerExperience.NEUTRAL)
        assertEquals(1, group.giveRating())

        setExperience(group, CustomerExperience.NEGATIVE)
        assertEquals(-1, group.giveRating())
    }

    @Test
    fun casualAndDeliveryGroupMatrix() {
        val never = casualGroup(ratingLikelihood = RatingLikelihood.NEVER)
        val some = casualGroup(ratingLikelihood = RatingLikelihood.SOME)
        val always = casualGroup(ratingLikelihood = RatingLikelihood.ALWAYS)

        for (g in listOf(never, some, always)) setExperience(g, CustomerExperience.POSITIVE)
        assertEquals(0, never.giveRating())
        assertEquals(1, some.giveRating())
        assertEquals(1, always.giveRating())

        for (g in listOf(never, some, always)) setExperience(g, CustomerExperience.NEUTRAL)
        assertEquals(0, never.giveRating())
        assertEquals(0, some.giveRating()) // SOME never rates neutral
        assertEquals(1, always.giveRating())

        for (g in listOf(never, some, always)) setExperience(g, CustomerExperience.NEGATIVE)
        assertEquals(0, never.giveRating())
        assertEquals(-1, some.giveRating())
        assertEquals(-1, always.giveRating())
    }

    // -------------------------------------------------------------------------
    // 2. Experience Transitions (Board: Change Experience)
    // -------------------------------------------------------------------------

    @Test
    fun dineInExpectationWindowBoundaries() {
        Ticks.advanceEvening()
        val recipe = mock<Recipe>()

        // 1. Tick 4: delay = 4 - 1 = 3 (< 4) -> POSITIVE
        repeat(4) { Ticks.advanceTick() }
        val early = casualGroup(size = 1)
        val dish1 = Dish(recipe, DishStatus.SERVED)
        early.order = Order(1, mutableListOf(dish1), false, null, orderedTick = 1)
        early.receiveOrder()
        assertEquals(CustomerExperience.POSITIVE, getExperience(early))

        // 2. Tick 5: delay = 5 - 1 = 4 (== 4) -> NEUTRAL
        Ticks.advanceTick() // tick 5
        val onTime = casualGroup(size = 1)
        val dish2 = Dish(recipe, DishStatus.SERVED)
        onTime.order = Order(2, mutableListOf(dish2), false, null, orderedTick = 1)
        onTime.receiveOrder()
        assertEquals(CustomerExperience.NEUTRAL, getExperience(onTime))

        // 3. Tick 6: delay = 6 - 1 = 5 (> 4) -> NEGATIVE
        Ticks.advanceTick() // tick 6
        val late = casualGroup(size = 1)
        val dish3 = Dish(recipe, DishStatus.SERVED)
        late.order = Order(3, mutableListOf(dish3), false, null, orderedTick = 1)
        late.receiveOrder()
        assertEquals(CustomerExperience.NEGATIVE, getExperience(late))
    }

    @Test
    fun deliveryArrivalTickBoundaries() {
        Ticks.advanceEvening()
        val recipe = mock<Recipe>()

        // 1. tick < visitingTick (4 < 5) -> POSITIVE
        repeat(4) { Ticks.advanceTick() }
        val early = deliveryGroup(visitingTick = 5)
        early.order = Order(11, mutableListOf(Dish(recipe, DishStatus.SERVED)), true, 5, 1)
        early.receiveOrder()
        assertEquals(CustomerExperience.POSITIVE, getExperience(early))

        // 2. tick == visitingTick (5 == 5) -> NEUTRAL
        Ticks.advanceTick()
        val onTime = deliveryGroup(visitingTick = 5)
        onTime.order = Order(12, mutableListOf(Dish(recipe, DishStatus.SERVED)), true, 5, 1)
        onTime.receiveOrder()
        assertEquals(CustomerExperience.NEUTRAL, getExperience(onTime))

        // 3. tick > visitingTick (6 > 5) -> NEGATIVE
        Ticks.advanceTick()
        val late = deliveryGroup(visitingTick = 5)
        late.order = Order(13, mutableListOf(Dish(recipe, DishStatus.SERVED)), true, 5, 1)
        late.receiveOrder()
        assertEquals(CustomerExperience.NEGATIVE, getExperience(late))
    }

    @Test
    fun dineInGivesUpAfterFourTicksWithoutAnyFood() {
        Ticks.advanceEvening()
        repeat(5) { Ticks.advanceTick() } // tick 5

        val group = casualGroup(size = 2)
        val recipe = mock<Recipe>()
        val dish = Dish(recipe, DishStatus.NOTSTARTED)
        group.order = Order(20, mutableListOf(dish), false, null, orderedTick = 1) // 5 - 1 = 4 ticks elapsed

        // Whole group gives up, leaves, and becomes negative
        val leavers = group.waitAndGiveUp()
        assertEquals(2, leavers)
        assertEquals(CustomerExperience.NEGATIVE, getExperience(group))
        assertTrue(group.hasLeftVisit)
    }

    @Test
    fun partialOrderingFailureMarksGroupExperienceNegative() {
        val group = casualGroup(size = 3)
        group.peopleLeftFrustrated = 1 // 1 customer couldn't order and left

        // Group's overall experience is tainted
        group.markNegativeExperience()
        assertEquals(CustomerExperience.NEGATIVE, getExperience(group))
        assertEquals(-1, group.giveRating())
    }

    @Test
    fun closingEvictionDistinguishesFinishedFromUnfinishedDiners() {
        val unfinished = casualGroup(size = 4)
        unfinished.peopleFinishedEating = 2
        unfinished.applyEndOfOpeningExperience()
        assertEquals(CustomerExperience.NEGATIVE, getExperience(unfinished))

        val finished = casualGroup(size = 4)
        finished.peopleFinishedEating = 4
        setExperience(finished, CustomerExperience.POSITIVE)
        finished.applyEndOfOpeningExperience()
        assertEquals(CustomerExperience.POSITIVE, getExperience(finished)) // stays positive
    }

    // -------------------------------------------------------------------------
    // 3. Review Triggers & Timing (Board: getReview & TODO 5)
    // -------------------------------------------------------------------------

    @Test
    fun prepFailedReservationRatesInFirstTickOfEvening() {
        val logger = mock<RestaurantLogger>()
        val foh = mock<FrontOfHouse>()
        whenever(foh.hasReservedTable(any())).thenReturn(false)
        whenever(foh.freeSeatCount()).thenReturn(0)

        val menu = Menu(Supplier(emptyList(), mutableMapOf()), emptyList(), RestaurantType.EUROPEAN)
        val kitchen = mock<Kitchen>()
        whenever(kitchen.menu).thenReturn(menu)
        whenever(kitchen.cooks).thenReturn(mutableListOf())

        val restaurant =
            createRestaurant(openingStart = 1, openingEnd = 24, logger = logger, foh = foh, kitchen = kitchen)
        val regular = regularGroup(id = 55)

        // Prep phase identifies reservation failure
        Ticks.advanceEvening()
        restaurant.runPreparationPhase(listOf(regular))

        // No ratings logged during prep phase
        verify(logger, never()).receivedRating(any(), any(), any(), any())

        // First serving tick: rates immediately at opening start
        Ticks.advanceTick() // tick 1
        restaurant.runServingPhaseTick()

        verify(logger).receivedRating(eq(55), eq("NEGATIVE"), any(), any())
        verify(logger).ratingStatus(1)
    }

    @Test
    fun deliveryGivesUpAndRatesAtVisitingTickPlusThree() {
        val logger = mock<RestaurantLogger>()
        val deliveryLogger = mock<DeliveryCustomerLogger>()
        val driverLogger = mock<DriverLogger>()
        val restaurant = createRestaurant(openingStart = 1, openingEnd = 24, logger = logger)

        // Assign driver and delivery loggers so DeliveryService.deliver() does not early-return
        restaurant.deliveryService.assignLogger(driverLogger, deliveryLogger)

        val group = deliveryGroup(id = 88, size = 1, visitingTick = 5)
        val dish = Dish(mock<Recipe>(), DishStatus.NOTSTARTED)
        group.order = Order(801, mutableListOf(dish), true, 5, 1)
        restaurant.addCustomerGroup(group)

        Ticks.advanceEvening()

        // Tick 7 (visitingTick + 2): Still within the waiting threshold, does not give up or rate
        repeat(7) { Ticks.advanceTick() }
        restaurant.runServingPhaseTick()
        verify(deliveryLogger, never()).deliveryGivenUp(any(), any())
        verify(logger, never()).receivedRating(eq(88), any(), any(), any())

        // Tick 8 (visitingTick + 3): Delivery times out, logs give-up, and rates negative
        Ticks.advanceTick()
        restaurant.runServingPhaseTick()

        assertTrue(group.checkGaveUp())
        verify(deliveryLogger).deliveryGivenUp(88, 801)
        verify(logger).receivedRating(eq(88), eq("NEGATIVE"), any(), any())
    }

    @Test
    fun endOfEveningDoesNotRateOutboundDeliveries() {
        val logger = mock<RestaurantLogger>()
        val restaurant = createRestaurant(logger = logger)

        val group = deliveryGroup(id = 77)
        group.order = Order(701, mutableListOf(Dish(mock<Recipe>(), DishStatus.INPROGRESS)), true, 5, 1)
        restaurant.addCustomerGroup(group)

        // End of evening cancels outbound delivery without rating
        restaurant.endOfEvening()

        verify(logger, never()).receivedRating(any(), any(), any(), any())
        assertEquals(0, restaurant.statistics.getRatingsReceived())
    }

    @Test
    fun duplicateRatingsArePreventedWithinSameEvening() {
        val logger = mock<RestaurantLogger>()
        val restaurant = createRestaurant(id = 1, positiveRatings = 10, negativeRatings = 5, logger = logger)

        val group = eventGroup(id = 99)
        setExperience(group, CustomerExperience.POSITIVE)

        // Rate twice in the same evening
        invokeRate(restaurant, listOf(group, group))
        invokeRate(restaurant, listOf(group))

        assertEquals(11, restaurant.positiveRatings)
        verify(logger).receivedRating(99, "POSITIVE", 11, 5)
        assertEquals(1, restaurant.statistics.getRatingsReceived())

        // Next evening allows rating again
        restaurant.endOfEvening()
        invokeRate(restaurant, listOf(group))

        assertEquals(12, restaurant.positiveRatings)
        verify(logger).receivedRating(99, "POSITIVE", 12, 5)
        assertEquals(2, restaurant.statistics.getRatingsReceived())
    }
}
