package de.radicalfishgames.crosscode.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import de.radicalfishgames.crosscode.R

/** Mirrors the brand palette of [R.style.AppTheme] so Compose screens match the View-based launcher. */
@Composable
fun appColorScheme() =
    darkColorScheme(
        primary = colorResource(R.color.colorPrimary),
        onPrimary = colorResource(R.color.colorOnPrimary),
        primaryContainer = colorResource(R.color.colorPrimaryContainer),
        onPrimaryContainer = colorResource(R.color.colorOnPrimaryContainer),
        secondary = colorResource(R.color.colorSecondary),
        onSecondary = colorResource(R.color.colorOnSecondary),
        secondaryContainer = colorResource(R.color.colorSecondaryContainer),
        onSecondaryContainer = colorResource(R.color.colorOnSecondaryContainer),
        background = colorResource(R.color.colorSurface),
        onBackground = colorResource(R.color.colorOnSurface),
        surface = colorResource(R.color.colorSurface),
        onSurface = colorResource(R.color.colorOnSurface),
        surfaceVariant = colorResource(R.color.colorSurfaceVariant),
        onSurfaceVariant = colorResource(R.color.colorOnSurfaceVariant),
        surfaceContainerLowest = colorResource(R.color.colorSurfaceContainerLowest),
        surfaceContainerLow = colorResource(R.color.colorSurfaceContainerLow),
        surfaceContainer = colorResource(R.color.colorSurfaceContainer),
        surfaceContainerHigh = colorResource(R.color.colorSurfaceContainerHigh),
        surfaceContainerHighest = colorResource(R.color.colorSurfaceContainerHighest),
        outline = colorResource(R.color.colorOutline),
        outlineVariant = colorResource(R.color.colorOutlineVariant),
    )
