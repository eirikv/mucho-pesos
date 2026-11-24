package vige.land.muchopesos.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Refined Emerald & Slate fallback palette for Mucho Pesos (when Dynamic Color is disabled)
val EmeraldPrimaryLight = Color(0xFF006B58)
val EmeraldOnPrimaryLight = Color(0xFFFFFFFF)
val EmeraldPrimaryContainerLight = Color(0xFF9AF4DC)
val EmeraldOnPrimaryContainerLight = Color(0xFF002019)

val SlateSecondaryLight = Color(0xFF4A635C)
val SlateOnSecondaryLight = Color(0xFFFFFFFF)
val SlateSecondaryContainerLight = Color(0xFFCCE8DF)
val SlateOnSecondaryContainerLight = Color(0xFF06201A)

val WarmAmberTertiaryLight = Color(0xFF426277)
val WarmAmberOnTertiaryLight = Color(0xFFFFFFFF)
val WarmAmberTertiaryContainerLight = Color(0xFFC7E7FF)
val WarmAmberOnTertiaryContainerLight = Color(0xFF001E2E)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFF6FBF8)
val OnBackgroundLight = Color(0xFF171D1B)
val SurfaceLight = Color(0xFFF6FBF8)
val OnSurfaceLight = Color(0xFF171D1B)
val SurfaceVariantLight = Color(0xFFDBE5E0)
val OnSurfaceVariantLight = Color(0xFF3F4945)
val OutlineLight = Color(0xFF6F7975)
val OutlineVariantLight = Color(0xFFBFC9C4)

val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF0F5F2)
val SurfaceContainerLight = Color(0xFFEAEFEC)
val SurfaceContainerHighLight = Color(0xFFE5EAE7)
val SurfaceContainerHighestLight = Color(0xFFDFE4E1)

// Dark palette
val EmeraldPrimaryDark = Color(0xFF7ED8C0)
val EmeraldOnPrimaryDark = Color(0xFF00382D)
val EmeraldPrimaryContainerDark = Color(0xFF005142)
val EmeraldOnPrimaryContainerDark = Color(0xFF9AF4DC)

val SlateSecondaryDark = Color(0xFFB1CCC3)
val SlateOnSecondaryDark = Color(0xFF1D352F)
val SlateSecondaryContainerDark = Color(0xFF334B45)
val SlateOnSecondaryContainerDark = Color(0xFFCCE8DF)

val WarmAmberTertiaryDark = Color(0xFFA9CBE3)
val WarmAmberOnTertiaryDark = Color(0xFF0F3447)
val WarmAmberTertiaryContainerDark = Color(0xFF2A4A5F)
val WarmAmberOnTertiaryContainerDark = Color(0xFFC7E7FF)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF0F1513)
val OnBackgroundDark = Color(0xFFDFE4E1)
val SurfaceDark = Color(0xFF0F1513)
val OnSurfaceDark = Color(0xFFDFE4E1)
val SurfaceVariantDark = Color(0xFF3F4945)
val OnSurfaceVariantDark = Color(0xFFBFC9C4)
val OutlineDark = Color(0xFF89938F)
val OutlineVariantDark = Color(0xFF3F4945)

val SurfaceContainerLowestDark = Color(0xFF0A0F0E)
val SurfaceContainerLowDark = Color(0xFF171D1B)
val SurfaceContainerDark = Color(0xFF1B211F)
val SurfaceContainerHighDark = Color(0xFF252B29)
val SurfaceContainerHighestDark = Color(0xFF303634)

val LightFallbackColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldPrimaryContainerLight,
    onPrimaryContainer = EmeraldOnPrimaryContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = SlateOnSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = SlateOnSecondaryContainerLight,
    tertiary = WarmAmberTertiaryLight,
    onTertiary = WarmAmberOnTertiaryLight,
    tertiaryContainer = WarmAmberTertiaryContainerLight,
    onTertiaryContainer = WarmAmberOnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
)

val DarkFallbackColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldPrimaryContainerDark,
    onPrimaryContainer = EmeraldOnPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    tertiary = WarmAmberTertiaryDark,
    onTertiary = WarmAmberOnTertiaryDark,
    tertiaryContainer = WarmAmberTertiaryContainerDark,
    onTertiaryContainer = WarmAmberOnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
)

