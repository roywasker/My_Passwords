package com.example.mypasswords.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.mypasswords.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeBehaviorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun lightThemeWithoutDynamicColorsUsesLightPalette() {
        var primary: Color? = null
        compose.setContent {
            MyPasswordsTheme(darkTheme = false, dynamicColor = false) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        compose.runOnIdle { assertEquals(Purple40, primary) }
    }

    @Test
    fun darkThemeWithoutDynamicColorsUsesDarkPalette() {
        var primary: Color? = null
        compose.setContent {
            MyPasswordsTheme(darkTheme = true, dynamicColor = false) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        compose.runOnIdle { assertEquals(Purple80, primary) }
    }

    @Test
    fun preAndroid12FallsBackToStaticPaletteWhenDynamicColorRequested() {
        var primary: Color? = null
        compose.setContent {
            MyPasswordsTheme(darkTheme = false, dynamicColor = true) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        compose.runOnIdle { assertEquals(Purple40, primary) }
    }
}
