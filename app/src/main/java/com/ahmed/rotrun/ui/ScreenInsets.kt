package com.ahmed.rotrun.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Status bar, gesture nav, and 3-button nav, plus the keyboard.
 * systemBars already includes the status and navigation bars. navigationBars is
 * unioned again so a 3-button bar is not missed when only one inset is reported.
 */
@Composable
fun Modifier.rotInsets(): Modifier = windowInsetsPadding(
    WindowInsets.systemBars.union(WindowInsets.navigationBars).union(WindowInsets.ime),
)

/** Bottom sheets already sit under the status bar. Keep the 3-button bar and the keyboard clear. */
@Composable
fun Modifier.rotBottomInsets(): Modifier = windowInsetsPadding(
    WindowInsets.navigationBars.union(WindowInsets.ime),
)
