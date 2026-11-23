package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.StorageCategory

@Immutable
data class StorageSemanticColors(
    val images: Color,
    val videos: Color,
    val audio: Color,
    val documents: Color,
    val other: Color,
    val freeSpace: Color,
    val warning: Color
) {
    fun colorFor(category: StorageCategory): Color = when (category) {
        StorageCategory.IMAGES -> images
        StorageCategory.VIDEOS -> videos
        StorageCategory.AUDIO -> audio
        StorageCategory.DOCUMENTS -> documents
        StorageCategory.OTHER -> other
    }
}

val LocalStorageColors = staticCompositionLocalOf {
    StorageSemanticColors(
        images = CatImagesLight,
        videos = CatVideosLight,
        audio = CatAudioLight,
        documents = CatDocumentsLight,
        other = CatOtherLight,
        freeSpace = CatFreeLight,
        warning = CatWarningLight
    )
}

private val LightStorageSemanticColors = StorageSemanticColors(
    images = CatImagesLight,
    videos = CatVideosLight,
    audio = CatAudioLight,
    documents = CatDocumentsLight,
    other = CatOtherLight,
    freeSpace = CatFreeLight,
    warning = CatWarningLight
)

private val DarkStorageSemanticColors = StorageSemanticColors(
    images = CatImagesDark,
    videos = CatVideosDark,
    audio = CatAudioDark,
    documents = CatDocumentsDark,
    other = CatOtherDark,
    freeSpace = CatFreeDark,
    warning = CatWarningDark
)

private val DarkColorScheme = darkColorScheme(
    primary = LensBluePrimaryDark,
    onPrimary = LensOnPrimaryDark,
    primaryContainer = LensPrimaryContainerDark,
    onPrimaryContainer = LensOnPrimaryContainerDark,
    secondary = LensCyanSecondaryDark,
    onSecondary = LensOnSecondaryDark,
    secondaryContainer = LensSecondaryContainerDark,
    onSecondaryContainer = LensOnSecondaryContainerDark,
    tertiary = LensEmeraldTertiaryDark,
    onTertiary = LensOnTertiaryDark,
    tertiaryContainer = LensTertiaryContainerDark,
    onTertiaryContainer = LensOnTertiaryContainerDark,
    background = LensBackgroundDark,
    onBackground = LensOnBackgroundDark,
    surface = LensSurfaceDark,
    onSurface = LensOnSurfaceDark,
    surfaceVariant = LensSurfaceVariantDark,
    onSurfaceVariant = LensOnSurfaceVariantDark,
    outline = LensOutlineDark,
    error = LensErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = LensBluePrimaryLight,
    onPrimary = LensOnPrimaryLight,
    primaryContainer = LensPrimaryContainerLight,
    onPrimaryContainer = LensOnPrimaryContainerLight,
    secondary = LensCyanSecondaryLight,
    onSecondary = LensOnSecondaryLight,
    secondaryContainer = LensSecondaryContainerLight,
    onSecondaryContainer = LensOnSecondaryContainerLight,
    tertiary = LensEmeraldTertiaryLight,
    onTertiary = LensOnTertiaryLight,
    tertiaryContainer = LensTertiaryContainerLight,
    onTertiaryContainer = LensOnTertiaryContainerLight,
    background = LensBackgroundLight,
    onBackground = LensOnBackgroundLight,
    surface = LensSurfaceLight,
    onSurface = LensOnSurfaceLight,
    surfaceVariant = LensSurfaceVariantLight,
    onSurfaceVariant = LensOnSurfaceVariantLight,
    outline = LensOutlineLight,
    error = LensErrorLight
)

private val StorageLensShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StorageLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val semanticColors = if (darkTheme) DarkStorageSemanticColors else LightStorageSemanticColors

    CompositionLocalProvider(LocalStorageColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = StorageLensShapes,
            content = content
        )
    }
}
