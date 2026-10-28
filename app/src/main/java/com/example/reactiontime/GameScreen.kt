package com.example.reactiontime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Green = Color(0xFF2ECC40)
private val Red = Color(0xFFE53935)

private fun Long?.ms() = if (this == null) "—" else "$this ms"

@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    val state = vm.state
    val stats = vm.stats

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Reaction Grid", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            StatusPanel(state)
            Grid(state, onPress = vm::onSquarePressed)
            ActionButton(state, onStart = vm::startGame, onRetry = vm::retryRound)
            CurrentGame(state)
            StatsCard(stats)
            Scoreboard(stats, onReset = vm::resetScores)
        }
    }
}

@Composable
private fun StatusPanel(state: GameState) {
    val round = (state.roundTimes.size + 1).coerceAtMost(ROUNDS_PER_GAME)
    val (headline, detail) = when (state.phase) {
        Phase.Idle -> "Ready?" to "Tap the square the moment it turns green. $ROUNDS_PER_GAME rounds per game."
        Phase.Waiting -> "Wait for green…" to "Round $round of $ROUNDS_PER_GAME"
        Phase.Go -> "TAP!" to "Round $round of $ROUNDS_PER_GAME"
        Phase.Hit -> state.lastMs.ms() to "Nice! Next round coming up…"
        Phase.TooEarly -> "Too early!" to "Wait until a square turns green."
        Phase.WrongSquare -> "Wrong square!" to "Tap the green one."
        Phase.GameOver -> state.roundTimes.averageMs().ms() to "Game average · last ${state.lastMs.ms()}"
    }
    val headlineColor = when (state.phase) {
        Phase.Go, Phase.Hit -> Green
        Phase.TooEarly, Phase.WrongSquare -> Red
        else -> MaterialTheme.colorScheme.onBackground
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(headline, fontSize = 40.sp, fontWeight = FontWeight.Black, color = headlineColor)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Grid(state: GameState, onPress: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(GRID_SIZE) { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(GRID_SIZE) { col ->
                    val index = row * GRID_SIZE + col
                    val color = when (index) {
                        state.litSquare -> Green
                        state.wrongSquare -> Red
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Square(color, Modifier.weight(1f), onPress = { onPress(index) })
                }
            }
        }
    }
}

@Composable
private fun Square(color: Color, modifier: Modifier, onPress: () -> Unit) {
    // Green must appear instantly so the timer is fair; only fade back to grey.
    val shown by animateColorAsState(
        color,
        animationSpec = if (color == Green) tween(0) else tween(150),
        label = "square",
    )
    val currentOnPress by rememberUpdatedState(onPress)
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(shown)
            // React on touch-down rather than release so the measured time is the real reaction.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    currentOnPress()
                }
            },
    )
}

@Composable
private fun ActionButton(state: GameState, onStart: () -> Unit, onRetry: () -> Unit) {
    val (label, action) = when (state.phase) {
        Phase.Idle -> "Start" to onStart
        Phase.TooEarly, Phase.WrongSquare -> "Try again" to onRetry
        Phase.GameOver -> "Play again" to onStart
        else -> null to null
    }
    Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
        if (label != null && action != null) {
            Button(onClick = action) { Text(label) }
        }
    }
}

@Composable
private fun CurrentGame(state: GameState) {
    if (state.roundTimes.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("This game", style = MaterialTheme.typography.titleMedium)
            state.roundTimes.forEachIndexed { i, ms -> StatRow("Round ${i + 1}", ms.ms()) }
            StatRow("Average", state.roundTimes.averageMs().ms(), bold = true)
        }
    }
}

@Composable
private fun StatsCard(stats: Stats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Averages", style = MaterialTheme.typography.titleMedium)
            StatRow("All-time average", stats.allTimeAverage.ms(), bold = true)
            StatRow("Last 10 average", stats.lastTenAverage.ms())
            StatRow("Best reaction", stats.best.ms())
            StatRow("Best game average", stats.bestGame.ms())
            StatRow("Reactions recorded", stats.reactions.size.toString())
            StatRow("Games played", stats.gameAverages.size.toString())
        }
    }
}

@Composable
private fun Scoreboard(stats: Stats, onReset: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    val entries = if (tab == 0) stats.topGames else stats.topReactions

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                "Scoreboard",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SecondaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Best games") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Best reactions") })
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entries.isEmpty()) {
                    Text(
                        if (tab == 0) "Finish a $ROUNDS_PER_GAME-round game to get on the board."
                        else "No reactions yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entries.forEachIndexed { i, ms -> StatRow("#${i + 1}", ms.ms(), bold = i == 0) }
            }
            if (stats.reactions.isNotEmpty()) {
                TextButton(onClick = { confirmReset = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Reset scores")
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset scores?") },
            text = { Text("This clears every saved reaction time and game average.") },
            confirmButton = {
                TextButton(onClick = { onReset(); confirmReset = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Spacer(Modifier.padding(4.dp))
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
