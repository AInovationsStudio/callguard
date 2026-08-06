package studio.ainovations.callguard.ui

import androidx.lifecycle.ViewModel
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.ainovations.callguard.data.CallGuardPreferences
import studio.ainovations.callguard.data.PreferencesRepository
import studio.ainovations.callguard.data.RuleRepository
import studio.ainovations.callguard.data.RuleSnapshot
import studio.ainovations.callguard.domain.BlockingRule
import studio.ainovations.callguard.domain.MatchResult
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.domain.RuleCompiler
import studio.ainovations.callguard.domain.RuleMatcher
import studio.ainovations.callguard.phone.PhoneNormalizationResult
import studio.ainovations.callguard.phone.PhoneNormalizer
import studio.ainovations.callguard.phone.PhoneNumberInput
import java.math.BigInteger
import java.util.UUID

/**
 * Plain-language matcher shapes offered by the guided wizard. Every value
 * maps to exactly one [RuleMatcher] variant; the label is what the UI shows
 * instead of the underlying type name (e.g. "Starts with", never "prefix").
 * [ADVANCED_PATTERN] (regex) is listed last so the guided shapes stay
 * primary and advanced syntax remains available without dominating the flow.
 */
enum class WizardMatcherType(val label: String) {
    EXACT_NUMBER("Exact number"),
    STARTS_WITH("Starts with"),
    ENDS_WITH("Ends with"),
    CONTAINS("Contains"),
    NUMBER_RANGE("Number range"),
    CONTACTS("Anyone in your contacts"),
    SPECIFIC_NUMBERS("A list of specific numbers"),
    ADVANCED_PATTERN("Advanced pattern (regex)"),
}

/** Which wizard text field [CallGuardViewModel.onWizardInputChanged] is updating. */
enum class WizardField {
    NAME,
    RAW_VALUE,
    RANGE_START,
    RANGE_END,
    SPECIFIC_NUMBERS,
    COUNTRY,
    PRIORITY,
    PREVIEW_INPUT,
}

/** The three top-level screens [CallGuardApp] switches between. */
sealed interface CallGuardScreen {
    data object RuleList : CallGuardScreen
    data object Wizard : CallGuardScreen
    data object Settings : CallGuardScreen
}

/** A generated example number, in canonical digits, for the wizard's "this will match" preview. */
data class WizardExample(val digits: String)

/** One-shot UI actions [CallGuardApp] performs (e.g. launching a system settings screen). */
sealed interface CallGuardEvent {
    data object RequestContactsPermission : CallGuardEvent
    data object RequestScreeningRole : CallGuardEvent
}

/** Read-only, non-claiming status of Android's call-screening role. See [CallGuardViewModel] constructor doc. */
sealed interface ScreeningRoleStatus {
    data object Active : ScreeningRoleStatus
    data object NotActive : ScreeningRoleStatus
    data object Unsupported : ScreeningRoleStatus
}

/**
 * All state for the guided rule wizard. Derived fields ([validationError],
 * [broadRegexWarning], [conflictWarnings], [positiveExample], [negativeExample],
 * [needsRegionOptions], [canSave]) are recomputed by
 * [CallGuardViewModel] every time a raw input field, the matcher type, or the
 * action changes; [previewInput]/[previewResult]/[previewError] are set only
 * by [CallGuardViewModel.onPreviewTested] and are left untouched by that
 * recomputation, so a stale preview survives an unrelated field edit exactly
 * as a saved preview would.
 */
data class WizardState(
    val editingRuleId: String? = null,
    val name: String = "",
    val matcherType: WizardMatcherType = WizardMatcherType.STARTS_WITH,
    val rawValue: String = "",
    val rangeStart: String = "",
    val rangeEnd: String = "",
    val specificNumbersRaw: String = "",
    val country: String? = null,
    val needsRegionOptions: List<String> = emptyList(),
    val action: RuleAction = RuleAction.BLOCK,
    val priority: Int = 0,
    val validationError: String? = null,
    val broadRegexWarning: String? = null,
    val conflictWarnings: List<String> = emptyList(),
    val positiveExample: WizardExample? = null,
    val negativeExample: WizardExample? = null,
    val previewInput: String = "",
    val previewResult: MatchResult? = null,
    val previewError: String? = null,
    val previewNotice: String? = null,
    val previewStale: Boolean = false,
    val canSave: Boolean = false,
)

