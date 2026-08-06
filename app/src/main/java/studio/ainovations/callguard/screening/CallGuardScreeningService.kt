package studio.ainovations.callguard.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import studio.ainovations.callguard.BuildConfig
import studio.ainovations.callguard.data.CallGuardRepositoryProvider
import studio.ainovations.callguard.data.PreferencesRepository
import studio.ainovations.callguard.data.RuleRepository
import studio.ainovations.callguard.data.RuleSnapshot
import studio.ainovations.callguard.data.callGuardDataStore
import studio.ainovations.callguard.domain.MatchResult
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.phone.PhoneNormalizer
import java.util.concurrent.atomic.AtomicLong

class CallGuardScreeningService : CallScreeningService() {
    private lateinit var ruleRepository: RuleRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var decisionResolver: ScreeningDecisionResolver
    private lateinit var contactNumberCache: ContactNumberCache
    private lateinit var runtimeState: ScreeningRuntimeState
    private lateinit var serviceScope: CoroutineScope
    private val contactRefreshMutex = Mutex()
    private val lastContactRefreshAt = AtomicLong(0L)

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext
        val normalizer = PhoneNormalizer(deviceRegion = { deviceRegionFor(context) })
        ruleRepository = CallGuardRepositoryProvider.rules(context)
        preferencesRepository = PreferencesRepository(context.callGuardDataStore)
        decisionResolver = ScreeningDecisionResolver(normalizer)
        contactNumberCache = ContactNumberCache(
            AndroidContactNumberProvider(
                contentResolver = contentResolver,
                normalizer = normalizer,
            ),
        )
        runtimeState = ScreeningRuntimeState()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // Bootstrap must publish one coherent initial snapshot BEFORE the Room
        // and DataStore observers start replacing state. Launching them
        // concurrently lets an observer emission land first and then be
        // overwritten by the bootstrap snapshot — leaving the runtime serving
        // stale rules until the next emission. Serializing bootstrap ahead
        // of the observers guarantees the initial snapshot is coherent and
        // that a completed bootstrap is never followed by a stale
        // loaded=false state. A failed bootstrap still publishes an explicit
        // loaded=false (fail-open) before the observers begin.
        //
        // The two observers are launched inside a supervisorScope so their
        // failure domains stay independent: an uncaught exception in the Room
        // observer does not cancel the DataStore observer (or vice versa),
        // which a plain sibling `launch` under the outer coroutine would do.
        // Bootstrap runs to completion first, then the supervisor scope
        // starts; the supervisor scope itself does not return until both
        // observers complete, so the outer coroutine stays alive for the
        // lifetime of the observers.
        serviceScope.launch {
            bootstrapRuntimeState()
            supervisorScope {
                launch {
                    ruleRepository.observeRules().collectLatest { rules ->
                        runCatching { RuleSnapshot.compile(rules) }
                            .onSuccess { runtimeState.publishRules(it) }
                            .onFailure { debugLog(ScreeningDiagnostics.failure(it)) }
                        refreshContactsIfNeeded(runtimeState.current().preferences.defaultRegion, force = true)
                    }
                }
                launch {
                    preferencesRepository.preferences.collectLatest { preferences ->
                        runtimeState.publishPreferences(preferences)
                        refreshContactsIfNeeded(preferences.defaultRegion, force = true)
                    }
                }
            }
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val state = runtimeState.current()
        debugLog(ScreeningDiagnostics.entry(state.loaded, state.rules.ruleCount))
        refreshContactsIfNeeded(state.preferences.defaultRegion)
        val result = resolveCallSafely {
            decisionResolver.resolve(
                snapshot = state.rules,
                rawNumber = callDetails.handle?.schemeSpecificPart,
                region = state.preferences.defaultRegion,
                preferences = state.preferences,
                contacts = state.contacts.numbers,
                contactsAvailable = state.contacts.available,
                runtimeLoaded = state.loaded,
            )
        }
        debugLog(
            ScreeningDiagnostics.decision(
                loaded = state.loaded,
                ruleCount = state.rules.ruleCount,
                action = result.action,
                ruleId = result.ruleId,
            ),
        )
        respondToCall(callDetails, result.toCallResponse())
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun bootstrapRuntimeState() {
        try {
            withTimeout(BOOTSTRAP_TIMEOUT_MS) {
                val rules = ruleRepository.bootstrapSnapshot()
                val preferences = preferencesRepository.bootstrapPreferences()
                runtimeState.publishInitial(rules, preferences)
            }
            val state = runtimeState.current()
            debugLog(ScreeningDiagnostics.bootstrap(state.loaded, state.rules.ruleCount))
        } catch (error: Throwable) {
            runtimeState.publishInitializationFailure(error::class.java)
            debugLog(ScreeningDiagnostics.failure(error))
        }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun refreshContactsIfNeeded(region: String?, force: Boolean = false) {
        val state = runtimeState.current()
        if (!state.preferences.contactMatchingEnabled || !state.rules.hasContactRules) return
        val now = System.currentTimeMillis()
        val lastRefresh = lastContactRefreshAt.get()
        if (!force && now - lastRefresh < CONTACT_REFRESH_INTERVAL_MS) return
        if (!lastContactRefreshAt.compareAndSet(lastRefresh, now)) return
        serviceScope.launch {
            contactRefreshMutex.withLock {
                contactNumberCache.refresh(region)
                runtimeState.publishContacts(contactNumberCache.current())
            }
        }
    }

    private fun MatchResult.toCallResponse(): CallResponse =
        CallResponse.Builder().apply {
            when (action) {
                RuleAction.BLOCK -> {
                    setDisallowCall(true)
                    setRejectCall(true)
                    setSkipNotification(true)
                }
                RuleAction.SILENCE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setSilenceCall(true)
                }
                RuleAction.ALLOW -> Unit
            }
        }.build()

    private companion object {
        const val CONTACT_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        const val BOOTSTRAP_TIMEOUT_MS = 2_000L
        const val TAG = "CallGuardScreening"
    }
}

/**
 * Evaluates [resolve] against the screening callback and fails open with
 * [RuleAction.ALLOW] on any unexpected exception.
 *
 * The configured unknown-number action is reserved for unavailable or
 * unparseable caller identity (see [ScreeningDecisionResolver]); an
 * unexpected internal error must never apply a blocking fallback, because a
 * crash in resolution must preserve the phone's normal behavior rather than
 * silently drop a call. The diagnostic explanation is deliberately
 * privacy-safe: it carries no number, contact, rule, or exception detail.
 *
 * Extracted to a top-level internal function so the fail-open policy is
 * unit-testable without an Android [CallScreeningService] harness.
 */
internal fun resolveCallSafely(resolve: () -> MatchResult): MatchResult =
    runCatching { resolve() }.getOrElse {
        MatchResult(
            action = RuleAction.ALLOW,
            ruleId = null,
            explanation = "Screening encountered an internal error; the call was allowed " +
                "to preserve the phone's normal behavior.",
        )
    }
