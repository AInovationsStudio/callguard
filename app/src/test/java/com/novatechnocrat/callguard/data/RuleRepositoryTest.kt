package studio.ainovations.callguard.data

import studio.ainovations.callguard.domain.BlockingRule
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.domain.RuleMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RuleRepository] against an in-memory [FakeRuleDao].
 *
 * This suite exercises [RuleRepository]'s persistence and atomic-snapshot
 * contract — the [RuleEntity]/[BlockingRule] mapping, the transactional
 * replace, and the "an evaluator never observes a partially compiled rule
 * set" guarantee — without a real SQLite engine. [RuleDao.replaceAll]'s
 * `@Transaction` annotation and every `@Query`/`@Insert` SQL binding are
 * still validated by Room's annotation processor at compile time (a typo in
 * the SQL or an unsupported column type fails `kaptDebugUnitTestKotlin`);
 * what this suite does not cover is a live SQLite engine actually executing
 * that SQL, which is left to a future Android instrumentation test (see the
 * persistence layer report's concerns).
 */
class RuleRepositoryTest {

    /** An in-memory stand-in for a Room-backed [RuleDao]. */
    private class FakeRuleDao : RuleDao {
        private val state = MutableStateFlow<List<RuleEntity>>(emptyList())

        override fun observeAll() = state

        override suspend fun getAllOnce(): List<RuleEntity> = state.value

        override suspend fun insertAll(entities: List<RuleEntity>) {
            state.value = state.value + entities
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }
    }

    private fun rule(
        id: String,
        action: RuleAction,
        matcher: RuleMatcher,
        priority: Int = 0,
        enabled: Boolean = true,
        name: String = id,
    ): BlockingRule = BlockingRule(id, name, enabled, action, matcher, priority)

    @Test
    fun insertingRulesArePersistedAndObservable() = runBlocking {
        val dao = FakeRuleDao()
        val repo = RuleRepository(dao)

        repo.replaceRules(listOf(rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"))))

        val persisted = dao.getAllOnce()
        assertEquals(1, persisted.size)
        assertEquals("block-prefix", persisted[0].id)
        assertEquals(MatcherType.STARTS_WITH, persisted[0].matcherType)

        val observed = repo.observeRules().first()
        assertEquals(listOf(rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"))), observed)
    }

    @Test
    fun compileSnapshotReflectsInsertedRule() = runBlocking {
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(listOf(rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"))))

        val result = repo.compileSnapshot().evaluate("15718881234", emptySet())
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("block-prefix", result.ruleId)
    }

    @Test
    fun emptySnapshotDefaultsToAllowBeforeAnyRuleIsSaved() {
        val repo = RuleRepository(FakeRuleDao())
        val result = repo.compileSnapshot().evaluate("15718881234", emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertNull(result.ruleId)
    }

    @Test
    fun updatingARuleChangesTheSnapshotButNotAPriorReference() = runBlocking {
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(listOf(rule("r1", RuleAction.BLOCK, RuleMatcher.Exact("15718881234"))))

        // Another evaluation holds a reference to the prior snapshot...
        val staleSnapshot = repo.compileSnapshot()
        assertEquals(RuleAction.BLOCK, staleSnapshot.evaluate("15718881234", emptySet()).action)

        // ...while replaceRules publishes an update (same id, new action).
        repo.replaceRules(listOf(rule("r1", RuleAction.ALLOW, RuleMatcher.Exact("15718881234"))))

        // The stale reference is unaffected by the update:
        assertEquals(RuleAction.BLOCK, staleSnapshot.evaluate("15718881234", emptySet()).action)
        // A fresh read sees the update:
        assertEquals(RuleAction.ALLOW, repo.compileSnapshot().evaluate("15718881234", emptySet()).action)
    }

    @Test
    fun disablingARuleFallsBackToDefaultAllow() = runBlocking {
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(listOf(rule("r1", RuleAction.BLOCK, RuleMatcher.Exact("15718881234"))))
        assertEquals(RuleAction.BLOCK, repo.compileSnapshot().evaluate("15718881234", emptySet()).action)

        repo.replaceRules(listOf(rule("r1", RuleAction.BLOCK, RuleMatcher.Exact("15718881234"), enabled = false)))

        val result = repo.compileSnapshot().evaluate("15718881234", emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertNull(result.ruleId)
    }

    @Test
    fun deletingARuleRemovesItFromPersistenceAndTheSnapshot() = runBlocking {
        val dao = FakeRuleDao()
        val repo = RuleRepository(dao)
        repo.replaceRules(
            listOf(
                rule("r1", RuleAction.BLOCK, RuleMatcher.Exact("15718881234")),
                rule("r2", RuleAction.BLOCK, RuleMatcher.Exact("18005551234")),
            ),
        )

        // r1 is omitted from the replacement: it is deleted.
        repo.replaceRules(listOf(rule("r2", RuleAction.BLOCK, RuleMatcher.Exact("18005551234"))))

        assertEquals(1, dao.getAllOnce().size)
        assertEquals("r2", dao.getAllOnce()[0].id)
        assertNull(repo.compileSnapshot().evaluate("15718881234", emptySet()).ruleId)
        assertEquals("r2", repo.compileSnapshot().evaluate("18005551234", emptySet()).ruleId)
    }

    @Test
    fun observeRulesAndTieBreakPreserveDeclarationOrder() = runBlocking {
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(
            listOf(
                rule("first-block", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888")),
                rule("second-allow", RuleAction.ALLOW, RuleMatcher.StartsWith("1571888")),
            ),
        )

        assertEquals(listOf("first-block", "second-allow"), repo.observeRules().first().map { it.id })
        // Equal priority + specificity: declaration order breaks the tie.
        assertEquals("first-block", repo.compileSnapshot().evaluate("15718881234", emptySet()).ruleId)

        // Reordering (a full replacement with the same ids in a new order)
        // changes both the observed order and the tie-break winner.
        repo.replaceRules(
            listOf(
                rule("second-allow", RuleAction.ALLOW, RuleMatcher.StartsWith("1571888")),
                rule("first-block", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888")),
            ),
        )
        assertEquals(listOf("second-allow", "first-block"), repo.observeRules().first().map { it.id })
        assertEquals("second-allow", repo.compileSnapshot().evaluate("15718881234", emptySet()).ruleId)
    }

    @Test
    fun replaceRulesValidatesBeforeTouchingPersistenceOrTheSnapshot() = runBlocking {
        val dao = FakeRuleDao()
        val repo = RuleRepository(dao)
        repo.replaceRules(listOf(rule("good", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"))))
        val publishedBeforeFailure = repo.compileSnapshot()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.replaceRules(listOf(rule("bad", RuleAction.BLOCK, RuleMatcher.StartsWith(""))))
            }
        }

        // Neither the database nor the published snapshot changed.
        assertEquals(1, dao.getAllOnce().size)
        assertEquals("good", dao.getAllOnce()[0].id)
        assertSame(publishedBeforeFailure, repo.compileSnapshot())
    }

    @Test
    fun contactsMatcherRoundTripsThroughPersistence() = runBlocking {
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(listOf(rule("allow-contacts", RuleAction.ALLOW, RuleMatcher.Contacts)))

        assertEquals(
            RuleAction.ALLOW,
            repo.compileSnapshot().evaluate("15718881234", setOf("15718881234")).action,
        )
        assertEquals(RuleAction.ALLOW, repo.compileSnapshot().evaluate("19999999999", emptySet()).action)
        assertNull(repo.compileSnapshot().evaluate("19999999999", emptySet()).ruleId)
    }

    @Test
    fun specificNumbersMatcherRoundTripsThroughPersistence() = runBlocking {
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(
            listOf(
                rule(
                    "allow-specific",
                    RuleAction.ALLOW,
                    RuleMatcher.SpecificNumbers(setOf("15718881234", "18005551234")),
                ),
            ),
        )

        assertEquals("allow-specific", repo.compileSnapshot().evaluate("15718881234", emptySet()).ruleId)
        assertEquals("allow-specific", repo.compileSnapshot().evaluate("18005551234", emptySet()).ruleId)
        assertNull(repo.compileSnapshot().evaluate("19999999999", emptySet()).ruleId)
    }

    @Test
    fun rangeMatcherRoundTripsThroughPersistence() = runBlocking {
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(
            listOf(rule("block-range", RuleAction.BLOCK, RuleMatcher.Range("15718880000", "15718889999"))),
        )

        assertEquals(RuleAction.BLOCK, repo.compileSnapshot().evaluate("15718881234", emptySet()).action)
        assertEquals(RuleAction.ALLOW, repo.compileSnapshot().evaluate("15718870000", emptySet()).action)
    }

    @Test
    fun regexMatcherRoundTripsThroughPersistence() = runBlocking {
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(listOf(rule("regex-block", RuleAction.BLOCK, RuleMatcher.Regex("1571\\d{6}"))))

        assertEquals(RuleAction.BLOCK, repo.compileSnapshot().evaluate("1571888123", emptySet()).action)
        assertEquals(RuleAction.ALLOW, repo.compileSnapshot().evaluate("1572888123", emptySet()).action)
    }

    @Test
    fun refreshFromDiskRepublishesPersistedRulesAfterARestart() = runBlocking {
        val dao = FakeRuleDao()
        val firstProcess = RuleRepository(dao)
        firstProcess.replaceRules(listOf(rule("r1", RuleAction.BLOCK, RuleMatcher.Exact("15718881234"))))

        // A new RuleRepository over the same dao simulates a fresh process:
        // it starts from RuleSnapshot.EMPTY until explicitly refreshed.
        val secondProcess = RuleRepository(dao)
        assertEquals(RuleAction.ALLOW, secondProcess.compileSnapshot().evaluate("15718881234", emptySet()).action)

        secondProcess.refreshFromDisk()
        val result = secondProcess.compileSnapshot().evaluate("15718881234", emptySet())
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("r1", result.ruleId)
    }

    @Test
    fun disabledInvalidRuleDoesNotBreakReplaceOrEvaluation() = runBlocking {
        // A disabled rule with a malformed matcher must not prevent a valid
        // enabled rule from being persisted, compiled, and matched — the
        // same rule compiler "disabled rules are skipped before compilation"
        // contract, exercised through the repository.
        val repo = RuleRepository(FakeRuleDao())
        repo.replaceRules(
            listOf(
                rule("disabled-bad-regex", RuleAction.BLOCK, RuleMatcher.Regex("(\\d+)+"), enabled = false),
                rule("enabled-exact-allow", RuleAction.ALLOW, RuleMatcher.Exact("15718881234")),
            ),
        )

        val result = repo.compileSnapshot().evaluate("15718881234", emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("enabled-exact-allow", result.ruleId)
        assertTrue(result.explanation.contains("enabled-exact-allow"))
    }
}
