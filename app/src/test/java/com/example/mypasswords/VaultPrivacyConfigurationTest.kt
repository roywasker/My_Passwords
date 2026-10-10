package com.example.mypasswords

import android.app.Application
import android.content.pm.ApplicationInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VaultPrivacyConfigurationTest {
    private val app
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun applicationCannotSendVaultPlatformNamesToExternalIconServices() {
        val permissions =
            app.packageManager
                .getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
    }

    @Test
    fun automaticBackupIsDisabledInApplicationManifest() {
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun cloudAndDeviceTransfersExplicitlyExcludeVaultFilesAndPreferences() {
        val excluded = mutableMapOf<String, MutableSet<String>>()
        app.resources.getXml(R.xml.data_extraction_rules).use { parser ->
            var section = ""
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.name in listOf("cloud-backup", "device-transfer"))
                        section = parser.name
                    if (parser.name == "exclude" && parser.getAttributeValue(null, "path") == ".") {
                        excluded
                            .getOrPut(section) { mutableSetOf() }
                            .add(parser.getAttributeValue(null, "domain"))
                    }
                }
                parser.next()
            }
        }
        for (section in listOf("cloud-backup", "device-transfer")) {
            assertTrue(
                excluded[section]
                    .orEmpty()
                    .containsAll(listOf("database", "sharedpref", "file", "root", "external"))
            )
        }
    }

    @Test
    fun olderAndroidBackupRulesAlsoExcludeVaultAndRecoveryEnvelope() {
        val excluded = mutableSetOf<String>()
        app.resources.getXml(R.xml.backup_rules).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                        parser.name == "exclude" &&
                        parser.getAttributeValue(null, "path") == "."
                ) {
                    excluded.add(parser.getAttributeValue(null, "domain"))
                }
                parser.next()
            }
        }
        assertTrue(excluded.containsAll(listOf("database", "sharedpref")))
    }
}
