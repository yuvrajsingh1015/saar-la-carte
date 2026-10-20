package general.customerTests

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks
import kotlin.test.assertEquals

class DeliveryCustomerTest {
    private lateinit var mockLogger: DeliveryCustomerLogger
    private lateinit var chickenRice: Recipe

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()
        mockLogger = mock()

        val rice = Ingredient("rice", MeasuringUnit.G, 3)
        val chicken = Ingredient("chicken", MeasuringUnit.G, 2)
        chickenRice = Recipe(
            1,
            "Chicken Rice",
            15,
            mutableMapOf(rice to 2, chicken to 1),
            listOf(CookType.SAUCE)
        )
    }

    @AfterEach
    fun tearDown() {
        Ticks.init(1)
    }

    // these with the likelihood always
    private fun createGroup(
        id: Int = 12,
        size: Int = 2,
        visitingTick: Int = 16,
        customerPreferences: List<FoodPreferences> = emptyList(),
        preferredTableType: TableType? = null,
        restaurantTypes: List<RestaurantType> = listOf(RestaurantType.ASIAN),
        visitingEvenings: List<Int> = listOf(1, 2),
        ratingLikelihood: RatingLikelihood = RatingLikelihood.ALWAYS,
        deliveryDistance: Int = 27
    ): DeliveryCustomerGroup {
        val group = DeliveryCustomerGroup(
            id,
            size,
            visitingTick,
            customerPreferences,
            preferredTableType,
            restaurantTypes,
            visitingEvenings,
            ratingLikelihood,
            deliveryDistance
        )
        group.setLogger(mockLogger)
        return group
    }

    private fun createGroupSome(
        id: Int = 12,
        size: Int = 2,
        visitingTick: Int = 16,
        customerPreferences: List<FoodPreferences> = emptyList(),
        preferredTableType: TableType? = null,
        restaurantTypes: List<RestaurantType> = listOf(RestaurantType.ASIAN),
        visitingEvenings: List<Int> = listOf(1, 2),
        ratingLikelihood: RatingLikelihood = RatingLikelihood.SOME,
        deliveryDistance: Int = 27
    ): DeliveryCustomerGroup {
        val group = DeliveryCustomerGroup(
            id,
            size,
            visitingTick,
            customerPreferences,
            preferredTableType,
            restaurantTypes,
            visitingEvenings,
            ratingLikelihood,
            deliveryDistance
        )
        group.setLogger(mockLogger)
        val dishes = mutableListOf(
            Dish(
                chickenRice,
                DishStatus.SERVED,
                false
            ),
            Dish(chickenRice, DishStatus.SERVED, false)
        )
        val order = Order(1, dishes, true, 27, 7)
        group.order = order

        return group
    }

    private fun createGroupNever(
        id: Int = 12,
        size: Int = 2,
        visitingTick: Int = 16,
        customerPreferences: List<FoodPreferences> = emptyList(),
        preferredTableType: TableType? = null,
        restaurantTypes: List<RestaurantType> = listOf(RestaurantType.ASIAN),
        visitingEvenings: List<Int> = listOf(1, 2),
        ratingLikelihood: RatingLikelihood = RatingLikelihood.NEVER,
        deliveryDistance: Int = 27
    ): DeliveryCustomerGroup {
        val group = DeliveryCustomerGroup(
            id,
            size,
            visitingTick,
            customerPreferences,
            preferredTableType,
            restaurantTypes,
            visitingEvenings,
            ratingLikelihood,
            deliveryDistance
        )
        group.setLogger(mockLogger)
        val dishes = mutableListOf(
            Dish(
                chickenRice,
                DishStatus.SERVED,
                false
            ),
            Dish(chickenRice, DishStatus.SERVED, false)
        )
        val order = Order(1, dishes, true, 27, 7)
        group.order = order

        return group
    }
    /*
        val group = DeliveryCustomerGroup(
            12,
            2,
            16,
            emptyList(),
            null,
            listOf(RestaurantType.ASIAN),
            listOf(1, 2),
            RatingLikelihood.ALWAYS,
            27
        )
        group.setLogger(mockLogger)

     */

    //     fun ` `() {}
    @Test
    fun `isDelivery returns true`() {
        val group = createGroup()
        assertTrue(group.isDelivery())
    }

    @Test
    fun `is ready to decide in correct tick`() {
        val group = createGroup()
        // visitingTick = 16
        // ceil deliveryDistance/5 = 6
        // 16 - 3 - 6 = 7

        while (Ticks.getTick() < 7) {
            Ticks.advanceTick()
        }
        assertTrue(group.isReadyToDecide())
    }

    @Test
    fun `is not ready to decide in earlier tick`() {
        val group = createGroup()
        // visitingTick = 16
        // ceil deliveryDistance/5 = 6
        // 16 - 3 - 6 = 7

        while (Ticks.getTick() < 6) {
            Ticks.advanceTick()
        }
        assertFalse(group.isReadyToDecide())
    }

    @Test
    fun `is not ready to decide in later tick`() {
        val group = createGroup()
        // visitingTick = 16
        // ceil deliveryDistance/5 = 6
        // 16 - 3 - 6 = 7

        while (Ticks.getTick() < 8) {
            Ticks.advanceTick()
        }
        assertFalse(group.isReadyToDecide())
    }

    // rating for those with rating likelihood always
    @Test
    fun `ALWAYS give Rating 1 when food arrives earlier`() {
        val group = createGroup()
        val dishes = mutableListOf(
            Dish(
                chickenRice,
                DishStatus.SERVED,
                false
            ),
            Dish(chickenRice, DishStatus.SERVED, false)
        )
        val order = Order(1, dishes, true, 27, 7)
        group.order = order
        while (Ticks.getTick() < 14) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(1, group.giveRating())
    }

    @Test
    fun `ALWAYS give Rating 1 when food arrives on time`() {
        val group = createGroup()
        val dishes = mutableListOf(
            Dish(
                chickenRice,
                DishStatus.SERVED,
                false
            ),
            Dish(chickenRice, DishStatus.SERVED, false)
        )
        val order = Order(1, dishes, true, 27, 7)
        group.order = order
        while (Ticks.getTick() < 15) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(1, group.giveRating())
    }

    @Test
    fun `ALWAYS give Rating -1 when food does not arrive on time`() {
        val group = createGroup()
        val dishes = mutableListOf(
            Dish(
                chickenRice,
                DishStatus.SERVED,
                false
            ),
            Dish(chickenRice, DishStatus.SERVED, false)
        )
        val order = Order(1, dishes, true, 27, 7)
        group.order = order
        while (Ticks.getTick() < 19) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(-1, group.giveRating())
    }

    // rating for those with rating likelihood some
    @Test
    fun `SOME give Rating 1 when food arrives earlier`() {
        val group = createGroupSome()
        while (Ticks.getTick() < 14) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(1, group.giveRating())
    }

    // should be 0
    // The exceptions are CASUAL customers with SOME,
    // who will not rate after a neutral experience
    @Test
    fun `SOME give Rating 0 when food arrives on time`() {
        val group = createGroupSome()
        while (Ticks.getTick() < 16) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(0, group.giveRating())
    }

    @Test
    fun `SOME give Rating -1 when food does not arrive on time`() {
        val group = createGroupSome()
        while (Ticks.getTick() < 19) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(-1, group.giveRating())
    }

    // ones with never ratingLikelihood always return 0
    @Test
    fun `Never give Rating 0 when food arrives earlier`() {
        val group = createGroupNever()
        while (Ticks.getTick() < 14) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(0, group.giveRating())
    }

    // should be 0
    // The exceptions are CASUAL customers with SOME,
    // who will not rate after a neutral experience
    @Test
    fun `NEVER give Rating 0 when food arrives on time`() {
        val group = createGroupNever()
        while (Ticks.getTick() < 16) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(0, group.giveRating())
    }

    @Test
    fun `NEVER give Rating -1 when food does not arrive on time`() {
        val group = createGroupNever()
        while (Ticks.getTick() < 19) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        assertEquals(0, group.giveRating())
    }

    // tests for check gave up
    /*@Test
    fun `give up when waiting for food too long`() {
        val group = createGroupSome()
        while (Ticks.getTick() < 21) {
            Ticks.advanceTick()
        }
        assertTrue(group.checkGaveUp())
    }*/

    @Test
    fun `do not give up when waiting for food for less than visitingTick + 3`() {
        val group = createGroupSome()
        while (Ticks.getTick() < 18) {
            Ticks.advanceTick()
        }
        // they give up when the tick is 19.
        // they can wait in 16, 17, 18
        assertFalse(group.checkGaveUp())
    }

    @Test
    fun `do not give up when waiting for food too early`() {
        val group = createGroupSome()
        while (Ticks.getTick() < 12) {
            Ticks.advanceTick()
        }
        assertFalse(group.checkGaveUp())
    }

    // tests for eat
    // they take two ticks to eat the food
    /*@Test
    fun `eat makes a call to the logger when everyone finishes eating`() {
        val group = createGroupSome()
        while (Ticks.getTick() < 16) {
            Ticks.advanceTick()
        }
        group.receiveOrder()
        group.eat()
        verify(mockLogger, never()).deliveryFinishedEating(12)

        group.eat()
        verify(mockLogger).deliveryFinishedEating(12)
    }*/

    // give up case check: logger stuff
    @Test
    fun `giveUpCaseCheck makes the call to the logger when customers give up`() {
        val group = createGroupSome()
        while (Ticks.getTick() < 17) {
            Ticks.advanceTick()
        }
        group.giveUpCaseCheck()
        verify(mockLogger, never()).deliveryGivenUp(12, 1)

        Ticks.advanceTick()
        Ticks.advanceTick()
        group.giveUpCaseCheck()
        verify(mockLogger).deliveryGivenUp(12, 1)
    }
}
