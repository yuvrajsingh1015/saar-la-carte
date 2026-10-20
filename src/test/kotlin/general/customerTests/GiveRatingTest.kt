package general.customerTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType

/**
 * P05: Regular/Event always rate; Delivery uses likelihood like Casual except
 * ALWAYS also rates a NEUTRAL visit.
 */
class GiveRatingTest {

    @Test
    fun regularAlwaysRatesPositiveOrNeutralAsPlusOne() {
        for (experience in listOf(CustomerExperience.POSITIVE, CustomerExperience.NEUTRAL)) {
            val group = regular()
            group.lastExperience = true
            setExperience(group, experience)
            assertEquals(1, group.giveRating())
            assertFalse(group.lastExperience)
        }
    }

    @Test
    fun regularNegativeRatesMinusOneAndRemembersIt() {
        val group = regular()
        setExperience(group, CustomerExperience.NEGATIVE)
        assertEquals(-1, group.giveRating())
        assertTrue(group.lastExperience)
        assertFalse(group.quitVisiting)
    }

    @Test
    fun regularSecondConsecutiveNegativeStillRatesMinusOne() {
        val group = regular()
        setExperience(group, CustomerExperience.NEGATIVE)
        assertEquals(-1, group.giveRating())
        setExperience(group, CustomerExperience.NEGATIVE)
        assertEquals(-1, group.giveRating())
        assertTrue(group.lastExperience)
    }

    @Test
    fun eventAlwaysRatesPlusOneUnlessNegative() {
        val expected = mapOf(
            CustomerExperience.POSITIVE to 1,
            CustomerExperience.NEUTRAL to 1,
            CustomerExperience.NEGATIVE to -1
        )
        for ((experience, rating) in expected) {
            val group = event()
            setExperience(group, experience)
            assertEquals(rating, group.giveRating(), experience.name)
        }
    }

    @Test
    fun deliveryNeverNeverRates() {
        for (experience in CustomerExperience.entries) {
            val group = delivery(RatingLikelihood.NEVER)
            setExperience(group, experience)
            assertEquals(0, group.giveRating(), experience.name)
        }
    }

    @Test
    fun deliverySomeRatesOnlyNonNeutral() {
        val expected = mapOf(
            CustomerExperience.POSITIVE to 1,
            CustomerExperience.NEUTRAL to 0,
            CustomerExperience.NEGATIVE to -1
        )
        for ((experience, rating) in expected) {
            val group = delivery(RatingLikelihood.SOME)
            setExperience(group, experience)
            assertEquals(rating, group.giveRating(), experience.name)
        }
    }

    @Test
    fun deliveryAlwaysRatesNeutralAsPlusOne() {
        val expected = mapOf(
            CustomerExperience.POSITIVE to 1,
            CustomerExperience.NEUTRAL to 1,
            CustomerExperience.NEGATIVE to -1
        )
        for ((experience, rating) in expected) {
            val group = delivery(RatingLikelihood.ALWAYS)
            setExperience(group, experience)
            assertEquals(rating, group.giveRating(), experience.name)
        }
    }

    @Test
    fun casualSomeNeutralStillDoesNotRate() {
        val group = CasualCustomerGroup(
            1,
            1,
            1,
            emptyList(),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.SOME
        )
        setExperience(group, CustomerExperience.NEUTRAL)
        assertEquals(0, group.giveRating())
    }

    private fun regular() = RegularCustomerGroup(
        1,
        1,
        1,
        emptyList(),
        TableType.COMMON,
        1,
        1,
        1
    )

    private fun event() = EventCustomerGroup(
        1,
        1,
        1,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        1,
        mutableMapOf(RestaurantType.ASIAN to "Soup")
    )

    private fun delivery(likelihood: RatingLikelihood) = DeliveryCustomerGroup(
        1,
        1,
        1,
        emptyList(),
        TableType.COMMON,
        listOf(RestaurantType.ASIAN),
        listOf(1),
        likelihood,
        1
    )
}

private fun setExperience(group: CustomerGroup, experience: CustomerExperience) {
    val field = CustomerGroup::class.java.getDeclaredField("customerExperience")
    field.isAccessible = true
    field.set(group, experience)
}
