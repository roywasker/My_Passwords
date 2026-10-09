package com.example.core.data

import android.app.Application
import android.content.Context
import io.mockk.*
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LegacyDatabaseRecoveryTest {
    private lateinit var context: Context
    private val room = mockk<VaultDatabase>(relaxed = true)
    private val native = mockk<SQLiteDatabase>(relaxed = true)
    private val probe = mockk<SQLiteDatabase>(relaxed = true)
    private val checkResult = mockk<android.database.Cursor>(relaxed = true)
    private val key = ByteArray(32) { (it + 1).toByte() }
    private var openingKey: ByteArray? = null

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getDatabasePath("vault.db").apply {
            parentFile!!.mkdirs()
            writeText("synthetic test fixture")
        }
        mockkObject(VaultDatabase.Companion)
        mockkStatic(SQLiteDatabase::class)
        every { SQLiteDatabase.openDatabase(any<String>(), any<ByteArray>(), any(), any(), any(), any()) } returns probe
        every { probe.rawQuery("PRAGMA quick_check", null) } returns checkResult
        every { checkResult.moveToFirst() } returns true
        every { checkResult.getString(0) } returns "ok"
        every { checkResult.moveToNext() } returns false
        every { VaultDatabase.build(context, any()) } answers {
            openingKey = secondArg()
            room
        }
        every { room.openHelper.writableDatabase } returns native
    }

    @After
    fun cleanup() {
        context.deleteDatabase("vault.db")
        key.fill(0)
        unmockkAll()
    }

    @Test
    fun validatesLegacyDatabaseBeforeRekeyAndWipesTemporaryKeys() {
        var replacement: ByteArray? = null
        every { native.changePassword(any<ByteArray>()) } answers {
            replacement = firstArg()
            assertArrayEquals(key, replacement)
            assertNotSame(key, replacement)
        }
        VaultDatabase.recoverLegacyZeroKey(context, key)
        verifyOrder {
            room.openHelper.writableDatabase
            native.disableWriteAheadLogging()
            native.changePassword(any<ByteArray>())
            room.close()
        }
        assertTrue(openingKey!!.all { it == 0.toByte() })
        assertTrue(replacement!!.all { it == 0.toByte() })
        assertTrue(key.any { it != 0.toByte() })
        assertTrue(context.getDatabasePath("vault.db").exists())
    }

    @Test
    fun schemaValidationFailureNeverRekeysOrDeletesExistingDatabase() {
        val error = IllegalStateException("synthetic schema mismatch")
        every { room.openHelper.writableDatabase } throws error
        assertSame(error, assertThrows(IllegalStateException::class.java) {
            VaultDatabase.recoverLegacyZeroKey(context, key)
        })
        verify(exactly = 0) { native.changePassword(any<ByteArray>()) }
        verify(exactly = 1) { room.close() }
        assertTrue(context.getDatabasePath("vault.db").exists())
    }

    @Test
    fun failedRekeyClosesLegacyHandleAndWipesReplacement() {
        var replacement: ByteArray? = null
        every { native.changePassword(any<ByteArray>()) } answers {
            replacement = firstArg()
            throw IllegalStateException("synthetic rekey failure")
        }
        assertThrows(IllegalStateException::class.java) {
            VaultDatabase.recoverLegacyZeroKey(context, key)
        }
        verify(exactly = 1) { room.close() }
        assertTrue(replacement!!.all { it == 0.toByte() })
        assertTrue(context.getDatabasePath("vault.db").exists())
    }

    @Test
    fun missingDatabaseIsNeverCreatedByRecovery() {
        context.deleteDatabase("vault.db")
        assertThrows(IllegalStateException::class.java) {
            VaultDatabase.recoverLegacyZeroKey(context, key)
        }
        verify(exactly = 0) { VaultDatabase.build(any(), any()) }
    }

    @Test
    fun emptyDatabaseIsNeverRekeyed() {
        context.getDatabasePath("vault.db").writeBytes(byteArrayOf())
        assertThrows(IllegalStateException::class.java) {
            VaultDatabase.recoverLegacyZeroKey(context, key)
        }
        verify(exactly = 0) { VaultDatabase.build(any(), any()) }
    }

    @Test
    fun unexpectedKeySizeIsRejectedBeforeOpeningDatabase() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultDatabase.recoverLegacyZeroKey(context, ByteArray(16))
        }
        verify(exactly = 0) { VaultDatabase.build(any(), any()) }
    }

    @Test
    fun damagedLegacyDatabaseNeverOpensWritableHandleOrRekeys() {
        every { checkResult.getString(0) } returns "synthetic corruption"
        assertThrows(IllegalStateException::class.java) {
            VaultDatabase.recoverLegacyZeroKey(context, key)
        }
        verify(exactly = 0) { VaultDatabase.build(any(), any()) }
        verify(exactly = 0) { native.changePassword(any<ByteArray>()) }
        verify(exactly = 1) { probe.close() }
        assertTrue(context.getDatabasePath("vault.db").exists())
    }
}
