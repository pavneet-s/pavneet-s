package com.pavneet.workoutcycle.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.WorkoutCycle

@Database(
    entities = [CycleEntity::class, ExerciseEntity::class, SetLogEntity::class, SettingsEntity::class],
    version = 4,
)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val DEFAULT_CYCLE_ID = 1L

        fun build(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout-cycle.db")
                .addCallback(SeedDefaultCycle)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

        /** The first release's default exercises, renamed in v4 to the muscle groups they train. */
        private val RENAMED_DEFAULTS = mapOf("Push-ups" to "Chest", "Back stretches" to "Back")

        /**
         * v4 records which cable exercise each set was, so a muscle group's exercises keep their
         * own weights. Earlier sets are credited to the exercise their slot was showing. The old
         * default names become the muscle groups they train, in the log too.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE set_logs ADD COLUMN movement TEXT")
                db.query("SELECT id, name, animation FROM exercises").use { cursor ->
                    while (cursor.moveToNext()) {
                        val exercise = Exercise(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            animation = AnimationColumn.decode(if (cursor.isNull(2)) null else cursor.getString(2)),
                        )
                        val movement = exercise.movement ?: continue
                        db.execSQL(
                            "UPDATE set_logs SET movement = ? WHERE exercise_id = ?",
                            arrayOf<Any?>(movement.name, exercise.id),
                        )
                    }
                }
                // Sets of deleted exercises only have their name to go on.
                db.query("SELECT DISTINCT exercise_name FROM set_logs WHERE exercise_id IS NULL").use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        val movement = CableMovement.guessFor(name) ?: continue
                        db.execSQL(
                            "UPDATE set_logs SET movement = ? WHERE exercise_id IS NULL AND exercise_name = ?",
                            arrayOf<Any?>(movement.name, name),
                        )
                    }
                }
                for ((old, new) in RENAMED_DEFAULTS) {
                    db.execSQL("UPDATE exercises SET name = ? WHERE name = ?", arrayOf<Any?>(new, old))
                    db.execSQL("UPDATE set_logs SET exercise_name = ? WHERE exercise_name = ?", arrayOf<Any?>(new, old))
                }
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
