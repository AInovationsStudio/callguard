package studio.ainovations.callguard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BrandColors {
    val Canvas = Color(0xFFF7F6F3)
    val Ink = Color(0xFF12110F)
    val InkSoft = Color(0xFF55534E)
    val Hairline = Color(0x1A12110F)
    val IrisIndigo = Color(0xFF6366F1)
    val IrisCyan = Color(0xFF06B6D4)
    val IrisLime = Color(0xFFD9F99D)
    val IrisRose = Color(0xFFFFD1DC)
}

private val BrandColorScheme = lightColorScheme(
    primary = BrandColors.Ink,
    onPrimary = BrandColors.Canvas,
    primaryContainer = BrandColors.Ink,
    onPrimaryContainer = BrandColors.Canvas,
    secondary = BrandColors.IrisIndigo,
    onSecondary = Color.White,
    secondaryContainer = BrandColors.IrisRose,
    onSecondaryContainer = BrandColors.Ink,
    tertiary = BrandColors.IrisIndigo,
    onTertiary = Color.White,
    background = BrandColors.Canvas,
    onBackground = BrandColors.Ink,
    surface = BrandColors.Canvas,
    onSurface = BrandColors.Ink,
    surfaceVariant = Color.White.copy(alpha = 0.72f),
    onSurfaceVariant = BrandColors.InkSoft,
    outline = BrandColors.Hairline,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val BrandTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.03).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        color = BrandColors.InkSoft,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.08.sp,
    ),
)

@Composable
fun CallGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrandColorScheme,
        typography = BrandTypography,
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}