/**
 * Semantic financial colors kept consistent even when Material You Dynamic Color is active,
 * so "owed to you" (emerald), "you owe" (coral/terracotta), and "settled up" (teal/slate)
 * always communicate clear monetary direction with accessible contrast.
 */
@Immutable
data class FinanceColors(
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val positiveText: Color,
    val negativeContainer: Color,
    val onNegativeContainer: Color,
    val negativeText: Color,
    val settledContainer: Color,
    val onSettledContainer: Color,
    val settledText: Color,
    val demoContainer: Color,
    val onDemoContainer: Color,
    val categoryFoodContainer: Color,
    val onCategoryFoodContainer: Color,
    val categoryHousingContainer: Color,
    val onCategoryHousingContainer: Color,
    val categoryTransportContainer: Color,
    val onCategoryTransportContainer: Color,
    val categoryUtilitiesContainer: Color,
    val onCategoryUtilitiesContainer: Color,
    val categoryEntertainmentContainer: Color,
    val onCategoryEntertainmentContainer: Color,
    val categoryOtherContainer: Color,
    val onCategoryOtherContainer: Color,
)

val LightFinanceColors = FinanceColors(
    positiveContainer = Color(0xFFD6F5E7),
    onPositiveContainer = Color(0xFF003826),
    positiveText = Color(0xFF0A6E4D),
    negativeContainer = Color(0xFFFFE1DC),
    onNegativeContainer = Color(0xFF4B110B),
    negativeText = Color(0xFFB33224),
    settledContainer = Color(0xFFDDF0F5),
    onSettledContainer = Color(0xFF083542),
    settledText = Color(0xFF145B6E),
    demoContainer = Color(0xFFFFEDCA),
    onDemoContainer = Color(0xFF4D3400),
    categoryFoodContainer = Color(0xFFFFE5D0),
    onCategoryFoodContainer = Color(0xFF632B00),
    categoryHousingContainer = Color(0xFFDCEBFF),
    onCategoryHousingContainer = Color(0xFF0A3463),
    categoryTransportContainer = Color(0xFFE3E0FF),
    onCategoryTransportContainer = Color(0xFF28206B),
    categoryUtilitiesContainer = Color(0xFFFFF0C4),
    onCategoryUtilitiesContainer = Color(0xFF574100),
    categoryEntertainmentContainer = Color(0xFFFADDF7),
    onCategoryEntertainmentContainer = Color(0xFF5D1357),
    categoryOtherContainer = Color(0xFFE0ECE8),
    onCategoryOtherContainer = Color(0xFF223B34),
)

val DarkFinanceColors = FinanceColors(
    positiveContainer = Color(0xFF0F3E2E),
    onPositiveContainer = Color(0xFFA7F3D0),
    positiveText = Color(0xFF6EE7B7),
    negativeContainer = Color(0xFF4E1E19),
    onNegativeContainer = Color(0xFFFFC9C2),
    negativeText = Color(0xFFFF8E80),
    settledContainer = Color(0xFF153742),
    onSettledContainer = Color(0xFFB8E9F7),
    settledText = Color(0xFF7DD3EC),
    demoContainer = Color(0xFF4A3508),
    onDemoContainer = Color(0xFFFFDF9B),
    categoryFoodContainer = Color(0xFF4B2810),
    onCategoryFoodContainer = Color(0xFFFFCBA4),
    categoryHousingContainer = Color(0xFF123254),
    onCategoryHousingContainer = Color(0xFFB3D7FF),
    categoryTransportContainer = Color(0xFF282357),
    onCategoryTransportContainer = Color(0xFFC8C2FF),
    categoryUtilitiesContainer = Color(0xFF47370A),
    onCategoryUtilitiesContainer = Color(0xFFFFE18F),
    categoryEntertainmentContainer = Color(0xFF4C1748),
    onCategoryEntertainmentContainer = Color(0xFFF6B7F0),
    categoryOtherContainer = Color(0xFF243832),
    onCategoryOtherContainer = Color(0xFFC1DDD4),
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }
