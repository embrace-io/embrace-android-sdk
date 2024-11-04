package io.embrace.android.exampleapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EmbraceColorScheme = lightColorScheme(
    primary = EmbraceBlack,
    secondary = EmbraceYellow,
    tertiary = EmbraceYellow

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun ExampleAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EmbraceColorScheme,
        typography = Typography,
        content = content
    )
}
