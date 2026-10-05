package com.pavneet.workoutcycle.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A rotation and its progress pointer. The app uses a single row today; keying exercises by
 * cycle leaves room for more routines (e.g. "Push day", "Pull day") later.
 */
@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(name = "current_exercise_id") val currentExerciseId: Long?,
    @ColumnInfo(name = "sets_completed") val setsCompleted: Int,
    @ColumnInfo(name = "rounds_completed") val roundsCompleted: Int,
    /** Epoch ms when the current rest ends. Added in v3. */
    @ColumnInfo(name = "rest_ends_at") val restEndsAt: Long? = null,
)

@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycle_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cycle_id")],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "cycle_id") val cycleId: Long,
    val name: String,
    /** 0-based slot in the rotation; rewritten for every exercise on each save. */
    val position: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    /** `null` = guess from the name, "OFF" = no animation, otherwise a CableMovement name. Added in v2. */
    val animation: String? = null,
    /** An Attachment name. This and the next two columns were added in v3. */
    val attachment: String? = null,
    @ColumnInfo(name = "pulley_position") val pulleyPosition: String? = null,
    @ColumnInfo(name = "setup_note") val setupNote: String? = null,
)

/**
 * One completed set. The exercise name is copied in so history survives renames and deletes;
 * deleting the exercise only clears [exerciseId]. Added in v3.
 */
@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("exercise_id"), Index("completed_at")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long?,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    val weight: Double?,
    /** A WeightUnit name, set whenever [weight] is. */
    @ColumnInfo(name = "weight_unit") val weightUnit: String?,
    val reps: Int?,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "completed_at") val completedAt: Long,
)

/** App preferences in a single row. Missing until first changed; defaults live in AppSettings. Added in v3. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "rest_enabled") val restEnabled: Boolean,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    /** A WeightUnit name. */
    @ColumnInfo(name = "weight_unit") val weightUnit: String,
    @ColumnInfo(name = "workout_notification") val workoutNotification: Boolean,
    @ColumnInfo(name = "notification_prompted") val notificationPrompted: Boolean,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/**
 * The cycle and its exercises, loaded in one transaction. Observing this (rather than combining
 * two separate flows) means a change to both tables is never seen half-applied.
 */
data class CycleWithExercises(
    @Embedded val cycle: CycleEntity,
    @Relation(parentColumn = "id", entityColumn = "cycle_id")
    val exercises: List<ExerciseEntity>,
)
