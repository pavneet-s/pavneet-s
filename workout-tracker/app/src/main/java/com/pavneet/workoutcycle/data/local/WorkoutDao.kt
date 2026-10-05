package com.pavneet.workoutcycle.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM cycles WHERE id = :cycleId")
    fun observeCycle(cycleId: Long): Flow<CycleWithExercises?>

    @Transaction
    @Query("SELECT * FROM cycles WHERE id = :cycleId")
    suspend fun getCycle(cycleId: Long): CycleWithExercises?

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Upsert
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercises WHERE cycle_id = :cycleId AND id NOT IN (:keepIds)")
    suspend fun deleteExercisesNotIn(cycleId: Long, keepIds: List<Long>)

    @Query(
        """
        UPDATE cycles
        SET current_exercise_id = :currentExerciseId,
            sets_completed = :setsCompleted,
            rounds_completed = :roundsCompleted,
            rest_ends_at = :restEndsAt
        WHERE id = :cycleId
        """,
    )
    suspend fun updateProgress(
        cycleId: Long,
        currentExerciseId: Long?,
        setsCompleted: Int,
        roundsCompleted: Int,
        restEndsAt: Long?,
    )

    @Insert
    suspend fun insertLog(log: SetLogEntity): Long

    @Query("DELETE FROM set_logs WHERE id = :logId")
    suspend fun deleteLog(logId: Long)

    @Query("SELECT * FROM set_logs ORDER BY completed_at, id")
    fun observeLogs(): Flow<List<SetLogEntity>>

    /** The last set of [exerciseId] done as [movement] (`IS` also matches a null movement). */
    @Query(
        """
        SELECT * FROM set_logs WHERE exercise_id = :exerciseId AND movement IS :movement
        ORDER BY completed_at DESC, id DESC LIMIT 1
        """,
    )
    suspend fun lastLogFor(exerciseId: Long, movement: String?): SetLogEntity?

    /** The most recent set of each cable exercise of every exercise that still exists. */
    @Query(
        """
        SELECT * FROM set_logs
        WHERE id IN (SELECT MAX(id) FROM set_logs WHERE exercise_id IS NOT NULL GROUP BY exercise_id, movement)
        """,
    )
    fun observeLatestLogs(): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SINGLETON_ID}")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SINGLETON_ID}")
    suspend fun getSettings(): SettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: SettingsEntity)
}
