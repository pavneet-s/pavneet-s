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
            rounds_completed = :roundsCompleted
        WHERE id = :cycleId
        """,
    )
    suspend fun updateProgress(
        cycleId: Long,
        currentExerciseId: Long?,
        setsCompleted: Int,
        roundsCompleted: Int,
    )
}
