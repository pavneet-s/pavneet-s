package com.pavneet.workoutcycle.domain

/** Where to set the pulley on the cable tower. */
enum class PulleyHeight { HIGH, CHEST, LOW }

/** A muscle group in the rotation. Its cable exercises are the ones you can swipe between. */
enum class MuscleGroup {
    CHEST,
    BACK,
    SHOULDERS,
    TRICEPS,
    BICEPS,
    LEGS,
    CORE,
    ;

    /** This group's cable exercises; the first is the default. */
    val movements: List<CableMovement> get() = CableMovement.entries.filter { it.group == this }

    companion object {
        // Checked in order: "Shoulder press" is shoulders and "Leg press" is legs, not chest.
        private val KEYWORDS: List<Pair<MuscleGroup, List<String>>> = listOf(
            TRICEPS to listOf("tricep"),
            BICEPS to listOf("bicep", "curl"),
            SHOULDERS to listOf("shoulder", "delt", "overhead press", "military"),
            LEGS to listOf("leg", "glute", "quad", "hamstring", "squat", "lunge", "hip", "calf", "calves", "butt"),
            CORE to listOf("core", "ab", "oblique", "stomach", "crunch"),
            BACK to listOf("back", "lat", "row", "pull"),
            CHEST to listOf("chest", "pec", "push", "bench", "fly", "flye", "press"),
        )

        /** Best guess from a name such as "Chest" or "Back day", or `null` if nothing matches. */
        fun guessFor(name: String): MuscleGroup? {
            val words = name.words()
            return KEYWORDS.firstOrNull { (_, keywords) -> keywords.any { words.containsPhrase(it) } }?.first
        }
    }
}

/**
 * Cable-machine exercises the app can animate, grouped by the muscle they train. Within a group
 * they're listed in swipe order, default first.
 */
enum class CableMovement(val group: MuscleGroup, val pulley: PulleyHeight) {
    CHEST_PRESS(MuscleGroup.CHEST, PulleyHeight.CHEST),
    CHEST_FLY(MuscleGroup.CHEST, PulleyHeight.CHEST),
    HIGH_TO_LOW_FLY(MuscleGroup.CHEST, PulleyHeight.HIGH),
    LOW_TO_HIGH_FLY(MuscleGroup.CHEST, PulleyHeight.LOW),

    ROW(MuscleGroup.BACK, PulleyHeight.CHEST),
    LAT_PULLDOWN(MuscleGroup.BACK, PulleyHeight.HIGH),
    STRAIGHT_ARM_PULLDOWN(MuscleGroup.BACK, PulleyHeight.HIGH),

    LATERAL_RAISE(MuscleGroup.SHOULDERS, PulleyHeight.LOW),
    FRONT_RAISE(MuscleGroup.SHOULDERS, PulleyHeight.LOW),
    FACE_PULL(MuscleGroup.SHOULDERS, PulleyHeight.HIGH),

    TRICEPS_PUSHDOWN(MuscleGroup.TRICEPS, PulleyHeight.HIGH),
    OVERHEAD_TRICEPS_EXTENSION(MuscleGroup.TRICEPS, PulleyHeight.HIGH),
    TRICEPS_KICKBACK(MuscleGroup.TRICEPS, PulleyHeight.LOW),

    BICEPS_CURL(MuscleGroup.BICEPS, PulleyHeight.LOW),
    HIGH_CABLE_CURL(MuscleGroup.BICEPS, PulleyHeight.HIGH),
    BEHIND_BACK_CURL(MuscleGroup.BICEPS, PulleyHeight.LOW),

    GLUTE_KICKBACK(MuscleGroup.LEGS, PulleyHeight.LOW),
    PULL_THROUGH(MuscleGroup.LEGS, PulleyHeight.LOW),

    CABLE_CRUNCH(MuscleGroup.CORE, PulleyHeight.HIGH),
    WOODCHOPPER(MuscleGroup.CORE, PulleyHeight.HIGH),
    ;

    companion object {
        // Named exercises, checked in order so specific phrases ("glute kickback") win over
        // broad ones ("kickback"). Anything else falls back to its muscle group's default.
        private val KEYWORDS: List<Pair<CableMovement, List<String>>> = listOf(
            OVERHEAD_TRICEPS_EXTENSION to listOf("overhead tricep", "overhead extension", "tricep extension"),
            GLUTE_KICKBACK to listOf("glute kickback", "donkey kick", "leg kickback", "hip extension"),
            TRICEPS_KICKBACK to listOf("kickback", "kick back"),
            TRICEPS_PUSHDOWN to listOf("pushdown", "push down", "pressdown", "press down"),
            HIGH_CABLE_CURL to listOf("high cable curl", "high curl"),
            BEHIND_BACK_CURL to listOf("behind the back", "behind back", "bayesian"),
            BICEPS_CURL to listOf("curl"),
            FACE_PULL to listOf("face pull", "rear delt"),
            FRONT_RAISE to listOf("front raise"),
            LATERAL_RAISE to listOf("lateral", "side raise", "lat raise"),
            STRAIGHT_ARM_PULLDOWN to listOf("straight arm", "pullover"),
            LAT_PULLDOWN to listOf("pulldown", "pull down", "lat pull"),
            PULL_THROUGH to listOf("pull through", "pullthrough"),
            ROW to listOf("row"),
            HIGH_TO_LOW_FLY to listOf("high to low", "crossover", "cross over"),
            LOW_TO_HIGH_FLY to listOf("low to high", "incline fly"),
            CHEST_FLY to listOf("fly", "flye", "pec deck"),
            CHEST_PRESS to listOf("chest press", "bench press", "push up", "pushup"),
            CABLE_CRUNCH to listOf("crunch"),
            WOODCHOPPER to listOf("woodchop", "wood chop", "chop"),
        )

        /**
         * Best guess from an exercise's name, or `null` if nothing matches. Each keyword word
         * must start a word of the name, so "lat" matches "Lats" but not "Plate".
         */
        fun guessFor(exerciseName: String): CableMovement? {
            val words = exerciseName.words()
            return KEYWORDS.firstOrNull { (_, keywords) -> keywords.any { words.containsPhrase(it) } }?.first
                ?: MuscleGroup.guessFor(exerciseName)?.movements?.first()
        }
    }
}

/** How an exercise picks its animation. */
sealed interface AnimationSetting {
    /** Guess from the exercise's name. */
    data object Auto : AnimationSetting

    /** Never animate this exercise. */
    data object Off : AnimationSetting

    data class Fixed(val movement: CableMovement) : AnimationSetting
}

private fun String.words(): List<String> = lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }

private fun List<String>.containsPhrase(phrase: String): Boolean {
    val parts = phrase.split(' ')
    return (0..size - parts.size).any { start ->
        parts.indices.all { i -> this[start + i].startsWith(parts[i]) }
    }
}
