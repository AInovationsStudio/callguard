package studio.ainovations.callguard.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.ainovations.callguard.data.CallGuardPreferences
import studio.ainovations.callguard.data.RuleSnapshot
import studio.ainovations.callguard.domain.BlockingRule
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.domain.RuleMatcher

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

    @Test
    fun publishingRulesAloneCannotMarkInitializationComplete() {
        val state = ScreeningRuntimeState()
        val rules = RuleSnapshot.compile(
            listOf(
                BlockingRule(
                    id = "prefix-block",
                    name = "Block 1571",
                    enabled = true,
                    action = RuleAction.BLOCK,
                    matcher = RuleMatcher.StartsWith("1571"),
                    priority = 0,
                ),
            ),
        )

        state.publishRules(rules)

        assertFalse(
            "Observer rule publishes must never flip loaded to true",
            state.current().loaded,
        )
        assertEquals(rules, state.current().rules)
        assertNull(state.current().initializationError)
    }

    @Test
    fun publishingPreferencesAloneCannotMarkInitializationComplete() {
        val state = ScreeningRuntimeState()
        val preferences = CallGuardPreferences(
            defaultRegion = "US",
            unknownNumberAction = RuleAction.BLOCK,
            contactMatchingEnabled = true,
        )

        state.publishPreferences(preferences)

        assertFalse(
            "Observer preference publishes must never flip loaded to true",
            state.current().loaded,
        )
        assertEquals(preferences, state.current().preferences)
        assertNull(state.current().initializationError)
    }

    @Test
    fun publishingContactsAloneCannotMarkInitializationComplete() {
        val state = ScreeningRuntimeState()
        val contacts = ContactCacheSnapshot(
            available = true,
            region = "US",
            numbers = setOf("15718881234"),
        )

        state.publishContacts(contacts)

        assertFalse(
            "Observer contact publishes must never flip loaded to true",
            state.current().loaded,
        )
        assertEquals(contacts, state.current().contacts)
        assertNull(state.current().initializationError)
    }

    @Test
    fun completedInitialSnapshotRemainsLoadedWhenObserversPublishLater() {
        val state = ScreeningRuntimeState()
        state.publishInitial(
            rules = RuleSnapshot.EMPTY,
            preferences = CallGuardPreferences(
                defaultRegion = null,
                unknownNumberAction = CallGuardPreferences.DEFAULT_UNKNOWN_NUMBER_ACTION,
                contactMatchingEnabled = false,
            ),
        )
        assertTrue(state.current().loaded)

        val observerRules = RuleSnapshot.compile(
            listOf(
                BlockingRule(
                    id = "prefix-block",
                    name = "Block 1571",
                    enabled = true,
                    action = RuleAction.BLOCK,
                    matcher = RuleMatcher.StartsWith("1571"),
                    priority = 0,
                ),
            ),
        )
        val observerPreferences = CallGuardPreferences(
            defaultRegion = "US",
            unknownNumberAction = RuleAction.BLOCK,
            contactMatchingEnabled = true,
        )
        val observerContacts = ContactCacheSnapshot(
            available = true,
            region = "US",
            numbers = setOf("15718881234"),
        )

        state.publishRules(observerRules)
        assertTrue(
            "Rule publish after bootstrap must preserve loaded=true",
            state.current().loaded,
        )
        assertEquals(observerRules, state.current().rules)

        state.publishPreferences(observerPreferences)
        assertTrue(
            "Preference publish after bootstrap must preserve loaded=true",
            state.current().loaded,
        )
        assertEquals(observerPreferences, state.current().preferences)

        state.publishContacts(observerContacts)
        assertTrue(
            "Contact publish after bootstrap must preserve loaded=true",
            state.current().loaded,
        )
        assertEquals(observerContacts, state.current().contacts)

        assertNull(
            "Observer publishes must not clear the bootstrap error marker",
            state.current().initializationError,
        )
    }

    @Test
    fun failedBootstrapRemainsExplicitlyNotLoadedWhenObserversPublishLater() {
        val state = ScreeningRuntimeState()
        state.publishInitializationFailure(IllegalStateException::class.java)
        assertFalse(state.current().loaded)

        state.publishRules(RuleSnapshot.EMPTY)
        state.publishPreferences(
            CallGuardPreferences(
                defaultRegion = "US",
                unknownNumberAction = RuleAction.BLOCK,
                contactMatchingEnabled = true,
            ),
        )
        state.publishContacts(
            ContactCacheSnapshot(available = true, region = "US", numbers = setOf("15718881234")),
        )

        assertFalse(
            "Observer publishes after a failed bootstrap must stay fail-open (loaded=false)",
            state.current().loaded,
        )
        assertEquals(
            "Observer publishes must not clear the recorded initialization error",
            IllegalStateException::class.java.name,
            state.current().initializationError,
        )
    }

    @Test
    fun bootstrapPublishOverwritesEarlierObserverState() {
        val state = ScreeningRuntimeState()

        val observerRules = RuleSnapshot.compile(
            listOf(
                BlockingRule(
                    id = "observer-rule",
                    name = "Observer block",
                    enabled = true,
                    action = RuleAction.BLOCK,
                    matcher = RuleMatcher.StartsWith("1571"),
                    priority = 0,
                ),
            ),
        )
        val bootstrapRules = RuleSnapshot.compile(
            listOf(
                BlockingRule(
                    id = "bootstrap-rule",
                    name = "Bootstrap block",
                    enabled = true,
                    action = RuleAction.BLOCK,
                    matcher = RuleMatcher.Exact("15718881234"),
                    priority = 0,
                ),
            ),
        )

        // An observer publish that lands BEFORE bootstrap completes leaves the
        // runtime unloaded (fail-open) and is then overwritten by the bootstrap
        // snapshot. This race is why the service must complete bootstrap before
        // starting observers; see CallGuardScreeningService.onCreate.
        state.publishRules(observerRules)
        assertFalse(state.current().loaded)
        assertEquals(observerRules, state.current().rules)

        state.publishInitial(
            rules = bootstrapRules,
            preferences = CallGuardPreferences(
                defaultRegion = null,
                unknownNumberAction = CallGuardPreferences.DEFAULT_UNKNOWN_NUMBER_ACTION,
                contactMatchingEnabled = false,
            ),
        )

        assertTrue(state.current().loaded)
        assertEquals(
            "Bootstrap snapshot supersedes a stale observer publish when it lands second",
            bootstrapRules,
            state.current().rules,
        )
    }
}
