package com.dzire.dzirecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dzire.dzirecompanion.ui.CompanionDashboard
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                CompanionDashboard()
            }
        }
    }
}
