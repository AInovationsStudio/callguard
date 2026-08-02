package studio.ainovations.callguard.data

import studio.ainovations.callguard.domain.BlockingRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference

/**
 * Persists [BlockingRule]s via [RuleDao] and publishes an atomically
 * swapped, fully compiled [RuleSnapshot] for the screening hot path.
 *
 * [replaceRules] validates and compiles the new rule set BEFORE writing
 * anything to disk: [RuleSnapshot.compile] throws on an invalid enabled
 * matcher, so an invalid rule set touches neither the database nor the
 * published snapshot. The database write happens in a single [RuleDao.replaceAll]
 * transaction, and the in-memory snapshot reference is only swapped after
 * that transaction completes — so [compileSnapshot] always returns either
 * the previous fully-compiled snapshot or the new fully-compiled snapshot,
 * never a partially applied rule set.
 */
class RuleRepository(
    private val ruleDao: RuleDao,
    initialSnapshot: RuleSnapshot = RuleSnapshot.EMPTY,
) {

    private val snapshotRef = AtomicReference(initialSnapshot)

    /** Observes persisted rules in stable declaration order. */
    fun observeRules(): Flow<List<BlockingRule>> =
        ruleDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Validate, compile, persist, and atomically publish [rules] as the new
     * snapshot. A reference to a snapshot obtained via [compileSnapshot]
     * before this call keeps evaluating against the prior rules; only a
     * fresh [compileSnapshot] call after this returns sees [rules].
     *
     * @throws IllegalArgumentException if any enabled rule is invalid. On
     *   throw, neither the database nor the published snapshot changes.
     */
    suspend fun replaceRules(rules: List<BlockingRule>) {
        val snapshot = RuleSnapshot.compile(rules)
        ruleDao.replaceAll(rules.mapIndexed { index, rule -> rule.toEntity(position = index) })
        snapshotRef.set(snapshot)
    }

    /** Returns the currently published, fully compiled snapshot. */
    fun compileSnapshot(): RuleSnapshot = snapshotRef.get()

    /**
     * Loads the persisted rule set from disk and republishes it as the
     * current snapshot. Intended to be called once at process start (e.g.
     * from `Application.onCreate`) so a freshly started process serves the
     * last persisted rules instead of [RuleSnapshot.EMPTY] until the first
     * [replaceRules] call.
     *
     * This method is not part of the interface the persistence layer brief requires
     * ([observeRules], [replaceRules], [compileSnapshot]); it exists so a
     * real application can recover persisted rules across a process
     * restart. A caller that always calls [replaceRules] before the first
     * [compileSnapshot] read (e.g. a test) can ignore it.
     */
    suspend fun refreshFromDisk() {
        snapshotRef.set(RuleSnapshot.compile(ruleDao.getAllOnce().map { it.toDomain() }))
    }
}
