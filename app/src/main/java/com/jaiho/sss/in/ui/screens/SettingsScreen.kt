package com.jaiho.sss.`in`.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaiho.sss.`in`.R
import com.jaiho.sss.`in`.data.Settings
import com.jaiho.sss.`in`.ui.MainViewModel
import com.jaiho.sss.`in`.ui.components.GoldButton
import com.jaiho.sss.`in`.ui.components.SectionCard
import com.jaiho.sss.`in`.ui.components.SectionTitle
import com.jaiho.sss.`in`.ui.theme.Gold
import com.jaiho.sss.`in`.ui.theme.TextMuted
import com.jaiho.sss.`in`.util.SystemUtils

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var showReset by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_settings), tint = Gold)
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Gold,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = stringResource(R.string.settings_defaults, Settings().intervalMs, Settings().betLevel),
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall
        )

        SectionCard {
            SectionTitle(stringResource(R.string.settings_title))
            SwitchRow(
                title = stringResource(R.string.keep_screen_on),
                hint = stringResource(R.string.keep_screen_on_hint),
                checked = settings.keepScreenOn,
                onChange = vm::setKeepScreenOn
            )
            SwitchRow(
                title = stringResource(R.string.auto_start_with_game),
                hint = stringResource(R.string.auto_start_with_game_hint),
                checked = settings.autoStartWithGame,
                onChange = vm::setAutoStartWithGame
            )
            SwitchRow(
                title = stringResource(R.string.start_on_boot),
                hint = stringResource(R.string.start_on_boot_hint),
                checked = settings.startOnBoot,
                onChange = vm::setStartOnBoot
            )
        }

        SectionCard {
            SectionTitle(stringResource(R.string.log_verbosity))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0 to stringResource(R.string.verbosity_off),
                    1 to stringResource(R.string.verbosity_basic),
                    2 to stringResource(R.string.verbosity_verbose)
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = settings.logVerbosity == value,
                        onClick = { vm.setLogVerbosity(value) },
                        label = { Text(label) }
                    )
                }
            }
        }

        GoldButton(
            text = stringResource(R.string.restore_defaults),
            onClick = {
                vm.restoreDefaults()
                restored = true
            }
        )
        if (restored) {
            Text(stringResource(R.string.defaults_restored), color = Gold, style = MaterialTheme.typography.bodySmall)
        }

        GoldButton(
            text = stringResource(R.string.reset_all),
            onClick = { showReset = true }
        )

        SectionCard {
            SectionTitle(stringResource(R.string.about_title))
            Text(
                text = stringResource(R.string.about_version, SystemUtils.VERSION),
                color = Gold,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.about_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.reset_all_title)) },
            text = { Text(stringResource(R.string.reset_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetEverything()
                    showReset = false
                }) { Text(stringResource(R.string.reset_all_confirm), color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
