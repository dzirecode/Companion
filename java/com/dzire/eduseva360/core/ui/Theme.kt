package com.dzire.eduseva360.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EduColors = lightColorScheme(
    primary = Color(0xFF2457D6),
    secondary = Color(0xFF536DFE),
    tertiary = Color(0xFF00897B),
    background = Color(0xFFF7F9FC),
    surface = Color.White
)

@Composable
fun EduSevaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EduColors,
        typography = Typography(),
        content = content
    )
}
