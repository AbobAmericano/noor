package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NoorDarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = DeepGoldText,
    primaryContainer = GoldContainerDark,
    onPrimaryContainer = SoftGoldText,
    secondary = EmeraldAccent,
    onSecondary = OnEmeraldDark,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = MintMistText,
    tertiary = EmeraldAccent,
    background = ObsidianNight,
    onBackground = WarmIvoryText,
    surface = DeepPineSurface,
    onSurface = WarmIvoryText,
    surfaceVariant = ElevatedPineSurface,
    onSurfaceVariant = MutedSageText,
    surfaceContainerLowest = ObsidianNight,
    surfaceContainerLow = DeepPineSurface,
    surfaceContainer = ElevatedPineSurface,
    surfaceContainerHigh = HighestPineSurface,
    outline = SubtlePineBorder,
    outlineVariant = SubtlePineBorder,
    error = SoftCoralErrorDark
)

private val NoorLightColorScheme = lightColorScheme(
    primary = BurnishedGold,
    onPrimary = Color.White,
    primaryContainer = GoldContainerLight,
    onPrimaryContainer = DeepAmberText,
    secondary = DeepEmerald,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = DeepForestText,
    tertiary = DeepEmerald,
    background = ParchmentLight,
    onBackground = ForestInkText,
    surface = WarmLinenSurface,
    onSurface = ForestInkText,
    surfaceVariant = ElevatedLinenSurface,
    onSurfaceVariant = MutedSlateGreen,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = ParchmentLight,
    surfaceContainer = WarmLinenSurface,
    surfaceContainerHigh = ElevatedLinenSurface,
    outline = SubtleLinenBorder,
    outlineVariant = SubtleLinenBorder,
    error = SoftCoralErrorLight
)

val NoorShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NoorTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NoorDarkColorScheme else NoorLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NoorTypography,
        shapes = NoorShapes,
        content = content
    )
}
