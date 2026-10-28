package com.example.reactiontime

import android.content.Context

/** Persists reaction times and finished-game averages (all in milliseconds). */
class ScoreRepository(context: Context) {

    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)

    fun reactions(): List<Long> = read(KEY_REACTIONS)

    fun gameAverages(): List<Long> = read(KEY_GAMES)

    fun addReaction(ms: Long) = append(KEY_REACTIONS, ms)

    fun addGameAverage(ms: Long) = append(KEY_GAMES, ms)

    fun clear() = prefs.edit().clear().apply()

    private fun read(key: String): List<Long> =
        prefs.getString(key, "").orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }

    private fun append(key: String, ms: Long) {
        val updated = (read(key) + ms).takeLast(MAX_ENTRIES)
        prefs.edit().putString(key, updated.joinToString(",")).apply()
    }

    private companion object {
        const val KEY_REACTIONS = "reactions"
        const val KEY_GAMES = "games"
        const val MAX_ENTRIES = 1000
    }
}
