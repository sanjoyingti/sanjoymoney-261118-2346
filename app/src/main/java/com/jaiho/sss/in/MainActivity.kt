package com.jaiho.sss.`in`

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaiho.sss.`in`.ui.MainViewModel
import com.jaiho.sss.`in`.ui.SanjoyRoot
import com.jaiho.sss.`in`.ui.theme.SanjoyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SanjoyApp).container
        setContent {
            SanjoyTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModel.factory(container))
                SanjoyRoot(vm)
            }
        }
    }
}
