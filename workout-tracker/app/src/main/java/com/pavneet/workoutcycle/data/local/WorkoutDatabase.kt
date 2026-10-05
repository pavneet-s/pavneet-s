package com.pavneet.workoutcycle.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pavneet.workoutcycle.domain.WorkoutCycle

@Database(
    entities = [CycleEntity::class, ExerciseEntity::class, SetLogEntity::class, SettingsEntity::class],
    version = 3,
)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val DEFAULT_CYCLE_ID = 1L

        fun build(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout-cycle.db")
                .addCallback(SeedDefaultCycle)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2 adds the per-exercise animation choice; existing rows keep guessing from their name. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN animation TEXT")
            }
        }

        /** v3 adds the set log, settings, machine setup per exercise and the rest timer. */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN attachment TEXT")
                db.execSQL("ALTER TABLE exercises ADD COLUMN pulley_position TEXT")
                db.execSQL("ALTER TABLE exercises ADD COLUMN setup_note TEXT")
                db.execSQL("ALTER TABLE cycles ADD COLUMN rest_ends_at INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS set_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "exercise_id INTEGER, " +
                        "exercise_name TEXT NOT NULL, " +
                        "weight REAL, " +
                        "weight_unit TEXT, " +
                        "reps INTEGER, " +
                        "completed_at INTEGER NOT NULL, " +
                        "FOREIGN KEY(exercise_id) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE SET NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_set_logs_exercise_id ON set_logs (exercise_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_set_logs_completed_at ON set_logs (completed_at)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS settings (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "rest_enabled INTEGER NOT NULL, " +
                        "rest_seconds INTEGER NOT NULL, " +
                        "weight_unit TEXT NOT NULL, " +
                        "workout_notification INTEGER NOT NULL, " +
                        "notification_prompted INTEGER NOT NULL)",
                )
            }
        }
    }

    /** Seeds the default rotation once, when the database file is first created. */
    private object SeedDefaultCycle : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO cycles (id, name, current_exercise_id, sets_completed, rounds_completed) " +
                    "VALUES (?, 'My cycle', NULL, 0, 0)",
                arrayOf<Any?>(DEFAULT_CYCLE_ID),
            )
            WorkoutCycle.DEFAULT_EXERCISES.forEachIndexed { position, name ->
                db.execSQL(
                    "INSERT INTO exercises (cycle_id, name, position, is_active) VALUES (?, ?, ?, 1)",
                    arrayOf<Any?>(DEFAULT_CYCLE_ID, name, position),
                )
            }
        }
    }
}
