package general.customerTests

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.restaurant.Restaurant
import saarlacarte.simulation.BrowsingService
import saarlacarte.simulation.Ticks

class CasualCustomerGroupTest {

    @BeforeEach
    fun resetTicks() {
        Ticks.init(50)
    }

    @AfterEach
    fun clearTicks() {
        Ticks.init(50)
    }

    private fun casualGroup(
        size: Int = 2,
        visitingTick: Int = 5,
        preferredTableType: TableType? = TableType.COMMON,
        restaurantTypes: List<RestaurantType> = listOf(RestaurantType.ASIAN),
        visitingEvenings: List<Int> = listOf(1),
        ratingLikelihood: RatingLikelihood = RatingLikelihood.ALWAYS
    ): CasualCustomerGroup {
        return CasualCustomerGroup(
            1,
            size,
            visitingTick,
            emptyList(),
            preferredTableType,
            restaurantTypes,
            visitingEvenings,
            ratingLikelihood
        )
    }

    // no public setter for customerExperience, so we reach in like SimulationTest does for Ticks
    private fun setExperience(group: CasualCustomerGroup, experience: CustomerExperience) {
        val field = CustomerGroup::class.java.getDeclaredField("customerExperience")
        field.isAccessible = true
        field.set(group, experience)
    }

    @Test
    fun isVisitingTrueOnlyOnListedEvenings() {
        val group = casualGroup(visitingEvenings = listOf(2, 4))
        Ticks.advanceEvening() // evening 1, not listed
        assertFalse(group.isVisiting())
        Ticks.advanceEvening() // evening 2, listed
        assertTrue(group.isVisiting())
        Ticks.advanceEvening() // evening 3, not listed
        assertFalse(group.isVisiting())
    }

    @Test
    fun giveRatingMatrix() {
        val never = casualGroup(ratingLikelihood = RatingLikelihood.NEVER)
        val some = casualGroup(ratingLikelihood = RatingLikelihood.SOME)
        val always = casualGroup(ratingLikelihood = RatingLikelihood.ALWAYS)

        for (group in listOf(never, some, always)) {
            setExperience(group, CustomerExperience.POSITIVE)
        }
        assertEquals(0, never.giveRating())
        assertEquals(1, some.giveRating())
        assertEquals(1, always.giveRating())

        for (group in listOf(never, some, always)) {
            setExperience(group, CustomerExperience.NEUTRAL)
        }
        assertEquals(0, never.giveRating())
        assertEquals(0, some.giveRating()) // spec exception: SOME never rates neutral
        assertEquals(1, always.giveRating())

        for (group in listOf(never, some, always)) {
            setExperience(group, CustomerExperience.NEGATIVE)
        }
        assertEquals(0, never.giveRating())
        assertEquals(-1, some.giveRating())
        assertEquals(-1, always.giveRating())
    }

    @Test
    fun isReadyToDecideExactTickAndVisitingRequired() {
        val group = casualGroup(visitingTick = 5, visitingEvenings = listOf(1))
        Ticks.advanceEvening() // evening 1
        repeat(4) { Ticks.advanceTick() } // tick 4, one early
        assertFalse(group.isReadyToDecide())

        Ticks.advanceTick() // tick 5, right on time
        assertTrue(group.isReadyToDecide())

        Ticks.advanceTick() // tick 6, one late
        assertFalse(group.isReadyToDecide())
    }

    @Test
    fun isReadyToDecideFalseOnRightTickWrongEvening() {
        val notVisiting = casualGroup(visitingTick = 5, visitingEvenings = listOf(2))
        Ticks.advanceEvening() // evening 1, not one of its visiting evenings
        repeat(5) { Ticks.advanceTick() } // tick 5, the right tick
        assertFalse(notVisiting.isReadyToDecide())
    }

    @Test
    fun decideRestaurantReturnsNullWithoutBrowsingService() {
        val group = casualGroup()
        assertNull(group.decideRestaurant())
    }

    @Test
    fun decideRestaurantReturnsNullWhenNoEligibleRestaurants() {
        val group = casualGroup()
        val browsing = mock<BrowsingService>()
        whenever(
            browsing.getEligibleRestaurants(any(), anyOrNull(), any(), any(), any(), any(), any())
        ).thenReturn(emptyList())
        group.setBrowsingService(browsing)

        assertNull(group.decideRestaurant())
    }

    @Test
    fun decideRestaurantPicksHighestRatingDiffAndUpdatesAvailability() {
        val group = casualGroup(size = 4, preferredTableType = TableType.BAR)

        val worse = mock<Restaurant>()
        whenever(worse.id).thenReturn(1)
        whenever(worse.positiveRatings).thenReturn(2)
        whenever(worse.negativeRatings).thenReturn(1) // diff 1

        val better = mock<Restaurant>()
        whenever(better.id).thenReturn(2)
        whenever(better.positiveRatings).thenReturn(5)
        whenever(better.negativeRatings).thenReturn(0) // diff 5

        val browsing = mock<BrowsingService>()
        whenever(
            browsing.getEligibleRestaurants(any(), anyOrNull(), any(), any(), any(), any(), any())
        ).thenReturn(listOf(worse, better))
        group.setBrowsingService(browsing)

        val chosen = group.decideRestaurant()

        assertEquals(better, chosen)
        verify(browsing).updateAvailability(better, 4, TableType.BAR, false)
        // makes sure the group's own state actually gets forwarded, not just a mocked answer
        verify(browsing).getEligibleRestaurants(
            group.restaurantTypes,
            TableType.BAR,
            4,
            group.customerPreferences,
            false,
            false,
            group.visitingTick
        )
    }

    @Test
    fun decideRestaurantTieBreaksOnLowestId() {
        val group = casualGroup()

        val higherId = mock<Restaurant>()
        whenever(higherId.id).thenReturn(9)
        whenever(higherId.positiveRatings).thenReturn(3)
        whenever(higherId.negativeRatings).thenReturn(0) // diff 3

        val lowerId = mock<Restaurant>()
        whenever(lowerId.id).thenReturn(2)
        whenever(lowerId.positiveRatings).thenReturn(3)
        whenever(lowerId.negativeRatings).thenReturn(0) // diff 3, same as above

        val browsing = mock<BrowsingService>()
        whenever(
            browsing.getEligibleRestaurants(any(), anyOrNull(), any(), any(), any(), any(), any())
        ).thenReturn(listOf(higherId, lowerId))
        group.setBrowsingService(browsing)

        // both restaurants tie on rating diff, lowest id should win
        assertEquals(lowerId, group.decideRestaurant())
    }

    @Test
    fun decideRestaurantDefaultsToCommonTableTypeWhenPreferredIsNull() {
        val group = casualGroup(size = 3, preferredTableType = null)

        val restaurant = mock<Restaurant>()
        whenever(restaurant.id).thenReturn(1)
        whenever(restaurant.positiveRatings).thenReturn(0)
        whenever(restaurant.negativeRatings).thenReturn(0)

        val browsing = mock<BrowsingService>()
        whenever(
            browsing.getEligibleRestaurants(any(), anyOrNull(), any(), any(), any(), any(), any())
        ).thenReturn(listOf(restaurant))
        group.setBrowsingService(browsing)

        group.decideRestaurant()

        // null preferredTableType should fall back to COMMON, not stay null
        verify(browsing).updateAvailability(restaurant, 3, TableType.COMMON, false)
    }
}
