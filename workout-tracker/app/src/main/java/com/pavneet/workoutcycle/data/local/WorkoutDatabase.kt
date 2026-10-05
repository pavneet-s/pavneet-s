package com.pavneet.workoutcycle.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pavneet.workoutcycle.domain.WorkoutCycle

@Database(entities = [CycleEntity::class, ExerciseEntity::class], version = 1)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val DEFAULT_CYCLE_ID = 1L

        fun build(context: Context): WorkoutDatabase =
            Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout-cycle.db")
                .addCallback(SeedDefaultCycle)
                .build()
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
