package io.github.snowcivilian.questairplay.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.Prefs
import io.github.snowcivilian.questairplay.pokemon.BattleCard
import io.github.snowcivilian.questairplay.pokemon.PokeDataRepository
import io.github.snowcivilian.questairplay.pokemon.PokemonEngine
import io.github.snowcivilian.questairplay.pokemon.Status
import io.github.snowcivilian.questairplay.pokemon.PokemonSettings
import io.github.snowcivilian.questairplay.pokemon.Species

/** Room for the video's top-right buttons (48 dp icons + padding), so the card never covers them. */
private val TOOLBAR_CLEARANCE = 56.dp

/** Panel mode: the battle card floats semi-transparent in a top corner of the video (S4). */
@Composable
fun PokemonCardOverlay(modifier: Modifier = Modifier) {
    val card by PokemonEngine.card.collectAsState()
    val sprite by PokemonEngine.sprite.collectAsState()
    val on by rememberPref(Prefs.POKEMON_HELPER)
    val left by rememberPref(Prefs.POKEMON_CARD_LEFT)
    val shown by rememberPref(Prefs.POKEMON_CARD_SHOWN)
    val window by rememberPref(Prefs.POKEMON_CARD_WINDOW)
    val status by PokemonEngine.status.collectAsState()
    // hidden from the toolbar, or shown in its own window instead (the sample card still shows here)
    val elsewhere = !shown || (window && androidx.compose.ui.res.booleanResource(io.github.jqssun.airplay.R.bool.card_window_supported))
    if (elsewhere && card?.demo != true) return
    // say so on the video when the chosen game can't be read yet, instead of silently doing nothing
    if (on && card == null && status.startsWith("No screen layout")) {
        Box(modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = TOOLBAR_CLEARANCE), contentAlignment = if (left) Alignment.TopStart else Alignment.TopEnd) {
            Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(12.dp)) {
                Text("Battle helper: $status", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        return
    }
    val c = card ?: return
    if (!on && !c.demo) return
    Box(modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = TOOLBAR_CLEARANCE), contentAlignment = if (left) Alignment.TopStart else Alignment.TopEnd) {
        Surface(color = Color.Black.copy(alpha = 0.75f), contentColor = Color.White, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp)) {
            BattleCardContent(c, sprite, Modifier.padding(12.dp))
        }
    }
}

/** Theater mode: the battle card on its own floating panel (S4). */
@Composable
fun TheaterPokemonCard() = io.github.jqssun.airplay.ui.theme.AirPlayTheme(darkTheme = true) {
    val card by PokemonEngine.card.collectAsState()
    val sprite by PokemonEngine.sprite.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        card?.let { BattleCardContent(it, sprite, Modifier.padding(16.dp)) }
    }
}

private val TYPE_COLORS = mapOf(
    "Normal" to 0xFFA8A77A, "Fire" to 0xFFEE8130, "Water" to 0xFF6390F0, "Electric" to 0xFFF7D02C,
    "Grass" to 0xFF7AC74C, "Ice" to 0xFF96D9D6, "Fighting" to 0xFFC22E28, "Poison" to 0xFFA33EA1,
    "Ground" to 0xFFE2BF65, "Flying" to 0xFFA98FF3, "Psychic" to 0xFFF95587, "Bug" to 0xFFA6B91A,
    "Rock" to 0xFFB6A136, "Ghost" to 0xFF735797, "Dragon" to 0xFF6F35FC, "Dark" to 0xFF705746,
    "Steel" to 0xFFB7B7CE, "Fairy" to 0xFFD685AD,
)

