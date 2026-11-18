package com.jaiho.sss.`in`.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jaiho.sss.`in`.SanjoyApp
import com.jaiho.sss.`in`.util.SystemUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as? SanjoyApp ?: return
        val pending = goAsync()
        app.container.scope.launch {
            try {
                val settings = app.container.store.settings.first()
                if (!settings.startOnBoot) return@launch
                if (!SystemUtils.isOverlayGranted(context)) return@launch
                if (app.container.startOverlay()) {
                    app.container.engine.setAutoSpin(settings.autoSpin)
                }
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