/** A pre-formatted row for [RuleListScreen]; all text is already plain-language. */
data class RuleListItem(
    val id: String,
    val name: String,
    val actionLabel: String,
    val matcherDescription: String,
    val enabled: Boolean,
    val priority: Int,
    val requiresContactsPermission: Boolean,
)

/** All state for [SettingsScreen]. */
data class SettingsUiState(
    val defaultRegion: String? = null,
    val unknownNumberAction: RuleAction = CallGuardPreferences.DEFAULT_UNKNOWN_NUMBER_ACTION,
    val contactMatchingEnabled: Boolean = false,
    val contactsPermissionGranted: Boolean = false,
    val screeningRoleStatus: ScreeningRoleStatus = ScreeningRoleStatus.Unsupported,
)

/** The full screen-state tree [CallGuardApp] renders. */
data class CallGuardUiState(
    val screen: CallGuardScreen = CallGuardScreen.RuleList,
    val rules: List<BlockingRule> = emptyList(),
    val ruleListItems: List<RuleListItem> = emptyList(),
    val wizard: WizardState = WizardState(),
    val settings: SettingsUiState = SettingsUiState(),
    val availableRegions: List<String> = emptyList(),
)

/**
 * Drives every CallGuard screen. Holds no Android [android.content.Context];
 * everything Android-specific (contacts permission, the call-screening role)
 * is injected as a callback, mirroring [PhoneNormalizer]'s own
 * `deviceRegion: () -> String?` pattern so this class stays constructible
 * with fakes in a test.
 *
 * **Never reimplements matching.** Every matcher-shape decision the wizard
 * makes — whether an input is valid, what a rule will and will not match,
 * whether two rules conflict — is answered by compiling a real
 * [BlockingRule] (or rule list) through [RuleCompiler]/[RuleSnapshot] and
 * reading the result, never by a hand-rolled predicate. This is what lets
 * [RuleWizardScreen] and [RulePreviewScreen] stay pure rendering: they only
 * ever display fields already computed here.
 *
 * [screeningRoleStatus] and [contactsPermissionGranted] answer "what is true
 * right now"; neither is ever assumed. A caller that cannot determine the
 * role (API < 29, or no [android.telecom.RoleManager]) must supply
 * [ScreeningRoleStatus.Unsupported], never [ScreeningRoleStatus.Active] —
 * this class never claims screening is active on the caller's behalf.
 */
