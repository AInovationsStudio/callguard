package studio.ainovations.callguard.screening

import studio.ainovations.callguard.data.CallGuardPreferences
import studio.ainovations.callguard.data.RuleSnapshot
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable state read by the screening callback.
 *
 * Room, DataStore, and ContactsProvider refreshes publish complete replacements
 * from background work. [current] is a single atomic read, so incoming calls
 * never perform disk, contacts, or regex-compilation work.
 */
data class ScreeningRuntimeSnapshot(
    val rules: RuleSnapshot = RuleSnapshot.EMPTY,
    val preferences: CallGuardPreferences = CallGuardPreferences(
        defaultRegion = null,
        unknownNumberAction = CallGuardPreferences.DEFAULT_UNKNOWN_NUMBER_ACTION,
        contactMatchingEnabled = false,
    ),
    val contacts: ContactCacheSnapshot = ContactCacheSnapshot(),
    val loaded: Boolean = false,
    val initializationError: String? = null,
)

class ScreeningRuntimeState(
    initial: ScreeningRuntimeSnapshot = ScreeningRuntimeSnapshot(),
) {
    private val state = AtomicReference(initial)

    fun current(): ScreeningRuntimeSnapshot = state.get()

    /**
     * Publish a compiled rule snapshot from the Room observer. Observer
     * publishes never mark initialization complete or incomplete: they
     * preserve both [ScreeningRuntimeSnapshot.loaded] and
     * [ScreeningRuntimeSnapshot.initializationError]. Only
     * [publishInitial] may set `loaded = true` and only
     * [publishInitializationFailure] may set `loaded = false`.
     */
    fun publishRules(snapshot: RuleSnapshot) {
        state.updateAndGet { it.copy(rules = snapshot) }
    }

    /**
     * Publish preferences from the DataStore observer. Observer publishes
     * never mark initialization complete or incomplete: they preserve both
     * [ScreeningRuntimeSnapshot.loaded] and
     * [ScreeningRuntimeSnapshot.initializationError]. Only
     * [publishInitial] may set `loaded = true` and only
     * [publishInitializationFailure] may set `loaded = false`.
     */
    fun publishPreferences(preferences: CallGuardPreferences) {
        state.updateAndGet { it.copy(preferences = preferences) }
    }

    /**
     * Publish a refreshed contact cache from the contacts provider. Observer
     * publishes never mark initialization complete or incomplete: they
     * preserve both [ScreeningRuntimeSnapshot.loaded] and
     * [ScreeningRuntimeSnapshot.initializationError].
     */
    fun publishContacts(contacts: ContactCacheSnapshot) {
        state.updateAndGet { it.copy(contacts = contacts) }
    }

    /**
     * Publish the coherent initial snapshot assembled during bootstrap. This
     * is the only way to set `loaded = true`; it also clears any prior
     * initialization error. The screening service must complete this before
     * starting its Room/DataStore observers so an observer emission can never
     * be overwritten by a stale bootstrap snapshot.
     */
    fun publishInitial(rules: RuleSnapshot, preferences: CallGuardPreferences) {
        state.updateAndGet {
            it.copy(
                rules = rules,
                preferences = preferences,
                loaded = true,
                initializationError = null,
            )
        }
    }

    /**
     * Record that bootstrap failed and keep the runtime explicitly unloaded
     * so the screening callback fails open (`ALLOW`) until the next
     * [publishInitial]. Observer publishes after this preserve `loaded =
     * false` and the recorded error, so a failed bootstrap stays fail-open
     * even as rules/preferences/contacts arrive.
     */
    fun publishInitializationFailure(exceptionClass: Class<out Throwable>) {
        state.updateAndGet {
            it.copy(
                loaded = false,
                initializationError = exceptionClass.name,
            )
        }
    }
}
