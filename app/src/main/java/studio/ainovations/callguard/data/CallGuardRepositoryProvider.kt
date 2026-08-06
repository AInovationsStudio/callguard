package studio.ainovations.callguard.data

import android.content.Context

/**
 * Process-wide repositories shared by the UI and the screening service.
 *
 * Both entry points run in the application process. Sharing the repository
 * keeps their in-memory snapshots aligned immediately after a mutation while
 * Room remains the durable source of truth across process restarts.
 */
object CallGuardRepositoryProvider {
    @Volatile
    private var rulesRepository: RuleRepository? = null

    fun rules(context: Context): RuleRepository =
        rulesRepository ?: synchronized(this) {
            rulesRepository ?: RuleRepository(
                CallGuardDatabase.build(context.applicationContext).ruleDao(),
            ).also { rulesRepository = it }
        }
}