@Composable
fun TypeChip(type: String) = Text(
    type.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, softWrap = false,
    modifier = Modifier.background(Color(TYPE_COLORS[type] ?: 0xFF777777), RoundedCornerShape(6.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp),
)

private fun mult(m: Float) = when (m) {
    0.25f -> "¼×"; 0.5f -> "½×"; 0.125f -> "⅛×"; 0f -> "0×"
    else -> if (m % 1f == 0f) "${m.toInt()}×" else "${m}×"
}

/** Status badge colours, close to the games'. */
private val STATUS_COLORS = mapOf(
    Status.PSN to 0xFFA040A0, Status.PAR to 0xFFB8A038, Status.SLP to 0xFF8C888C, Status.BRN to 0xFFE0602F, Status.FRZ to 0xFF5CB8D8,
)

@Composable
fun StatusChip(s: Status) = Text(
    s.badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, softWrap = false,
    modifier = Modifier.background(Color(STATUS_COLORS.getValue(s)), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
)
private fun multColor(m: Float) = when {
    m >= 2f -> Color(0xFF7CFC8A); m == 0f -> Color(0xFF9E9E9E); m < 1f -> Color(0xFFFF8A80); else -> Color.White
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleCardContent(c: BattleCard, sprite: Bitmap?, modifier: Modifier = Modifier) {
    val small = MaterialTheme.typography.bodySmall
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.demo) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sample card", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = PokemonEngine::dismissDemo) { Text("Close") }
        }
        c.note?.let { Text(it, style = MaterialTheme.typography.titleSmall); return@Column }
        val opp = c.opponent
        Row(verticalAlignment = Alignment.CenterVertically) {
            sprite?.let {
                Image(it.asImageBitmap(), null, filterQuality = FilterQuality.None, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(opp?.species?.name ?: opp?.readAs?.let { "$it ?" } ?: "Reading…", style = MaterialTheme.typography.titleMedium)
                    c.oppStatus?.let { StatusChip(it) }
                }
                opp?.species?.let { s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { s.types.forEach { TypeChip(it) } }
                    Text(when (c.wild) { true -> "Wild · catch rate ${s.catchRate}"; false -> "Trainer's Pokémon"; null -> "" }, style = small)
                }
            }
        }
        if (c.opponentProfile.isNotEmpty()) {
            c.opponentProfile.forEach { (m, types) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mult(m), color = multColor(m), fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        types.forEach { TypeChip(it) }
                    }
                }
            }
        }
        c.oppStatusNotes.forEach { Text("${c.oppStatus!!.label}: $it", style = small, color = Color(0xFFFFD180)) }
        c.summary?.let { s ->
            s.myStrongTypes.forEach { (t, m) -> Text("Your $t hits it ${mult(m)}", style = small, color = multColor(m)) }
            s.theirThreats.forEach { (t, m) -> Text("Its $t hits you ${mult(m)}", style = small, color = Color(0xFFFF8A80)) }
            if (s.myStrongTypes.isEmpty() && s.theirThreats.isEmpty()) Text("No type advantage either way", style = small)
        }
        if (c.moves.isNotEmpty()) {
            Text("Your moves", style = MaterialTheme.typography.labelLarge)
            c.moves.forEach { e ->
                // short columns never wrap; the name takes what's left and ends in … if it must
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(e.move.name, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TypeChip(e.move.type)
                    Text(when (e.move.category) { "physical" -> "Phys"; "special" -> "Spec"; "status" -> "Stat"; else -> "?" },
                        style = small, softWrap = false)
                    Text(e.move.power?.toString() ?: "—", style = small, softWrap = false, modifier = Modifier.widthIn(min = 24.dp))
                    Text(if (e.move.category == "status") "" else mult(e.multiplier), color = multColor(e.multiplier),
                        fontWeight = FontWeight.Bold, softWrap = false, modifier = Modifier.widthIn(min = 28.dp))
                    // after my status (burn halves physical moves): normal → now
                    if (c.myStatus != null && e.move.category != "status")
                        Text(if (e.adjusted != e.multiplier) "→ ${mult(e.adjusted)}" else "", color = multColor(e.adjusted),
                            fontWeight = FontWeight.Bold, softWrap = false, modifier = Modifier.widthIn(min = 36.dp))
                }
            }
        }
        opp?.species?.let { s ->
            StatsLine("Its base stats", s)
            if (c.oppAbilities.isNotEmpty()) {
                Text(if (c.oppAbilities.size == 1) "Ability" else "Ability (one of)", style = MaterialTheme.typography.labelLarge)
                c.oppAbilities.forEach { a ->
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(a.name) }
                        if (a.hidden) append(" (hidden)")
                        a.text?.let { append(" — $it") }
                    }, style = small)
                }
            }
            if (s.evolutionLine.size > 1) Text(s.evolutionLine.joinToString(" → "), style = small)
        }
        val me = c.player
        when {
            c.nicknamePrompt != null -> NicknamePrompt(c.nicknamePrompt)
            me?.species != null -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    Text("You: ${me.species.name}", style = MaterialTheme.typography.labelLarge, softWrap = false)
                    me.species.types.forEach { TypeChip(it) }
                    c.myStatus?.let { StatusChip(it) }
                }
                c.myStatusNotes.forEach { Text("${c.myStatus!!.label}: $it", style = small, color = Color(0xFFFFD180)) }
                StatsLine("Your base stats", me.species)
            }
        }
    }
}

@Composable
private fun StatsLine(label: String, s: Species) {
    val names = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")
    Text("$label: " + s.stats.zip(names).joinToString("  ") { (v, n) -> "$n $v" } + "  · BST ${s.stats.sum()}",
        style = MaterialTheme.typography.bodySmall)
}

/** Asked once per nickname; the answer is saved for this game (S4). */
@Composable
private fun NicknamePrompt(nick: String) {
    val ctx = LocalContext.current
    var query by remember(nick) { mutableStateOf("") }
    val all = remember {
        val prefs = ctx.questPrefs()
        PokeDataRepository.get(ctx, PokemonSettings.generation(prefs, PokemonSettings.profile(ctx, prefs))).species.map { it.name }
    }
    Text("Which Pokémon is \"$nick\"?", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Species") },
        modifier = Modifier.fillMaxWidth())
    if (query.length >= 2) {
        all.filter { it.contains(query, ignoreCase = true) }.take(5).forEach { name ->
            TextButton(onClick = { PokemonEngine.saveNickname(nick, name) }, modifier = Modifier.heightIn(min = TARGET)) { Text(name) }
        }
    }
}

/** Calibration mode: the game's battle regions drawn over the video (S4). */
@Composable
fun CalibrationOverlay(aspect: Float) {
    val ctx = LocalContext.current
    val on by rememberPref(Prefs.POKEMON_CALIBRATION)
    val profileId by rememberPref(Prefs.POKEMON_PROFILE)
    if (!on) return
    val layout = remember(profileId) { PokemonSettings.profile(ctx, ctx.questPrefs()).layout } ?: return
    val colors = listOf(Color.Red, Color.Green, Color.Cyan, Color.Yellow, Color.Magenta, Color.White, Color(0xFFFF9800))
    Canvas(Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f).fillMaxSize()) {
        layout.named().forEachIndexed { i, (_, r) ->
            drawRect(colors[i % colors.size], Offset(r.left * size.width, r.top * size.height),
                Size(r.width * size.width, r.height * size.height), style = Stroke(width = 3f))
        }
    }
}
