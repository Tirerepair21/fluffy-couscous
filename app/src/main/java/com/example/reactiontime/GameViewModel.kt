package com.example.reactiontime

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

const val GRID_SIZE = 4
const val SQUARE_COUNT = GRID_SIZE * GRID_SIZE
const val ROUNDS_PER_GAME = 5

enum class Phase { Idle, Waiting, Go, Hit, TooEarly, WrongSquare, GameOver }

data class GameState(
    val phase: Phase = Phase.Idle,
    val litSquare: Int? = null,
    val wrongSquare: Int? = null,
    val lastMs: Long? = null,
    val roundTimes: List<Long> = emptyList(),
)

data class Stats(
    val reactions: List<Long> = emptyList(),
    val gameAverages: List<Long> = emptyList(),
) {
    val best: Long? get() = reactions.minOrNull()
    val allTimeAverage: Long? get() = reactions.averageMs()
    val lastTenAverage: Long? get() = reactions.takeLast(10).averageMs()
    val bestGame: Long? get() = gameAverages.minOrNull()
    val topReactions: List<Long> get() = reactions.sorted().take(10)
    val topGames: List<Long> get() = gameAverages.sorted().take(10)
}

fun List<Long>.averageMs(): Long? = if (isEmpty()) null else Math.round(average())

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ScoreRepository(app)

    var state by mutableStateOf(GameState())
        private set

    var stats by mutableStateOf(loadStats())
        private set

    private var roundJob: Job? = null
    private var greenAtNanos = 0L

    fun startGame() {
        state = GameState()
        startRound()
    }

    /** Re-run the current round after a false start or wrong square. */
    fun retryRound() = startRound()

    fun onSquarePressed(index: Int) {
        val pressedAt = System.nanoTime()
        when (state.phase) {
            Phase.Waiting -> {
                roundJob?.cancel()
                state = state.copy(phase = Phase.TooEarly, wrongSquare = index)
            }
            Phase.Go -> if (index == state.litSquare) {
                recordHit((pressedAt - greenAtNanos) / 1_000_000)
            } else {
                state = state.copy(phase = Phase.WrongSquare, wrongSquare = index)
            }
            else -> Unit
        }
    }

    fun resetScores() {
        repo.clear()
        stats = loadStats()
    }

    private fun startRound() {
        roundJob?.cancel()
        state = state.copy(phase = Phase.Waiting, litSquare = null, wrongSquare = null)
        roundJob = viewModelScope.launch {
            delay(Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS))
            state = state.copy(phase = Phase.Go, litSquare = Random.nextInt(SQUARE_COUNT))
            greenAtNanos = System.nanoTime()
        }
    }

    private fun recordHit(ms: Long) {
        repo.addReaction(ms)
        val times = state.roundTimes + ms
        val finished = times.size == ROUNDS_PER_GAME
        if (finished) repo.addGameAverage(times.averageMs()!!)
        stats = loadStats()
        state = state.copy(
            phase = if (finished) Phase.GameOver else Phase.Hit,
            lastMs = ms,
            roundTimes = times,
        )
        if (!finished) {
            roundJob = viewModelScope.launch {
                delay(NEXT_ROUND_PAUSE_MS)
                startRound()
            }
        }
    }

    private fun loadStats() = Stats(repo.reactions(), repo.gameAverages())

    private companion object {
        const val MIN_DELAY_MS = 1_500L
        const val MAX_DELAY_MS = 5_000L
        const val NEXT_ROUND_PAUSE_MS = 1_200L
    }
}
