package com.jaiho.sss.`in`.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jaiho.sss.`in`.ui.screens.CalibrationScreen
import com.jaiho.sss.`in`.ui.screens.HomeScreen
import com.jaiho.sss.`in`.ui.screens.SettingsScreen

@Composable
fun SanjoyRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onSettings = { nav.navigate("settings") },
                onCalibrate = { nav.navigate("calibrate") }
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("calibrate") {
            CalibrationScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
