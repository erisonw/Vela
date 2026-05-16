package com.vela.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vela.app.navigation.VelaNavHost
import com.vela.app.ui.theme.VelaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VelaTheme {
                VelaNavHost()
            }
        }
    }
}
