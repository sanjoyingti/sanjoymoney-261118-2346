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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.jaiho.sss.`in`.ui.MainViewModel
import com.jaiho.sss.`in`.ui.components.GoldButton
import com.jaiho.sss.`in`.ui.components.SectionCard
import com.jaiho.sss.`in`.ui.components.SectionTitle
import com.jaiho.sss.`in`.ui.theme.Gold
import com.jaiho.sss.`in`.ui.theme.TextMuted

@Composable
fun CalibrationScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var needsOverlay by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_calibrate),
                    tint = Gold
                )
            }
            Text(
                text = stringResource(R.string.calibrate_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Gold,
                fontWeight = FontWeight.Bold
            )
        }

        SectionCard {
            SectionTitle(stringResource(R.string.calibrate_title))
            Text(stringResource(R.string.calibrate_body), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (settings.spinCalibrated || settings.collectCalibrated) {
                    stringResource(
                        R.string.calibrate_current,
                        (settings.spinX * 100).toInt(),
                        (settings.spinY * 100).toInt(),
                        (settings.collectX * 100).toInt(),
                        (settings.collectY * 100).toInt()
                    )
                } else {
                    stringResource(R.string.calibrate_not_set)
                },
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        GoldButton(
            text = stringResource(R.string.calibrate_now),
            onClick = {
                if (vm.startOverlayForCalibration()) {
                    vm.openGame()
                    needsOverlay = false
                } else {
                    needsOverlay = true
                }
            }
        )

        if (needsOverlay) {
            Text(
                text = stringResource(R.string.calibrate_need_overlay),
                color = Gold,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            text = stringResource(R.string.calibrate_open_panel),
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
