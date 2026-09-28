package de.radicalfishgames.crosscode.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import de.radicalfishgames.crosscode.R

/** Mirrors the brand palette of [R.style.AppTheme] so Compose screens match the View-based launcher. */
@Composable
fun appColorScheme() =
    if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = colorResource(R.color.colorPrimary),
            onPrimary = colorResource(R.color.colorOnPrimary),
            primaryContainer = colorResource(R.color.colorPrimaryContainer),
            onPrimaryContainer = colorResource(R.color.colorOnPrimaryContainer),
            secondary = colorResource(R.color.colorSecondary),
            onSecondary = colorResource(R.color.colorOnSecondary),
            secondaryContainer = colorResource(R.color.colorSecondaryContainer),
            onSecondaryContainer = colorResource(R.color.colorOnSecondaryContainer),
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.colorPrimary),
            onPrimary = colorResource(R.color.colorOnPrimary),
            primaryContainer = colorResource(R.color.colorPrimaryContainer),
            onPrimaryContainer = colorResource(R.color.colorOnPrimaryContainer),
            secondary = colorResource(R.color.colorSecondary),
            onSecondary = colorResource(R.color.colorOnSecondary),
            secondaryContainer = colorResource(R.color.colorSecondaryContainer),
            onSecondaryContainer = colorResource(R.color.colorOnSecondaryContainer),
        )
    }
