package com.example.mypasswords.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.example.compose_ui.components.*
import com.example.core.model.CategoryChip
import com.example.core.model.PasswordItemModel
import com.example.mypasswords.R
import com.example.mypasswords.screens.finger.*
import com.example.mypasswords.screens.password.*
import com.example.mypasswords.screens.welcome.WelcomeScreen
import com.example.mypasswords.ui.theme.MyPasswordsTheme
import io.mockk.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.*

/** Renders only synthetic UI fixtures on the JVM, never a real vault or physical device. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
open class DesignPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    protected fun text(id: Int) = compose.activity.getString(id)

    protected open fun render(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            MyPasswordsTheme {
                Box(
                    Modifier.fillMaxSize()
                        .background(com.example.mypasswords.ui.theme.BackgroundColor)
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Capture all test-owned windows, including modal sheets and dialogs.
            val windowsClass = Class.forName("android.view.WindowManagerGlobal")
            val windows = windowsClass.getMethod("getInstance").invoke(null)
            val roots =
                windowsClass.getDeclaredField("mViews").apply { isAccessible = true }.get(windows)
                    as List<*>
            roots.filterIsInstance<android.view.View>().forEachIndexed { index, root ->
                if (index > 0) canvas.drawColor(0x88000000.toInt())
                canvas.save()
                canvas.translate((view.width - root.width) / 2f, (view.height - root.height) / 2f)
                root.draw(canvas)
                canvas.restore()
            }
            val file = File("build/design-preview/$name.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun welcomePreview() {
        render("welcome") { WelcomeScreen {} }
        compose.onNodeWithText(text(R.string.welcome_screen_button_text)).assertExists()
    }

    @Test
    fun fingerprintPreview() {
        val vm = mockk<FingerPrintViewModel>(relaxed = true)
        every { vm.authState } returns MutableStateFlow(AuthState.Idle)
        render("fingerprint") { FingerPrintAuthScreen(vm) }
        compose.onNodeWithText(text(R.string.fingerPrint_screen_title)).assertExists()
    }

    @Test
    fun passwordsPreview() {
        val vm = mockk<PasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow(
                PasswordUiState(
                    categories =
                        listOf(
                            CategoryChip(1, "הכל"),
                            CategoryChip(2, "עבודה"),
                            CategoryChip(3, "אישי"),
                        ),
                    listStatus = PasswordListStatus.Ready,
                )
            )
        every { vm.filteredPasswords } returns
            MutableStateFlow(
                listOf(
                    PasswordItemModel(1, "Google", "demo@example.com"),
                    PasswordItemModel(2, "GitHub", "demo-developer"),
                    PasswordItemModel(3, "הבנק שלי", "demo-account"),
                )
            )
        render("passwords") { PasswordScreen(vm, {}, {}) }
        compose.onNodeWithText("Google").assertExists()
    }

    @Test
    fun addPasswordPreview() {
        val vm = mockk<AddPasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow(
                AddPasswordUiState(
                    categories =
                        listOf(
                            CategoryChip(1, "הכל"),
                            CategoryChip(2, "עבודה"),
                            CategoryChip(3, "אישי"),
                        ),
                    platformName = "Google",
                    username = "demo@example.com".toCharArray(),
                    password = "synthetic-secret".toCharArray(),
                    isSaveEnabled = true,
                )
            )
        render("add-password") { AddPasswordScreen(vm, {}) }
        compose.onNodeWithText(text(R.string.save_now_btn)).assertExists()
    }

    @Test
    fun editPasswordPreview() {
        render("edit-password") {
            CEditPasswordSheet(
                "Google",
                "demo@example.com",
                "synthetic-secret",
                "",
                {},
                { _, _ -> },
                title = text(R.string.update_password),
                usernameLabel = text(R.string.username_label),
                passwordLabel = text(R.string.password_label),
                updateButtonText = text(R.string.edit),
            )
        }
        compose.onNodeWithText(text(R.string.edit)).assertExists()
    }

    @Test
    fun detailPreview() {
        render("detail") {
            CPasswordDetailSheet(
                "Google",
                "demo@example.com".toCharArray(),
                "synthetic-secret".toCharArray(),
                "",
                {},
                closeButtonText = text(R.string.close_btn),
                usernameLabel = text(R.string.username_label),
                passwordLabel = text(R.string.password_label),
                loginDetailsSubtitle = text(R.string.login_details_subtitle),
            )
        }
        compose.onNodeWithText(text(R.string.close_btn)).assertExists()
    }

    @Test
    fun recoveryPreview() {
        render("recovery") {
            RecoveryCodeDialog("DEMO-DEMO-DEMO-DEMO-DEMO-DEMO-DEMO-DEMO-DEMO", false, {}, {})
        }
        compose.onNodeWithText(text(R.string.recovery_saved)).assertExists()
    }

    @Test
    fun deletePreview() {
        render("delete") { DeleteConfirmationDialog("Google", {}, {}) }
        compose.onNodeWithText(text(R.string.delete_dialog_confirm)).assertExists()
    }

    @Test
    fun largeFontDeleteActionsRemainReachable() {
        render("delete-large-font") {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(1f, 2f)
            ) {
                DeleteConfirmationDialog("Synthetic platform with a long name", {}, {})
            }
        }
        compose
            .onNodeWithText(text(R.string.delete_dialog_confirm))
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(text(R.string.delete_dialog_cancel))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
