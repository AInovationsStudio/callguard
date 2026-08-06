package studio.ainovations.callguard.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.ainovations.callguard.domain.BlockingRule
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
 *
 * **Concurrency:** [replaceRules] and [refreshFromDisk] each perform the same
 * read-(or-write)-then-publish sequence against the same disk state and the
 * same [snapshotRef]. Without a guard, a slow [refreshFromDisk] started
 * before a user edit (e.g. during `Application.onCreate`) could still
 * *publish* after that edit's [replaceRules] call — overwriting the newer,
 * already-persisted edit with the stale rule set the refresh had read
 * earlier. [writeMutex] serializes the two methods' entire
 * read/write-compile-publish bodies against each other so that interleaving
 * is impossible: whichever call's critical section runs second either reads
 * the other's already-committed write (a disk-reading [refreshFromDisk]
 * always sees the fully-applied result of a [replaceRules] that finished
 * first) or is the one being published last (a [replaceRules] that finishes
 * after a [refreshFromDisk] simply supersedes it, which is correct — it's
 * newer). Either order, the final published snapshot is always the most
 * recently *completed* write, never a stale one. [compileSnapshot] itself
 * takes no lock — it is a single [AtomicReference.get] — so the screening
 * hot path stays lock-free after publication.
 */
class RuleRepository(
    private val ruleDao: RuleDao,
    initialSnapshot: RuleSnapshot = RuleSnapshot.EMPTY,
) {

    private val snapshotRef = AtomicReference(initialSnapshot)
    private val writeMutex = Mutex()

    /** Observes persisted rules in stable declaration order. */
    fun observeRules(): Flow<List<BlockingRule>> =
        ruleDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Validate, compile, persist, and atomically publish [rules] as the new
     * snapshot. A reference to a snapshot obtained via [compileSnapshot]
     * before this call keeps evaluating against the prior rules; only a
     * fresh [compileSnapshot] call after this returns sees [rules].
     *
     * Serialized against [refreshFromDisk] via [writeMutex]; see the class
     * doc's Concurrency section for why a concurrent restart-recovery read
     * can never publish a stale snapshot over this edit.
     *
     * @throws IllegalArgumentException if any enabled rule is invalid. On
     *   throw, neither the database nor the published snapshot changes.
     */
    suspend fun replaceRules(rules: List<BlockingRule>) {
        writeMutex.withLock {
            val snapshot = RuleSnapshot.compile(rules)
            ruleDao.replaceAll(rules.mapIndexed { index, rule -> rule.toEntity(position = index) })
            snapshotRef.set(snapshot)
        }
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
     * Serialized against [replaceRules] via [writeMutex]; see the class
     * doc's Concurrency section.
     *
     * This method is not part of the interface the persistence layer brief requires
     * ([observeRules], [replaceRules], [compileSnapshot]); it exists so a
     * real application can recover persisted rules across a process
     * restart. A caller that always calls [replaceRules] before the first
     * [compileSnapshot] read (e.g. a test) can ignore it.
     */
    suspend fun refreshFromDisk(): RuleSnapshot = bootstrapSnapshot()

    /**
     * Loads and publishes the persisted rules for a newly created screening
     * service before its first callback can evaluate the empty snapshot.
     */
    suspend fun bootstrapSnapshot(): RuleSnapshot = writeMutex.withLock {
        val snapshot = RuleSnapshot.compile(ruleDao.getAllOnce().map { it.toDomain() })
        snapshotRef.set(snapshot)
        snapshot
    }
}
