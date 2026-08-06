package studio.ainovations.callguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The single Room database for CallGuard's local persistence. Stores only
 * [RuleEntity] rows (rule definitions and canonical matcher values); no
 * call audio, message content, contacts export, or raw phone history.
 *
 * `exportSchema` is `false`: CallGuard has a single schema version and no
 * migrations to validate yet. Set it to `true` and configure
 * `room.schemaLocation` once a version-2 migration is introduced.
 */
@Database(entities = [RuleEntity::class], version = 1, exportSchema = false)
@TypeConverters(RuleEntityConverters::class)
abstract class CallGuardDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao

    companion object {
        private const val DATABASE_NAME = "callguard.db"

        fun build(context: Context): CallGuardDatabase =
            Room.databaseBuilder(context.applicationContext, CallGuardDatabase::class.java, DATABASE_NAME).build()
    }
}
