package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = VibrantPrimary,
    onPrimary = VibrantOnPrimary,
    secondary = VibrantSecondary,
    tertiary = VibrantTertiary,
    background = VibrantBackground,
    surface = VibrantSurface,
    surfaceVariant = VibrantSurfaceVariant,
    outline = VibrantOutline,
    onBackground = VibrantOnBackground,
    onSurface = VibrantOnSurface,
    onSurfaceVariant = Color(0xFFCAC4D0)
  )

private val LightColorScheme =
  darkColorScheme( // Use dark scheme in light mode for a premium unified cinema streaming player experience
    primary = VibrantPrimary,
    onPrimary = VibrantOnPrimary,
    secondary = VibrantSecondary,
    tertiary = VibrantTertiary,
    background = VibrantBackground,
    surface = VibrantSurface,
    surfaceVariant = VibrantSurfaceVariant,
    outline = VibrantOutline,
    onBackground = VibrantOnBackground,
    onSurface = VibrantOnSurface,
    onSurfaceVariant = Color(0xFFCAC4D0)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to enforce the gorgeous custom "Vibrant Palette" theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
