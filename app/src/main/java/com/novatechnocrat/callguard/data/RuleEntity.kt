package studio.ainovations.callguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import studio.ainovations.callguard.domain.BlockingRule
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.domain.RuleMatcher

/**
 * The matcher shape stored in [RuleEntity.matcherType]. Mirrors the
 * [RuleMatcher] sealed interface's variants one-to-one.
 */
enum class MatcherType {
    EXACT,
    STARTS_WITH,
    ENDS_WITH,
    CONTAINS,
    RANGE,
    CONTACTS,
    SPECIFIC_NUMBERS,
    REGEX,
}

/**
 * The persisted form of a [BlockingRule]. Only rule fields and canonical
 * matcher values are stored — no call audio, message content, contacts
 * export, or raw phone history. [SpecificNumbers][RuleMatcher.SpecificNumbers]
 * numbers are canonical digit strings the user chose as part of the rule
 * definition, not a contacts export or call log.
 *
 * [position] preserves the declaration order of the list passed to
 * [RuleRepository.replaceRules], since [RuleCompiler][studio.ainovations.callguard.domain.RuleCompiler]'s
 * precedence rule breaks priority/specificity ties by that order; a `SELECT`
 * without an explicit order is not guaranteed to preserve it.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val action: RuleAction,
    val matcherType: MatcherType,
    val matcherValue: String? = null,
    val rangeStart: String? = null,
    val rangeEnd: String? = null,
    val specificNumbers: Set<String>? = null,
    val priority: Int,
    val position: Int,
)

/**
 * Room [TypeConverter]s for [RuleEntity] columns that are not natively
 * supported SQLite types. Registered on [CallGuardDatabase].
 */
class RuleEntityConverters {

    @TypeConverter
    fun fromRuleAction(action: RuleAction): String = action.name

    @TypeConverter
    fun toRuleAction(value: String): RuleAction = RuleAction.valueOf(value)

    @TypeConverter
    fun fromMatcherType(type: MatcherType): String = type.name

    @TypeConverter
    fun toMatcherType(value: String): MatcherType = MatcherType.valueOf(value)

    /**
     * Joins [set] into a single [SPECIFIC_NUMBERS_DELIMITER]-separated column
     * value. A comma is only a safe, collision-free delimiter because every
     * element is required to be a non-empty canonical digit string — digits
     * can never contain a comma, so a join can never be ambiguously re-split.
     * [requireCanonicalDigits] enforces that here rather than assuming it: a
     * value containing the delimiter (or any non-digit character) would
     * otherwise round-trip as a *different* set of numbers than what was
     * written, silently and without error.
     *
     * [BlockingRule.toEntity] validates this same invariant before it ever
     * reaches this converter, but this is the actual Room/SQLite persistence
     * boundary — a future caller that constructs a [RuleEntity] directly
     * (bypassing [BlockingRule.toEntity]) would not go through that check, so
     * it must fail loudly here too rather than silently corrupt the DB
     * column.
     */
    @TypeConverter
    fun fromStringSet(set: Set<String>?): String? {
        set?.forEach(::requireCanonicalDigits)
        return set?.joinToString(SPECIFIC_NUMBERS_DELIMITER)
    }

    @TypeConverter
    fun toStringSet(value: String?): Set<String>? =
        value?.split(SPECIFIC_NUMBERS_DELIMITER)?.filter { it.isNotEmpty() }?.toSet()

    private companion object {
        const val SPECIFIC_NUMBERS_DELIMITER = ","
    }
}

/**
 * Enforces the canonical-digit invariant [RuleEntityConverters]'s
 * comma-delimited [RuleEntity.specificNumbers] CSV encoding depends on: a
 * comma (or any other non-digit character) inside a value would be
 * indistinguishable, after encode then decode, from two separate numbers
 * split at that comma — silent data corruption, not a crash. Shared by
 * [RuleEntityConverters.fromStringSet] and [BlockingRule.toEntity] so both
 * persistence boundaries a [RuleMatcher.SpecificNumbers] value crosses reject
 * a bad value the same way.
 */
private fun requireCanonicalDigits(value: String) {
    require(value.isNotEmpty() && value.all { it.isDigit() }) {
        "SpecificNumbers value '$value' must be a non-empty canonical digit string " +
            "(a non-digit character would corrupt the comma-delimited CSV round trip)"
    }
}

/** Maps a domain [BlockingRule] to its persisted form at list [position]. */
fun BlockingRule.toEntity(position: Int): RuleEntity {
    var matcherValue: String? = null
    var rangeStart: String? = null
    var rangeEnd: String? = null
    var specificNumbers: Set<String>? = null
    val matcherType = when (val m = matcher) {
        is RuleMatcher.Exact -> {
            matcherValue = m.value
            MatcherType.EXACT
        }
        is RuleMatcher.StartsWith -> {
            matcherValue = m.prefix
            MatcherType.STARTS_WITH
        }
        is RuleMatcher.EndsWith -> {
            matcherValue = m.suffix
            MatcherType.ENDS_WITH
        }
        is RuleMatcher.Contains -> {
            matcherValue = m.substring
            MatcherType.CONTAINS
        }
        is RuleMatcher.Range -> {
            rangeStart = m.startInclusive
            rangeEnd = m.endInclusive
            MatcherType.RANGE
        }
        is RuleMatcher.Contacts -> MatcherType.CONTACTS
        is RuleMatcher.SpecificNumbers -> {
            // RuleCompiler validates SpecificNumbers for ENABLED rules only —
            // it skips disabled rules entirely — yet replaceRules persists
            // disabled rules too, so a disabled rule's bad value must be
            // caught here, at the mapping boundary, instead of silently
            // corrupting on its next comma-delimited CSV encode (see
            // requireCanonicalDigits).
            m.numbers.forEach(::requireCanonicalDigits)
            specificNumbers = m.numbers
            MatcherType.SPECIFIC_NUMBERS
        }
        is RuleMatcher.Regex -> {
            matcherValue = m.pattern
            MatcherType.REGEX
        }
    }
    return RuleEntity(
        id = id,
        name = name,
        enabled = enabled,
        action = action,
        matcherType = matcherType,
        matcherValue = matcherValue,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        specificNumbers = specificNumbers,
        priority = priority,
        position = position,
    )
}

/** Maps a persisted [RuleEntity] back to the domain [BlockingRule]. */
fun RuleEntity.toDomain(): BlockingRule {
    val matcher: RuleMatcher = when (matcherType) {
        MatcherType.EXACT -> RuleMatcher.Exact(matcherValue.orEmpty())
        MatcherType.STARTS_WITH -> RuleMatcher.StartsWith(matcherValue.orEmpty())
        MatcherType.ENDS_WITH -> RuleMatcher.EndsWith(matcherValue.orEmpty())
        MatcherType.CONTAINS -> RuleMatcher.Contains(matcherValue.orEmpty())
        MatcherType.RANGE -> RuleMatcher.Range(rangeStart.orEmpty(), rangeEnd.orEmpty())
        MatcherType.CONTACTS -> RuleMatcher.Contacts
        MatcherType.SPECIFIC_NUMBERS -> RuleMatcher.SpecificNumbers(specificNumbers ?: emptySet())
        MatcherType.REGEX -> RuleMatcher.Regex(matcherValue.orEmpty())
    }
    return BlockingRule(
        id = id,
        name = name,
        enabled = enabled,
        action = action,
        matcher = matcher,
        priority = priority,
    )
}
