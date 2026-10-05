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
)

/**
 * The cycle and its exercises, loaded in one transaction. Observing this (rather than combining
 * two separate flows) means a change to both tables is never seen half-applied.
 */
data class CycleWithExercises(
    @Embedded val cycle: CycleEntity,
    @Relation(parentColumn = "id", entityColumn = "cycle_id")
    val exercises: List<ExerciseEntity>,
)
