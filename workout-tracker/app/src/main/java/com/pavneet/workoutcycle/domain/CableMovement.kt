package com.pavneet.workoutcycle.domain

/** Where to set the pulley on the cable tower. */
enum class PulleyHeight { HIGH, CHEST, LOW }

/** Cable-machine movements the app can animate. */
enum class CableMovement(val pulley: PulleyHeight) {
    CHEST_PRESS(PulleyHeight.CHEST),
    ROW(PulleyHeight.CHEST),
    STRAIGHT_ARM_PULLDOWN(PulleyHeight.HIGH),
    FACE_PULL(PulleyHeight.HIGH),
    LATERAL_RAISE(PulleyHeight.LOW),
    TRICEPS_PUSHDOWN(PulleyHeight.HIGH),
    OVERHEAD_TRICEPS_EXTENSION(PulleyHeight.HIGH),
    BICEPS_CURL(PulleyHeight.LOW),
    ;

    companion object {
        // Checked in order, so specific phrases ("triceps pushdown") win over broad ones ("push").
        private val KEYWORDS: List<Pair<CableMovement, List<String>>> = listOf(
            OVERHEAD_TRICEPS_EXTENSION to listOf("overhead tricep", "overhead extension", "tricep extension"),
            TRICEPS_PUSHDOWN to listOf("tricep", "pushdown", "push down", "pressdown", "press down"),
            BICEPS_CURL to listOf("bicep", "curl"),
            FACE_PULL to listOf("face pull", "rear delt"),
            LATERAL_RAISE to listOf("lateral", "side raise", "shoulder", "delt"),
            STRAIGHT_ARM_PULLDOWN to listOf("straight arm", "pulldown", "pull down", "pullover", "lat pull"),
            ROW to listOf("row", "back", "lat"),
            CHEST_PRESS to listOf("push", "chest", "press", "pec", "fly", "flye"),
        )

        /**
         * Best guess from an exercise's name, or `null` if nothing matches. Each keyword word
         * must start a word of the name, so "lat" matches "Lats" but not "Plate".
         */
        fun guessFor(exerciseName: String): CableMovement? {
            val words = exerciseName.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
            return KEYWORDS.firstOrNull { (_, keywords) -> keywords.any { words.containsPhrase(it) } }?.first
        }

        private fun List<String>.containsPhrase(phrase: String): Boolean {
            val parts = phrase.split(' ')
            return (0..size - parts.size).any { start ->
                parts.indices.all { i -> this[start + i].startsWith(parts[i]) }
            }
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
