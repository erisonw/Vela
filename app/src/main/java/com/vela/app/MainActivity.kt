package com.vela.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.navigation.VelaNavHost
import com.vela.app.ui.theme.VelaTheme
import com.vela.app.widget.VelaWidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MockVelaRepository.initialize(this)
        lifecycleScope.launch {
            VelaWidgetUpdater.updateAll(this@MainActivity)
        }
        setContent {
            VelaTheme {
                VelaNavHost()
            }
        }
    }
}
