package com.example.compose_ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Orientation is deliberate: portrait keeps its existing layout, including on tablets. */
@Composable
fun isVaultLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Falls back to a stack when a landscape window is too narrow for usable input fields. */
@Composable
fun VaultFieldPair(
    spacing: androidx.compose.ui.unit.Dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    if (isVaultLandscape()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 480.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { first() }
                    Column(Modifier.weight(1f)) { second() }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    first()
                    second()
                }
            }
        }
    } else {
        first()
        Spacer(Modifier.height(spacing))
        second()
    }
}

@Composable
fun VaultActionGroup(content: @Composable () -> Unit) {
    if (isVaultLandscape()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    } else {
        content()
    }
}
