package com.example.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.NetworkGuardian
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VersionUpdateManagerTest {

    @Test
    fun `installed version info matches Voice Guardian AI requirements`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val guardian = NetworkGuardian(context)
        try {
            val manager = VersionUpdateManager(context, guardian)
            val info = manager.getInstalledVersionInfo()
            assertEquals("Voice Guardian AI", info.appName)
            assertEquals("com.voiceguardian.jarvis", info.packageName)
            assertEquals("1.0.0", info.versionName)
            assertEquals(1L, info.versionCode)
            assertEquals(24, info.minSdk)
            assertEquals(36, info.targetSdk)
        } finally {
            guardian.release()
        }
    }

    @Test
    fun `parseRemoteVersionManifest extracts latest metadata and history`() {
        val json = """
            {
              "packageName": "com.voiceguardian.jarvis",
              "latestVersionName": "1.2.0",
              "latestVersionCode": 3,
              "minSdk": 24,
              "apkUrl": "https://updates.example.com/voice-guardian-1.2.0.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "releaseDate": "2026-12-01",
              "releaseNotes": "Improved wake-phrase latency and Forex alerts.",
              "mandatory": false,
              "history": [
                {
                  "versionName": "1.1.0",
                  "versionCode": 2,
                  "releaseDate": "2026-11-20",
                  "notes": "Enhanced AI Studio preview."
                }
              ]
            }
        """.trimIndent()

        val manifest = VersionUpdateManager.parseRemoteVersionManifest(json, "com.voiceguardian.jarvis")
        assertEquals("com.voiceguardian.jarvis", manifest.packageName)
        assertEquals("1.2.0", manifest.latestVersionName)
        assertEquals(3L, manifest.latestVersionCode)
        assertEquals(24, manifest.minSdk)
        assertEquals("https://updates.example.com/voice-guardian-1.2.0.apk", manifest.apkUrl)
        assertEquals(64, manifest.sha256.length)
        assertEquals(1, manifest.history.size)
        assertEquals("1.1.0", manifest.history.first().versionName)
    }

    @Test
    fun `downloadAndVerifyApk blocks downgrade versions before network call`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val guardian = NetworkGuardian(context)
        try {
            val manager = VersionUpdateManager(context, guardian)
            val olderManifest = RemoteVersionManifest(
                packageName = "com.voiceguardian.jarvis",
                latestVersionName = "0.9.0",
                latestVersionCode = 1L,
                minSdk = 24,
                apkUrl = "https://updates.example.com/old.apk",
                sha256 = "a".repeat(64),
                releaseDate = "2026-11-01",
                releaseNotes = "Old build",
                mandatory = false,
                history = emptyList()
            )
            val status = manager.downloadAndVerifyApk(olderManifest) { _, _, _ -> }
            assertTrue(status is ApkVerificationStatus.Failed)
            assertTrue((status as ApkVerificationStatus.Failed).reason.contains("downgrade", ignoreCase = true))
        } finally {
            guardian.release()
        }
    }

    @Test
    fun `verifyDownloadedApkFile rejects SHA-256 mismatch and computes exact digest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val guardian = NetworkGuardian(context)
        val tempFile = File.createTempFile("test_update", ".apk", context.cacheDir)
        try {
            tempFile.writeText("synthetic-apk-payload-bytes")
            val expectedRealSha = MessageDigest.getInstance("SHA-256")
                .digest("synthetic-apk-payload-bytes".toByteArray())
                .joinToString("") { "%02x".format(it) }

            val manager = VersionUpdateManager(context, guardian)
            assertEquals(expectedRealSha, VersionUpdateManager.computeSha256(tempFile))

            val failed = manager.verifyDownloadedApkFile(tempFile, "0".repeat(64))
            assertTrue(failed is ApkVerificationStatus.Failed)
            assertTrue((failed as ApkVerificationStatus.Failed).reason.contains("SHA-256"))
        } finally {
            tempFile.delete()
            guardian.release()
        }
    }
}
