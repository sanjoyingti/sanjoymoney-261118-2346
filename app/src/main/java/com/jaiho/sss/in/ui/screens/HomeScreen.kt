package com.jaiho.sss.`in`.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jaiho.sss.`in`.R
import com.jaiho.sss.`in`.ui.MainViewModel
import com.jaiho.sss.`in`.ui.components.GoldButton
import com.jaiho.sss.`in`.ui.components.SectionCard
import com.jaiho.sss.`in`.ui.components.SectionTitle
import com.jaiho.sss.`in`.ui.components.StatusRow
import com.jaiho.sss.`in`.ui.theme.Gold
import com.jaiho.sss.`in`.ui.theme.Green
import com.jaiho.sss.`in`.ui.theme.TextMuted
import com.jaiho.sss.`in`.util.SystemUtils

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onSettings: () -> Unit,
    onCalibrate: () -> Unit
) {
    val context = LocalContext.current
    val perms by vm.perms.collectAsState()
    val active by vm.active.collectAsState()
    val overlayRunning by vm.overlayRunning.collectAsState()
    val installed by vm.gameInstalled.collectAsState()

    var showMissing by remember { mutableStateOf(false) }
    var showGameMissing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val textOverlay = stringResource(R.string.req_overlay)
    val textAccessibility = stringResource(R.string.req_accessibility)
    val textBattery = stringResource(R.string.req_battery)

    val missing: List<String> = listOfNotNull(
        if (!perms.overlay) textOverlay else null,
        if (!perms.accessibility) textAccessibility else null,
        if (!perms.battery) textBattery else null
    )

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        vm.startBot()
        vm.openGame()
    }

    fun launchGameOrWarn() {
        if (!vm.openGame()) showGameMissing = true
    }

    fun attemptStart() {
        if (!perms.complete) {
            showMissing = true
            return
        }
        val needsNotification = SystemUtils.needsNotificationPermission() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotification) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        vm.startBot()
        launchGameOrWarn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Gold,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            IconButton(onClick = onCalibrate) {
                Icon(
                    Icons.Filled.CenterFocusStrong,
                    contentDescription = stringResource(R.string.nav_calibrate),
                    tint = Gold
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = Gold
                )
            }
        }

        Text(
            text = if (installed) stringResource(R.string.game_found) else stringResource(R.string.game_missing),
            color = if (installed) Green else TextMuted,
            style = MaterialTheme.typography.bodySmall
        )

        GoldButton(text = stringResource(R.string.open_game), onClick = { launchGameOrWarn() })

        SectionCard {
            SectionTitle(stringResource(R.string.checklist_title))
            StatusRow(
                title = textOverlay,
                hint = stringResource(R.string.req_overlay_hint),
                granted = perms.overlay,
                openLabel = stringResource(R.string.open_action),
                onOpen = { vm.openOverlaySettings() }
            )
            StatusRow(
                title = textAccessibility,
                hint = stringResource(R.string.req_accessibility_hint),
                granted = perms.accessibility,
                openLabel = stringResource(R.string.open_action),
                onOpen = { vm.openAccessibilitySettings() }
            )
            StatusRow(
                title = textBattery,
                hint = stringResource(R.string.req_battery_hint),
                granted = perms.battery,
                openLabel = stringResource(R.string.open_action),
                onOpen = { vm.openBatterySettings() }
            )
        }

        GoldButton(
            text = stringResource(R.string.start_bot),
            enabled = !overlayRunning,
            onClick = { attemptStart() }
        )

        if (overlayRunning || active) {
            SectionCard {
                Text(stringResource(R.string.bot_running), color = Green, fontWeight = FontWeight.SemiBold)
                GoldButton(text = stringResource(R.string.stop_bot), onClick = { vm.stopBot() })
            }
        } else {
            Text(stringResource(R.string.bot_stopped), color = TextMuted)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Stop, contentDescription = null, tint = TextMuted)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = {
                vm.copyPackage()
                copied = true
            }) {
                Text(stringResource(R.string.copy_package), color = Gold)
            }
        }
        if (copied) {
            Text(
                text = stringResource(R.string.package_copied),
                color = Gold,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showMissing) {
        AlertDialog(
            onDismissRequest = { showMissing = false },
            title = { Text(stringResource(R.string.missing_title)) },
            text = { Text(stringResource(R.string.missing_body, missing.joinToString("\n"))) },
            confirmButton = {
                TextButton(onClick = { showMissing = false }) {
                    Text(stringResource(R.string.missing_ok), color = Gold)
                }
            }
        )
    }

    if (showGameMissing) {
        AlertDialog(
            onDismissRequest = { showGameMissing = false },
            title = { Text(stringResource(R.string.game_missing_title)) },
            text = { Text(stringResource(R.string.game_missing_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.openPlayStore()
                    showGameMissing = false
                }) { Text(stringResource(R.string.play_store), color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.copyPackage()
                    copied = true
                    showGameMissing = false
                }) { Text(stringResource(R.string.copy_package)) }
            }
        )
    }
}
