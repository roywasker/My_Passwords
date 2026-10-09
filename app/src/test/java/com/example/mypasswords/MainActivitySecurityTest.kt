package com.example.mypasswords

import android.view.WindowManager
import io.mockk.*
import java.security.KeyStore
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MyPasswordsApp::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivitySecurityTest {
    @Before
    fun isolateKeystore() {
        val store = mockk<KeyStore>(relaxed = true)
        every { store.containsAlias("VaultMasterKey") } returns true
        mockkStatic(KeyStore::class)
        every { KeyStore.getInstance("AndroidKeyStore") } returns store
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun secureWindowFlagBlocksScreenshots() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertTrue(
            controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun appStartsLocked() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(controller.get().authRepository.isAuthenticated.value)
        controller.pause().stop().destroy()
    }

    @Test
    fun stoppingActivityLocksAuthenticatedSession() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val auth = controller.get().authRepository
        auth.setAuthenticated(true)
        controller.pause().stop()
        assertFalse(auth.isAuthenticated.value)
        controller.destroy()
    }

    @Test
    fun repeatedBackgroundTransitionsStayLocked() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val auth = controller.get().authRepository
        auth.setAuthenticated(true)
        controller.pause().stop().start().resume()
        assertFalse(auth.isAuthenticated.value)
        controller.pause().stop().destroy()
    }

    @Test
    fun configurationRecreationPreservesAuthenticationAndSecureWindow() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val auth = controller.get().authRepository
        auth.setAuthenticated(true)
        controller.configurationChange()
        assertTrue(auth.isAuthenticated.value)
        assertSame(auth, controller.get().authRepository)
        assertTrue(
            controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun restartingStoppedActivityRequiresAuthenticationAgain() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.get().authRepository.setAuthenticated(true)
        controller.pause().stop().restart().start().resume().visible()
        assertFalse(controller.get().authRepository.isAuthenticated.value)
        controller.pause().stop().destroy()
    }

    @Test
    fun restoringSavedActivityDoesNotRestoreAuthenticatedFlag() {
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        first.get().authRepository.setAuthenticated(true)
        val saved = android.os.Bundle()
        first.saveInstanceState(saved).pause().stop().destroy()
        val restored =
            Robolectric.buildActivity(MainActivity::class.java)
                .create(saved)
                .start()
                .resume()
                .visible()
        assertFalse(restored.get().authRepository.isAuthenticated.value)
        assertTrue(
            restored.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
        restored.pause().stop().destroy()
    }
}
