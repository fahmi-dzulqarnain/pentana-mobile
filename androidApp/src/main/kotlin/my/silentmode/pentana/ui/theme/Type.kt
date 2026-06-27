package my.silentmode.pentana.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto Flex is the design font; FontFamily.Default resolves to Roboto on Android
// (the spec allows falling back to system Roboto rather than bundling the variable font).
private val Ro = FontFamily.Default

// M3 type scale mapped from tokens.css (size / line-height / weight / letter-spacing).
val PentTypography = Typography(
    displayLarge = TextStyle(fontFamily = Ro, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.W400, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Ro, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.W400),
    displaySmall = TextStyle(fontFamily = Ro, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.W400),
    headlineLarge = TextStyle(fontFamily = Ro, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.W400),
    headlineMedium = TextStyle(fontFamily = Ro, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.W500, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = Ro, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.W500),
    titleLarge = TextStyle(fontFamily = Ro, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.W500, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = Ro, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Ro, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Ro, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.W400),
    bodyMedium = TextStyle(fontFamily = Ro, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W400, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = Ro, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.W400, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = Ro, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Ro, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Ro, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600, letterSpacing = 0.4.sp),
)

// Money / numeric styles — tabular figures so columns of money align.
val MoneyLarge = TextStyle(fontFamily = Ro, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum")
val MoneyMedium = TextStyle(fontFamily = Ro, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600, fontFeatureSettings = "tnum")
