package com.example.compose_ui.components

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.mockk.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SensitiveClipboardCleanerTest {
    private val app
        get() = RuntimeEnvironment.getApplication()

    private val board
        get() = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private var now = 1000L

    private fun cleaner() = SensitiveClipboardCleaner(app, { now })

    @Before
    fun setup() {
        app.getSharedPreferences("clipboard_cleanup", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun recreatedCleanerClearsExpiredCopyUsingPersistedMetadata() {
        cleaner().copy("Password", "synthetic-secret")
        now += 60_000L
        cleaner().clearExpired()
        assertEquals("", board.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun onlyTokenAndDeadlineArePersistedNeverCopiedSecret() {
        cleaner().copy("Password", "synthetic-secret")
        val values = app.getSharedPreferences("clipboard_cleanup", Context.MODE_PRIVATE).all
        assertEquals(setOf("token", "deadline"), values.keys)
        assertFalse(values.values.any { it == "synthetic-secret" })
        assertTrue(
            board.primaryClip!!
                .description
                .extras!!
                .getBoolean("android.content.extra.IS_SENSITIVE")
        )
    }

    @Test
    fun unexpiredCopyIsRetainedForPasting() {
        cleaner().copy("Password", "synthetic-secret")
        now += 59_999L
        cleaner().clearExpired()
        assertEquals("synthetic-secret", board.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun newerCopyFromOtherApplicationIsNeverDeleted() {
        cleaner().copy("Password", "synthetic-secret")
        board.setPrimaryClip(ClipData.newPlainText("Password", "unrelated copy"))
        now += 60_000L
        cleaner().clearExpired()
        assertEquals("unrelated copy", board.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun newerSensitiveCopyRenewsDeadline() {
        cleaner().copy("Password", "old-secret")
        now += 30_000L
        cleaner().copy("Password", "new-secret")
        now += 30_000L
        cleaner().clearExpired()
        assertEquals("new-secret", board.primaryClip!!.getItemAt(0).text.toString())
        now += 30_000L
        cleaner().clearExpired()
        assertEquals("", board.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun deniedBackgroundReadRetainsDeadlineForForegroundRetry() {
        val fake = mockk<ClipboardManager>(relaxed = true)
        val helper = SensitiveClipboardCleaner(app, { now }, fake)
        helper.copy("Password", "synthetic-secret")
        now += 60_000L
        every { fake.primaryClip } throws SecurityException("synthetic background restriction")
        helper.clearExpired()
        assertTrue(
            app.getSharedPreferences("clipboard_cleanup", Context.MODE_PRIVATE).contains("token")
        )
        val clip = slot<ClipData>()
        verify { fake.setPrimaryClip(capture(clip)) }
        every { fake.primaryClip } returns clip.captured
        helper.clearExpired()
        verify { fake.setPrimaryClip(match { it.getItemAt(0).text.toString() == "" }) }
        assertFalse(
            app.getSharedPreferences("clipboard_cleanup", Context.MODE_PRIVATE).contains("token")
        )
    }

    @Test
    fun nullBackgroundClipboardCanBeRetriedOnForegroundEntry() {
        val fake = mockk<ClipboardManager>(relaxed = true)
        val helper = SensitiveClipboardCleaner(app, { now }, fake)
        helper.copy("Password", "synthetic-secret")
        now += 60_000L
        every { fake.primaryClip } returns null
        helper.clearExpired()
        assertTrue(
            app.getSharedPreferences("clipboard_cleanup", Context.MODE_PRIVATE).contains("token")
        )
    }
}
