package my.silentmode.pentana.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = PrimaryL, onPrimary = OnPrimaryL, primaryContainer = PrimaryContainerL, onPrimaryContainer = OnPrimaryContainerL,
    secondary = SecondaryL, onSecondary = OnSecondaryL, secondaryContainer = SecondaryContainerL, onSecondaryContainer = OnSecondaryContainerL,
    tertiary = TertiaryL, tertiaryContainer = TertiaryContainerL, onTertiaryContainer = OnTertiaryContainerL,
    error = ErrorL, onError = OnErrorL, errorContainer = ErrorContainerL, onErrorContainer = OnErrorContainerL,
    surface = SurfaceL, onSurface = OnSurfaceL, onSurfaceVariant = OnSurfaceVariantL,
    outline = OutlineL, outlineVariant = OutlineVariantL,
    surfaceContainerLowest = ScLowestL, surfaceContainerLow = ScLowL, surfaceContainer = ScL,
    surfaceContainerHigh = ScHighL, surfaceContainerHighest = ScHighestL, surfaceDim = SurfaceDimL,
    background = SurfaceL, onBackground = OnSurfaceL,
)

private val DarkScheme = darkColorScheme(
    primary = PrimaryD, onPrimary = OnPrimaryD, primaryContainer = PrimaryContainerD, onPrimaryContainer = OnPrimaryContainerD,
    secondary = SecondaryD, onSecondary = OnSecondaryD, secondaryContainer = SecondaryContainerD, onSecondaryContainer = OnSecondaryContainerD,
    tertiary = TertiaryD, tertiaryContainer = TertiaryContainerD, onTertiaryContainer = OnTertiaryContainerD,
    error = ErrorD, onError = OnErrorD, errorContainer = ErrorContainerD, onErrorContainer = OnErrorContainerD,
    surface = SurfaceDarkD, onSurface = OnSurfaceD, onSurfaceVariant = OnSurfaceVariantD,
    outline = OutlineD, outlineVariant = OutlineVariantD,
    surfaceContainerLowest = ScLowestD, surfaceContainerLow = ScLowD, surfaceContainer = ScD,
    surfaceContainerHigh = ScHighD, surfaceContainerHighest = ScHighestD, surfaceBright = SurfaceBrightD,
    background = SurfaceDarkD, onBackground = OnSurfaceD,
)

@Composable
fun PentanaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalPentanaColors provides if (dark) DarkPentana else LightPentana) {
        MaterialTheme(colorScheme = scheme, typography = PentTypography, shapes = PentShapes, content = content)
    }
}
