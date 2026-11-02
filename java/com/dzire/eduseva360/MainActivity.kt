package com.dzire.eduseva360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.dzire.eduseva360.core.ui.EduSevaTheme
import com.dzire.eduseva360.core.navigation.EduSevaNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EduSevaTheme {
                EduSevaNavHost()
            }
        }
    }
}
