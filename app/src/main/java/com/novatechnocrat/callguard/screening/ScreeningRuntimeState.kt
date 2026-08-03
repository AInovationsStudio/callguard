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
)

class ScreeningRuntimeState(
    initial: ScreeningRuntimeSnapshot = ScreeningRuntimeSnapshot(),
) {
    private val state = AtomicReference(initial)

    fun current(): ScreeningRuntimeSnapshot = state.get()

    fun publishRules(snapshot: RuleSnapshot) {
        state.updateAndGet { it.copy(rules = snapshot) }
    }

    fun publishPreferences(preferences: CallGuardPreferences) {
        state.updateAndGet { it.copy(preferences = preferences) }
    }

    fun publishContacts(contacts: ContactCacheSnapshot) {
        state.updateAndGet { it.copy(contacts = contacts) }
    }
}