class CallGuardViewModel(
    private val ruleRepository: RuleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val normalizer: PhoneNormalizer,
    private val contactsPermissionGranted: () -> Boolean = { false },
    private val screeningRoleStatus: () -> ScreeningRoleStatus = { ScreeningRoleStatus.Unsupported },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val afterMutationStateRead: suspend () -> Unit = {},
) : ViewModel() {

    private val mutationMutex = Mutex()
    private val _uiState = MutableStateFlow(
        CallGuardUiState(availableRegions = PhoneNumberUtil.getInstance().supportedRegions.sorted()),
    )
    val uiState: StateFlow<CallGuardUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CallGuardEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CallGuardEvent> = _events.asSharedFlow()

    init {
        // Republishes the persisted snapshot immediately at startup, before any edit, so a
        // caller of RuleRepository.compileSnapshot() (the future screening service) never sees
        // RuleSnapshot.EMPTY across a process restart just because the user hasn't touched a
        // rule yet. observeRules() below reflects the same persisted rows independently — Room's
        // Flow re-queries on collection start — so this does not change what the rule list shows.
        scope.launch { ruleRepository.refreshFromDisk() }
        scope.launch {
            ruleRepository.observeRules().collect { rules -> onRulesChanged(rules) }
        }
        scope.launch {
            preferencesRepository.preferences.collect { prefs -> onPreferencesChanged(prefs) }
        }
        refreshPermissionState()
    }

    private fun onRulesChanged(rules: List<BlockingRule>) {
        _uiState.update { state ->
            state.copy(
                rules = rules,
                ruleListItems = rules.map(::toListItem),
                wizard = recomputeWizard(state.wizard, rules),
            )
        }
    }

    private fun onPreferencesChanged(preferences: CallGuardPreferences) {
        // Enforce the revocation invariant when preferences are applied, not
        // only in refreshPermissionState. On a cold start the persisted
        // preference can emit AFTER refreshPermissionState has already run
        // in init, so a stale contact_matching_enabled=true would silently
        // re-enable matching despite revoked permission. If contacts access
        // is not granted, contact matching is forced false here and the
        // disable is persisted, regardless of prior UI state.
        val contactsGranted = contactsPermissionGranted()
        val effectiveContactMatching = if (contactsGranted) preferences.contactMatchingEnabled else false
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    defaultRegion = preferences.defaultRegion,
                    unknownNumberAction = preferences.unknownNumberAction,
                    contactMatchingEnabled = effectiveContactMatching,
                ),
                wizard = recomputeWizard(state.wizard, state.rules),
            )
        }
        if (!contactsGranted && preferences.contactMatchingEnabled) {
            scope.launch { preferencesRepository.setContactMatchingEnabled(false) }
        }
    }

    /** Re-reads the injected permission/role callbacks. Called on init and whenever Settings opens. */
    fun refreshPermissionState() {
        val contactsGranted = contactsPermissionGranted()
        val previouslyEnabled = _uiState.value.settings.contactMatchingEnabled
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    contactsPermissionGranted = contactsGranted,
                    screeningRoleStatus = screeningRoleStatus(),
                    // Make the disabled condition explicit in UI state: when
                    // contacts access is gone, contact matching is not "on",
                    // even if a stale persisted preference has not yet been
                    // overwritten. This keeps the screening service and the
                    // Settings surface from silently retaining an active
                    // contact-matching preference after revocation.
                    contactMatchingEnabled = if (contactsGranted) state.settings.contactMatchingEnabled else false,
                ),
                wizard = recomputeWizard(state.wizard, state.rules, contactsGranted),
            )
        }
        if (!contactsGranted && previouslyEnabled) {
            scope.launch { preferencesRepository.setContactMatchingEnabled(false) }
        }
    }

    // --- Navigation ---

    fun onRuleListOpened() {
        _uiState.update { it.copy(screen = CallGuardScreen.RuleList) }
    }

    fun onSettingsOpened() {
        refreshPermissionState()
        _uiState.update { it.copy(screen = CallGuardScreen.Settings) }
    }

    /** Opens the wizard blank, or pre-filled to edit [editingRuleId] if it names an existing rule. */
    fun onWizardOpened(editingRuleId: String? = null) {
        val existing = editingRuleId?.let { id -> _uiState.value.rules.find { it.id == id } }
        val base = existing?.let(::wizardStateFromRule) ?: WizardState()
        _uiState.update { state -> state.copy(screen = CallGuardScreen.Wizard, wizard = recomputeWizard(base, state.rules)) }
    }

    fun onWizardCancelled() {
        _uiState.update { it.copy(screen = CallGuardScreen.RuleList, wizard = WizardState()) }
    }

    // --- Wizard editing ---

    fun onWizardInputChanged(field: WizardField, value: String) {
        updateWizard { wizard ->
            when (field) {
                WizardField.NAME -> wizard.copy(name = value)
                WizardField.RAW_VALUE -> wizard.copy(rawValue = value)
                WizardField.RANGE_START -> wizard.copy(rangeStart = value)
                WizardField.RANGE_END -> wizard.copy(rangeEnd = value)
                WizardField.SPECIFIC_NUMBERS -> wizard.copy(specificNumbersRaw = value)
                WizardField.COUNTRY -> wizard.copy(country = value.ifBlank { null })
                WizardField.PRIORITY -> value.toIntOrNull()?.let { wizard.copy(priority = it) } ?: wizard
                WizardField.PREVIEW_INPUT -> wizard.copy(previewInput = value)
            }
        }
    }

    fun onMatcherSelected(matcherType: WizardMatcherType) {
        updateWizard { it.copy(matcherType = matcherType) }
    }

    fun onActionSelected(action: RuleAction) {
        updateWizard { it.copy(action = action) }
    }

    /**
     * Tests [rawNumber] against the rule the wizard currently describes,
     * combined with every other saved rule — "route test input through the
     * same normalizer, snapshot, and evaluator used by the service": this
     * normalizes via [normalizer] exactly as production would, then compiles
     * a real [RuleSnapshot] (the same class the service publishes) over the
     * candidate rule set and reads its [RuleSnapshot.evaluate] result
     * directly. No matcher predicate is evaluated by hand.
     */
    fun onPreviewTested(rawNumber: String) {
        val state = _uiState.value
        val wizard = state.wizard
        val candidate = (validateMatcher(wizard) as? MatcherValidation.Valid)?.let { toCandidateRule(wizard, it.matcher) }
        val effectiveRules = state.rules.filterNot { it.id == wizard.editingRuleId } + listOfNotNull(candidate)

        val updated = if (rawNumber.isBlank()) {
            wizard.copy(
                previewInput = rawNumber,
                previewResult = null,
                previewError = "Enter a number to test.",
                previewNotice = null,
            )
        } else if (candidate?.matcher is RuleMatcher.Contacts) {
            wizard.copy(
                previewInput = rawNumber,
                previewResult = null,
                previewError = null,
                previewNotice = "Contacts are not simulated here. Grant contacts access and " +
                    "verify this rule with a real call.",
                previewStale = false,
            )
        } else {
            runCatching {
                when (val normalized = normalizer.normalize(PhoneNumberInput(rawNumber, wizard.country))) {
                    is PhoneNormalizationResult.Valid -> {
                        require(candidate != null) { wizard.validationError ?: "Finish the rule before testing a number." }
                        RuleSnapshot.compile(effectiveRules).evaluate(normalized.phone.digits, emptySet())
                    }
                    is PhoneNormalizationResult.NeedsRegion -> throw PreviewValidationException(
                        "This number could belong to more than one country; choose a country above and try again.",
                    )
                    is PhoneNormalizationResult.Invalid -> throw PreviewValidationException(
                        "Enter a valid phone number: ${normalized.reason}",
                    )
                }
            }.fold(
                onSuccess = { result ->
                    wizard.copy(
                        previewInput = rawNumber,
                        previewResult = result,
                        previewError = null,
                        previewNotice = null,
                        previewStale = false,
                    )
                },
                onFailure = { error ->
                    wizard.copy(
                        previewInput = rawNumber,
                        previewResult = null,
                        previewError = error.message ?: "Could not test this number.",
                        previewNotice = null,
                        previewStale = false,
                    )
                },
            )
        }
        _uiState.update { it.copy(wizard = updated) }
    }

    /** Saves the wizard's current rule. A no-op if [WizardState.canSave] is false. */
    fun onRuleSaved() {
        scope.launch {
            mutationMutex.withLock {
                val state = _uiState.value
                val wizard = state.wizard
                val matcher = (validateMatcher(wizard) as? MatcherValidation.Valid)?.matcher ?: return@withLock
                if (matcher is RuleMatcher.Contacts && !state.settings.contactsPermissionGranted) return@withLock
                val id = wizard.editingRuleId ?: UUID.randomUUID().toString()
                val newRule = BlockingRule(
                    id = id,
                    name = wizard.name.ifBlank { defaultRuleNameForMatcher(matcher, wizard.action) },
                    enabled = true,
                    action = wizard.action,
                    matcher = matcher,
                    priority = wizard.priority,
                )
                val updatedRules = if (wizard.editingRuleId != null) {
                    state.rules.map { if (it.id == id) newRule else it }
                } else {
                    state.rules + newRule
                }
                afterMutationStateRead()
                ruleRepository.replaceRules(updatedRules)
                _uiState.update { current ->
                    current.copy(
                        rules = updatedRules,
                        ruleListItems = updatedRules.map(::toListItem),
                        screen = CallGuardScreen.RuleList,
                        wizard = WizardState(),
                    )
                }
            }
        }
    }

    fun onRuleToggled(ruleId: String, enabled: Boolean) {
        scope.launch {
            mutationMutex.withLock {
                val updated = _uiState.value.rules.map { if (it.id == ruleId) it.copy(enabled = enabled) else it }
                afterMutationStateRead()
                ruleRepository.replaceRules(updated)
                publishRules(updated)
            }
        }
    }

    fun onRuleDeleted(ruleId: String) {
        scope.launch {
            mutationMutex.withLock {
                val updated = _uiState.value.rules.filterNot { it.id == ruleId }
                afterMutationStateRead()
                ruleRepository.replaceRules(updated)
                publishRules(updated)
            }
        }
    }

    // --- Settings ---

    fun onSettingsDefaultRegionChanged(region: String?) {
        scope.launch { preferencesRepository.setDefaultRegion(region) }
    }

    fun onUnknownNumberActionChanged(action: RuleAction) {
        scope.launch { preferencesRepository.setUnknownNumberAction(action) }
    }

    fun onContactMatchingToggled(enabled: Boolean) {
        scope.launch { preferencesRepository.setContactMatchingEnabled(enabled) }
    }

    /** Explicit repair path for a missing contacts permission: opens the app's system settings page. */
    fun onContactsPermissionRepairRequested() {
        _events.tryEmit(CallGuardEvent.RequestContactsPermission)
    }

    fun onScreeningRoleRequested() {
        if (_uiState.value.settings.screeningRoleStatus == ScreeningRoleStatus.NotActive) {
            _events.tryEmit(CallGuardEvent.RequestScreeningRole)
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    // --- Wizard derivation (validation, examples, conflicts) ---

    private fun updateWizard(transform: (WizardState) -> WizardState) {
        _uiState.update { state ->
            val changed = transform(state.wizard)
            state.copy(
                wizard = recomputeWizard(
                    changed.copy(
                        previewStale = changed.previewStale || changed.previewResult != null,
                        previewNotice = null,
                    ),
                    state.rules,
                ),
            )
        }
    }

    private fun publishRules(rules: List<BlockingRule>) {
        _uiState.update { state ->
            state.copy(rules = rules, ruleListItems = rules.map(::toListItem), wizard = recomputeWizard(state.wizard, rules))
        }
    }

    private fun recomputeWizard(
        wizard: WizardState,
        existingRules: List<BlockingRule>,
        contactsPermissionGranted: Boolean = _uiState.value.settings.contactsPermissionGranted,
    ): WizardState {
        val validation = validateMatcher(wizard)
        val matcher = (validation as? MatcherValidation.Valid)?.matcher
        val error = (validation as? MatcherValidation.Invalid)?.message
        val needsRegion = (validation as? MatcherValidation.NeedsRegion)?.regions.orEmpty()

        val broadWarning = if (wizard.matcherType == WizardMatcherType.ADVANCED_PATTERN) {
            broadRegexWarningFor(wizard.rawValue)
        } else {
            null
        }

        val candidateRule = matcher?.let { toCandidateRule(wizard, it) }
        val (positive, negative) = candidateRule?.let(::generateExamples) ?: (null to null)

        val otherRules = existingRules.filterNot { it.id == wizard.editingRuleId }
        val conflicts = if (candidateRule != null && positive != null) {
            detectConflicts(candidateRule, otherRules, positive.digits)
        } else {
            emptyList()
        }

        return wizard.copy(
            validationError = error,
            broadRegexWarning = broadWarning,
            conflictWarnings = conflicts,
            positiveExample = positive,
            negativeExample = negative,
            needsRegionOptions = needsRegion,
            canSave = candidateRule != null &&
                (
                    candidateRule.matcher !is RuleMatcher.Contacts ||
                        contactsPermissionGranted
                    ),
        )
    }

    private fun toCandidateRule(wizard: WizardState, matcher: RuleMatcher): BlockingRule = BlockingRule(
        id = wizard.editingRuleId ?: PENDING_RULE_ID,
        name = wizard.name.ifBlank { defaultRuleNameForMatcher(matcher, wizard.action) },
        enabled = true,
        action = wizard.action,
        matcher = matcher,
        priority = wizard.priority,
    )

    private sealed interface MatcherValidation {
        data class Valid(val matcher: RuleMatcher) : MatcherValidation
        data class Invalid(val message: String) : MatcherValidation
        data class NeedsRegion(val regions: List<String>) : MatcherValidation
    }

    private fun validateMatcher(wizard: WizardState): MatcherValidation = when (wizard.matcherType) {
        WizardMatcherType.STARTS_WITH -> startsWithMatcher(wizard.rawValue, wizard.country)
        WizardMatcherType.ENDS_WITH -> digitsMatcher(wizard.rawValue, "ends-with value") { RuleMatcher.EndsWith(it) }
        WizardMatcherType.CONTAINS -> digitsMatcher(wizard.rawValue, "contains value") { RuleMatcher.Contains(it) }
        WizardMatcherType.EXACT_NUMBER -> normalizeToExactValidation(wizard.rawValue, wizard.country)
        WizardMatcherType.NUMBER_RANGE -> rangeMatcher(wizard.rangeStart, wizard.rangeEnd)
        WizardMatcherType.CONTACTS -> MatcherValidation.Valid(RuleMatcher.Contacts)
        WizardMatcherType.SPECIFIC_NUMBERS -> specificNumbersMatcher(wizard.specificNumbersRaw, wizard.country)
        WizardMatcherType.ADVANCED_PATTERN -> regexMatcher(wizard.rawValue)
    }

    private fun startsWithMatcher(raw: String, region: String?): MatcherValidation {
        val prefix = normalizer.normalizePrefix(raw, region)
        return if (prefix.isEmpty()) {
            MatcherValidation.Invalid("Enter the starts-with value (digits only).")
        } else {
            MatcherValidation.Valid(RuleMatcher.StartsWith(prefix))
        }
    }

    private fun digitsMatcher(raw: String, label: String, build: (String) -> RuleMatcher): MatcherValidation {
        val digits = raw.filter { it.isDigit() }
        return if (digits.isEmpty()) {
            MatcherValidation.Invalid("Enter the $label (digits only).")
        } else {
            MatcherValidation.Valid(build(digits))
        }
    }

    private fun normalizeToExactValidation(raw: String, country: String?): MatcherValidation {
        if (raw.isBlank()) return MatcherValidation.Invalid("Enter a phone number.")
        return when (val result = normalizer.normalize(PhoneNumberInput(raw, country))) {
            is PhoneNormalizationResult.Valid -> MatcherValidation.Valid(RuleMatcher.Exact(result.phone.digits))
            is PhoneNormalizationResult.NeedsRegion -> MatcherValidation.NeedsRegion(result.regions)
            is PhoneNormalizationResult.Invalid -> MatcherValidation.Invalid(result.reason)
        }
    }

    private fun rangeMatcher(rawStart: String, rawEnd: String): MatcherValidation {
        val start = rawStart.filter { it.isDigit() }
        val end = rawEnd.filter { it.isDigit() }
        if (start.isEmpty() || end.isEmpty()) {
            return MatcherValidation.Invalid("Enter both a start and an end number for the range.")
        }
        val startValue = runCatching { BigInteger(start) }.getOrNull()
        val endValue = runCatching { BigInteger(end) }.getOrNull()
        if (startValue == null || endValue == null) return MatcherValidation.Invalid("Range values must be numeric.")
        if (startValue > endValue) return MatcherValidation.Invalid("The range start must not be greater than the end.")
        return MatcherValidation.Valid(RuleMatcher.Range(start, end))
    }

    private fun regexMatcher(pattern: String): MatcherValidation {
        if (pattern.isBlank()) return MatcherValidation.Invalid("Enter a pattern.")
        val probe = BlockingRule(PENDING_RULE_ID, "probe", enabled = true, RuleAction.BLOCK, RuleMatcher.Regex(pattern), priority = 0)
        return try {
            RuleCompiler.compile(listOf(probe))
            MatcherValidation.Valid(RuleMatcher.Regex(pattern))
        } catch (e: IllegalArgumentException) {
            MatcherValidation.Invalid(e.message ?: "Invalid pattern.")
        }
    }

    private fun specificNumbersMatcher(raw: String, country: String?): MatcherValidation {
        val entries = raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (entries.isEmpty()) return MatcherValidation.Invalid("Add at least one number.")
        val digits = mutableSetOf<String>()
        for (entry in entries) {
            when (val result = normalizer.normalize(PhoneNumberInput(entry, country))) {
                is PhoneNormalizationResult.Valid -> digits += result.phone.digits
                is PhoneNormalizationResult.NeedsRegion -> return MatcherValidation.NeedsRegion(result.regions)
                is PhoneNormalizationResult.Invalid -> {
                    return MatcherValidation.Invalid("'$entry' is not a valid number: ${result.reason}")
                }
            }
        }
        return MatcherValidation.Valid(RuleMatcher.SpecificNumbers(digits))
    }

    /**
     * A pattern that is syntactically valid but matches nearly every number.
     * This is a heuristic allowlist check, not a general "how broad is this
     * regex" analysis; it deliberately covers only the most obvious broad
     * patterns.
     */
    private fun broadRegexWarningFor(pattern: String): String? {
        val trimmed = pattern.trim()
        return if (trimmed.isEmpty() || trimmed in BROAD_REGEX_PATTERNS) {
            "This exact pattern is extremely broad. Consider \"starts with\"/\"ends with\", or a more specific pattern."
        } else {
            null
        }
    }

    /**
     * Detects that [candidate] and an existing enabled rule disagree about
     * one of [candidate]'s own positive examples. Reuses [RuleCompiler]
     * twice — once per other rule alone (does it match this number at all?)
     * and once over the full combined rule set (who actually wins?) — so the
     * conflict message names the real winner under [RuleCompiler]'s
     * priority/specificity precedence, not a guess.
     */
    private fun detectConflicts(candidate: BlockingRule, existingRules: List<BlockingRule>, positiveDigits: String): List<String> {
        val conflicts = mutableListOf<String>()
        for (other in existingRules) {
            if (!other.enabled) continue
            val singleResult = runCatching {
                RuleCompiler.compile(listOf(other)).evaluate(positiveDigits, emptySet())
            }.getOrNull() ?: continue
            val otherMatches = singleResult.ruleId == other.id
            if (otherMatches && other.action != candidate.action) {
                val combined = runCatching {
                    RuleCompiler.compile(existingRules + candidate).evaluate(positiveDigits, emptySet())
                }.getOrNull()
                val outcome = if (combined?.ruleId == candidate.id) {
                    "your new rule wins for this number"
                } else {
                    "'${other.name}' wins for this number"
                }
                conflicts += "Conflicts with '${other.name}' (${actionLabel(other.action)}) " +
                    "for numbers like $positiveDigits — $outcome."
            }
        }
        return conflicts
    }

    private fun generateExamples(rule: BlockingRule): Pair<WizardExample?, WizardExample?> {
        val positive = positiveCandidatesFor(rule.matcher).firstNotNullOfOrNull { verifyExample(rule, it, expectMatch = true) }
        val negative = negativeCandidatesFor(rule.matcher).firstNotNullOfOrNull { verifyExample(rule, it, expectMatch = false) }
        return positive to negative
    }

    /**
     * The only place a generated example is trusted: it is compiled through
     * the real [RuleCompiler] and only accepted if the real evaluator agrees
     * with [expectMatch]. A candidate that does not verify is silently
     * dropped rather than shown as if it were correct.
     */
    private fun verifyExample(rule: BlockingRule, digits: String, expectMatch: Boolean): WizardExample? {
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        val result = runCatching { RuleCompiler.compile(listOf(rule)).evaluate(digits, emptySet()) }.getOrNull() ?: return null
        val matched = result.ruleId == rule.id
        return if (matched == expectMatch) WizardExample(digits) else null
    }

    private fun positiveCandidatesFor(matcher: RuleMatcher): List<String> = when (matcher) {
        is RuleMatcher.Exact -> listOf(matcher.value)
        is RuleMatcher.StartsWith -> listOf(padEnd(matcher.prefix, EXAMPLE_LENGTH), matcher.prefix)
        is RuleMatcher.EndsWith -> listOf(padStart(matcher.suffix, EXAMPLE_LENGTH), matcher.suffix)
        is RuleMatcher.Contains -> listOf("555" + matcher.substring + "555", matcher.substring)
        is RuleMatcher.Range -> listOf(matcher.startInclusive, matcher.endInclusive)
        is RuleMatcher.Contacts -> emptyList()
        is RuleMatcher.SpecificNumbers -> matcher.numbers.toList()
        is RuleMatcher.Regex -> if (matcher.pattern.all { it.isDigit() }) listOf(matcher.pattern) else emptyList()
    }

    private fun negativeCandidatesFor(matcher: RuleMatcher): List<String> = when (matcher) {
        is RuleMatcher.Exact -> listOf(bumpLastDigit(matcher.value))
        is RuleMatcher.StartsWith -> listOf(padEnd(differentLeadingDigit(matcher.prefix), EXAMPLE_LENGTH))
        is RuleMatcher.EndsWith -> listOf(padStart(differentLeadingDigit(matcher.suffix), EXAMPLE_LENGTH))
        is RuleMatcher.Contains -> listOf("2".repeat(EXAMPLE_LENGTH), "7".repeat(EXAMPLE_LENGTH), "13579246801")
        is RuleMatcher.Range -> listOfNotNull(
            runCatching { (BigInteger(matcher.endInclusive) + BigInteger.ONE).toString() }.getOrNull(),
            runCatching {
                (BigInteger(matcher.startInclusive) - BigInteger.ONE).takeIf { it >= BigInteger.ZERO }?.toString()
            }.getOrNull(),
        )
        is RuleMatcher.Contacts -> emptyList()
        is RuleMatcher.SpecificNumbers -> listOf("00000000000", "11111111111", "22222222222").filterNot { it in matcher.numbers }
        is RuleMatcher.Regex -> if (matcher.pattern.all { it.isDigit() }) listOf(bumpLastDigit(matcher.pattern)) else emptyList()
    }

    private fun padEnd(prefix: String, length: Int): String =
        if (prefix.length >= length) prefix else prefix + EXAMPLE_FILLER.repeat(length - prefix.length)

    private fun padStart(suffix: String, length: Int): String =
        if (suffix.length >= length) suffix else EXAMPLE_FILLER.repeat(length - suffix.length) + suffix

    private fun bumpLastDigit(digits: String): String {
        if (digits.isEmpty()) return digits
        val bumped = ((digits.last() - '0' + 1) % 10).toString()
        return digits.dropLast(1) + bumped
    }

    private fun differentLeadingDigit(value: String): String {
        if (value.isEmpty()) return "9"
        val newFirstDigit = (value[0] - '0' + 5) % 10
        return newFirstDigit.toString() + value.substring(1)
    }

    private fun toListItem(rule: BlockingRule) = RuleListItem(
        id = rule.id,
        name = rule.name,
        actionLabel = rule.action.name.lowercase().replaceFirstChar { it.uppercase() },
        matcherDescription = describeMatcher(rule.matcher),
        enabled = rule.enabled,
        priority = rule.priority,
        requiresContactsPermission = rule.matcher is RuleMatcher.Contacts,
    )

    private fun describeMatcher(matcher: RuleMatcher): String = when (matcher) {
        is RuleMatcher.Exact -> "exactly ${matcher.value}"
        is RuleMatcher.StartsWith -> "starts with ${matcher.prefix}"
        is RuleMatcher.EndsWith -> "ends with ${matcher.suffix}"
        is RuleMatcher.Contains -> "contains ${matcher.substring}"
        is RuleMatcher.Range -> "between ${matcher.startInclusive} and ${matcher.endInclusive}"
        is RuleMatcher.Contacts -> "anyone in your contacts"
        is RuleMatcher.SpecificNumbers -> "${matcher.numbers.size} specific number(s)"
        is RuleMatcher.Regex -> "advanced pattern: ${matcher.pattern}"
    }

    private fun actionLabel(action: RuleAction): String =
        action.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun wizardStateFromRule(rule: BlockingRule): WizardState {
        var matcherType = WizardMatcherType.STARTS_WITH
        var rawValue = ""
        var rangeStart = ""
        var rangeEnd = ""
        var specificRaw = ""
        when (val m = rule.matcher) {
            is RuleMatcher.Exact -> {
                matcherType = WizardMatcherType.EXACT_NUMBER
                rawValue = m.value
            }
            is RuleMatcher.StartsWith -> {
                matcherType = WizardMatcherType.STARTS_WITH
                rawValue = m.prefix
            }
            is RuleMatcher.EndsWith -> {
                matcherType = WizardMatcherType.ENDS_WITH
                rawValue = m.suffix
            }
            is RuleMatcher.Contains -> {
                matcherType = WizardMatcherType.CONTAINS
                rawValue = m.substring
            }
            is RuleMatcher.Range -> {
                matcherType = WizardMatcherType.NUMBER_RANGE
                rangeStart = m.startInclusive
                rangeEnd = m.endInclusive
            }
            is RuleMatcher.Contacts -> matcherType = WizardMatcherType.CONTACTS
            is RuleMatcher.SpecificNumbers -> {
                matcherType = WizardMatcherType.SPECIFIC_NUMBERS
                specificRaw = m.numbers.joinToString("\n")
            }
            is RuleMatcher.Regex -> {
                matcherType = WizardMatcherType.ADVANCED_PATTERN
                rawValue = m.pattern
            }
        }
        return WizardState(
            editingRuleId = rule.id,
            name = rule.name,
            matcherType = matcherType,
            rawValue = rawValue,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            specificNumbersRaw = specificRaw,
            action = rule.action,
            priority = rule.priority,
        )
    }

    private companion object {
        const val PENDING_RULE_ID = "__wizard_pending__"
        const val EXAMPLE_LENGTH = 11
        const val EXAMPLE_FILLER = "4"
        val BROAD_REGEX_PATTERNS = setOf(".*", ".+", "\\d*", "\\d+", "[0-9]*", "[0-9]+", "^.*$", "^.+$")
    }

    private class PreviewValidationException(message: String) : IllegalArgumentException(message)
}

internal fun defaultRuleNameForMatcher(matcher: RuleMatcher, action: RuleAction): String =
    "${action.name.lowercase().replaceFirstChar { it.uppercase() }}: ${matcherLabel(matcher)}"

private fun matcherLabel(matcher: RuleMatcher): String = when (matcher) {
    is RuleMatcher.Exact -> "Exact number"
    is RuleMatcher.StartsWith -> "Starts with"
    is RuleMatcher.EndsWith -> "Ends with"
    is RuleMatcher.Contains -> "Contains"
    is RuleMatcher.Range -> "Number range"
    is RuleMatcher.Contacts -> "Anyone in your contacts"
    is RuleMatcher.SpecificNumbers -> "A list of specific numbers"
    is RuleMatcher.Regex -> "Advanced pattern (regex)"
}
