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

    @TypeConverter
    fun fromStringSet(set: Set<String>?): String? = set?.joinToString(SPECIFIC_NUMBERS_DELIMITER)

    @TypeConverter
    fun toStringSet(value: String?): Set<String>? =
        value?.split(SPECIFIC_NUMBERS_DELIMITER)?.filter { it.isNotEmpty() }?.toSet()

    private companion object {
        const val SPECIFIC_NUMBERS_DELIMITER = ","
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
