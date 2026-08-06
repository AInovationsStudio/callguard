package studio.ainovations.callguard.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.ainovations.callguard.data.CallGuardPreferences
import studio.ainovations.callguard.data.RuleSnapshot
import studio.ainovations.callguard.domain.RuleAction

class ScreeningRuntimeStateTest {
    @Test
    fun initialStateIsNotLoaded() {
        val state = ScreeningRuntimeState()

        assertFalse(state.current().loaded)
        assertNull(state.current().initializationError)
    }

    @Test
    fun bootstrapPublishesRulesAndPreferencesAsLoadedState() {
        val state = ScreeningRuntimeState()
        val preferences = CallGuardPreferences(
            defaultRegion = "US",
            unknownNumberAction = RuleAction.BLOCK,
            contactMatchingEnabled = false,
        )

        state.publishInitial(
            rules = RuleSnapshot.EMPTY,
            preferences = preferences,
        )

        assertTrue(state.current().loaded)
        assertEquals(RuleSnapshot.EMPTY, state.current().rules)
        assertEquals(preferences, state.current().preferences)
        assertNull(state.current().initializationError)
    }

    @Test
    fun bootstrapFailureRemainsExplicitlyNotLoaded() {
        val state = ScreeningRuntimeState()

        state.publishInitializationFailure(IllegalStateException::class.java)

        assertFalse(state.current().loaded)
        assertEquals(
            IllegalStateException::class.java.name,
            state.current().initializationError,
        )
    }
}
