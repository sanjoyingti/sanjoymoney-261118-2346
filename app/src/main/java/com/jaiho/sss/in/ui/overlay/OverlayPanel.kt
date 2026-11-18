=== ABOUT: The control panel that drops down from the gold bubble — every switch, the speed slider, the bet stepper, the counters and the live log ===
package com.jaiho.sss.in.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaiho.sss.in.R
import com.jaiho.sss.in.bot.BotEngine
import com.jaiho.sss.in.data.Settings
import com.jaiho.sss.in.data.SettingsStore
import com.jaiho.sss.in.ui.components.SectionTitle
import com.jaiho.sss.in.ui.theme.CardBg
import com.jaiho.sss.in.ui.theme.CardLine
import com.jaiho.sss.in.ui.theme.Gold
import com.jaiho.sss.in.ui.theme.Green
import com.jaiho.sss.in.ui.theme.Ink
import com.jaiho.sss.in.ui.theme.InkSoft
import com.jaiho.sss.in.ui.theme.TextMain
import com.jaiho.sss.in.ui.theme.TextMuted
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun OverlayPanel(
    store: SettingsStore,
    engine: BotEngine,
    scope: CoroutineScope,
    onCollapse: () -> Unit,
    onCalibrate: () -> Unit,
    onStopBot: () -> Unit,
    onToast: (String) -> Unit
) {
    val settings by store.settings.collectAsState(Settings())
    val stats by engine.stats.collectAsState()
    val log by engine.log.collectAsState()

    val spinOn = stringResource(R.string.toast_spin_on)
    val spinOff = stringResource(R.string.toast_spin_off)
    val countersReset = stringResource(R.string.counters_reset)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, CardLine, RoundedCornerShape(20.dp))
            .padding(14.dp)
            .heightIn(max = 470.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.panel_title),
                color = Gold,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCalibrate, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.CenterFocusStrong,
                    contentDescription = stringResource(R.string.panel_calibrate),
                    tint = Gold
                )
            }
            IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.panel_collapse),
                    tint = TextMuted
                )
            }
        }

        Text(stringResource(R.string.panel_hold), color = TextMuted, style = MaterialTheme.typography.bodySmall)

        ToggleRow(
            title = stringResource(R.string.toggle_auto_spin),
            checked = engine.active.collectAsState().value,
            accent = Green
        ) { value ->
            scope.launch { store.update { it.copy(autoSpin = value) } }
            engine.setAutoSpin(value)
            onToast(if (value) spinOn else spinOff)
        }

        ToggleRow(
            title = stringResource(R.string.toggle_always_win),
            checked = settings.alwaysWin
        ) { value -> scope.launch { store.update { it.copy(alwaysWin = value) } } }

        ToggleRow(
            title = stringResource(R.string.toggle_jackpot_hunter),
            checked = settings.jackpotHunter
        ) { value -> scope.launch { store.update { it.copy(jackpotHunter = value) } } }

        ToggleRow(
            title = stringResource(R.string.toggle_stealth),
            checked = settings.stealth
        ) { value -> scope.launch { store.update { it.copy(stealth = value) } } }

        ToggleRow(
            title = stringResource(R.string.toggle_vibration),
            checked = settings.vibrate
        ) { value -> scope.launch { store.update { it.copy(vibrate = value) } } }

        ToggleRow(
            title = stringResource(R.string.toggle_jackpot_toast),
            checked = settings.jackpotToast
        ) { value -> scope.launch { store.update { it.copy(jackpotToast = value) } } }

        Text(
            text = stringResource(R.string.interval_label, settings.intervalMs),
            color = TextMain,
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = settings.intervalMs.toFloat(),
            onValueChange = { raw -> scope.launch { store.update { it.copy(intervalMs = raw.toInt()) } } },
            valueRange = Settings.MIN_INTERVAL.toFloat()..Settings.MAX_INTERVAL.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Gold,
                activeTrackColor = Gold,
                inactiveTrackColor = CardLine
            )
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.bet_label),
                color = TextMain,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    scope.launch {
                        store.update { it.copy(betLevel = (it.betLevel - 1).coerceAtLeast(Settings.MIN_BET)) }
                    }
                }
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.bet_minus), tint = Gold)
            }
            Text(
                text = stringResource(R.string.bet_value, settings.betLevel),
                color = Gold,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    scope.launch {
                        store.update { it.copy(betLevel = (it.betLevel + 1).coerceAtMost(Settings.MAX_BET)) }
                    }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.bet_plus), tint = Gold)
            }
        }

        SectionTitle(stringResource(R.string.counters))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell(stringResource(R.string.counter_spins), stats.spins.toString(), Modifier.weight(1f))
            StatCell(stringResource(R.string.counter_wins), stats.wins.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell(stringResource(R.string.counter_near), stats.nearMisses.toString(), Modifier.weight(1f))
            StatCell(stringResource(R.string.counter_jackpots), stats.jackpots.toString(), Modifier.weight(1f))
        }
        StatCell(
            stringResource(R.string.counter_biggest),
            String.format(Locale.US, "%.2f", stats.biggestWin),
            Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = {
                    engine.resetStats()
                    onToast(countersReset)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.reset_counters), color = Gold)
            }
            TextButton(onClick = onStopBot, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Stop, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stop_bot), color = TextMuted)
            }
        }

        SectionTitle(stringResource(R.string.log_title))
        if (log.isEmpty()) {
            Text(stringResource(R.string.log_empty), color = TextMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(InkSoft)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(log) { line ->
                    Text(
                        text = line,
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Button(
            onClick = onCollapse,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
        ) {
            Text(stringResource(R.string.panel_collapse), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    accent: androidx.compose.ui.graphics.Color = Gold,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = TextMain, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = accent
            )
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(InkSoft)
            .padding(10.dp)
    ) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}
