package com.example.compose_ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.UUID

/** Stores only an ownership token and deadline, never the copied secret. */
class SensitiveClipboardCleaner(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
    clipboardOverride: ClipboardManager? = null,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("clipboard_cleanup", Context.MODE_PRIVATE)
    private val clipboard =
        clipboardOverride
            ?: appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copy(label: String, value: String) {
        val token = UUID.randomUUID().toString()
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras =
            PersistableBundle().apply {
                putString(TOKEN, token)
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        prefs.edit().putString("token", token).putLong("deadline", clock() + 60_000L).commit()
        clipboard.setPrimaryClip(clip)
        Handler(Looper.getMainLooper())
            .postDelayed(
                {
                    // Handler uses a monotonic clock; mark this copy expired even if wall time
                    // changed.
                    if (prefs.getString("token", null) == token) {
                        prefs.edit().putLong("deadline", 0L).commit()
                        clearExpired()
                    }
                },
                60_000L,
            )
    }

    /** Also called on foreground entry, covering a stopped process or denied background access. */
    fun clearExpired() {
        val token = prefs.getString("token", null) ?: return
        if (clock() < prefs.getLong("deadline", Long.MAX_VALUE)) return
        try {
            val current = clipboard.primaryClip ?: return
            if (current.description.extras?.getString(TOKEN) == token) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            prefs.edit().clear().commit()
        } catch (_: SecurityException) {
            // Retain metadata and retry when the app regains focus. Android controls access.
        }
    }

    companion object {
        private const val TOKEN = "com.example.mypasswords.CLIP_COPY_TOKEN"
    }
}
