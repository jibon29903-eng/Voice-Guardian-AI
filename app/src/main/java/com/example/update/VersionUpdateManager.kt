package com.example.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.core.NetworkGuardian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class InstalledVersionInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val buildType: String
)

data class VersionHistoryEntry(
    val versionName: String,
    val versionCode: Long,
    val releaseDate: String,
    val notes: String,
    val isCurrent: Boolean = false
)

data class RemoteVersionManifest(
    val packageName: String,
    val latestVersionName: String,
    val latestVersionCode: Long,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val releaseDate: String,
    val releaseNotes: String,
    val mandatory: Boolean,
    val history: List<VersionHistoryEntry>
)

sealed interface UpdateCheckOutcome {
    data class UpdateAvailable(val manifest: RemoteVersionManifest) : UpdateCheckOutcome
    data class UpToDate(val manifest: RemoteVersionManifest) : UpdateCheckOutcome
    data class DowngradeBlocked(val manifest: RemoteVersionManifest, val reason: String) : UpdateCheckOutcome
    data class Error(val message: String) : UpdateCheckOutcome
}

sealed interface ApkVerificationStatus {
    data object NotDownloaded : ApkVerificationStatus
    data class Verified(
        val file: File,
        val sha256: String,
        val apkVersionCode: Long,
        val apkVersionName: String,
        val byteSize: Long
    ) : ApkVerificationStatus
    data class Failed(val reason: String) : ApkVerificationStatus
}

sealed interface InstallLaunchResult {
    data object LaunchedInstaller : InstallLaunchResult
    data class NeedsUnknownSourcesPermission(val settingsIntent: Intent) : InstallLaunchResult
    data class Blocked(val reason: String) : InstallLaunchResult
}

data class VersionUpdateUiState(
    val installed: InstalledVersionInfo,
    val manifestUrl: String = "",
    val isChecking: Boolean = false,
    val lastCheckedLabel: String = "Not checked yet",
    val remoteManifest: RemoteVersionManifest? = null,
    val updateAvailable: Boolean = false,
    val downgradeBlockedReason: String? = null,
    val checkError: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val verificationStatus: ApkVerificationStatus = ApkVerificationStatus.NotDownloaded,
    val statusMessage: String? = null,
    val versionHistory: List<VersionHistoryEntry> = emptyList()
)

class VersionUpdateManager(
    context: Context,
    private val networkGuardian: NetworkGuardian,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val appContext = context.applicationContext
    private val updatesDir = File(appContext.filesDir, "updates").apply { mkdirs() }

    fun getInstalledVersionInfo(): InstalledVersionInfo {
        val pkgName = appContext.packageName
        var resolvedVersionName = BuildConfig.VERSION_NAME
        var resolvedVersionCode = BuildConfig.VERSION_CODE.toLong()
        try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(pkgName, 0)
            }
            resolvedVersionName = pkgInfo.versionName ?: BuildConfig.VERSION_NAME
            resolvedVersionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        } catch (_: Exception) {
        }

        return InstalledVersionInfo(
            appName = appContext.getString(R.string.app_name),
            packageName = pkgName,
            versionName = resolvedVersionName,
            versionCode = resolvedVersionCode,
            minSdk = 24,
            targetSdk = 36,
            buildType = BuildConfig.BUILD_TYPE.uppercase(Locale.US)
        )
    }

    fun getBundledHistory(currentCode: Long): List<VersionHistoryEntry> = listOf(
        VersionHistoryEntry(
            versionName = "1.0.0",
            versionCode = 1L,
            releaseDate = "2026-11-14",
            notes = "Initial Voice Guardian AI release: Gemini 3.8 Flash assistant, multilingual Voice Guardian (English/Bangla/Arabic), AI Website Studio, Deep Research, Image Creator, Live Forex Watchlist, Screen Guardian, AI Whiteboard, Network Guardian, and SHA-256 verified APK Version Management.",
            isCurrent = currentCode == 1L
        )
    )

    fun mergeVersionHistory(remote: RemoteVersionManifest?, currentCode: Long): List<VersionHistoryEntry> {
        val base = getBundledHistory(currentCode)
        if (remote == null) return base
        val combined = LinkedHashMap<Long, VersionHistoryEntry>()
        remote.history.forEach { entry ->
            combined[entry.versionCode] = entry.copy(isCurrent = entry.versionCode == currentCode)
        }
        if (!combined.containsKey(remote.latestVersionCode)) {
            combined[remote.latestVersionCode] = VersionHistoryEntry(
                versionName = remote.latestVersionName,
                versionCode = remote.latestVersionCode,
                releaseDate = remote.releaseDate,
                notes = remote.releaseNotes,
                isCurrent = remote.latestVersionCode == currentCode
            )
        }
        base.forEach { b ->
            combined.putIfAbsent(b.versionCode, b)
        }
        return combined.values.sortedByDescending { it.versionCode }
    }

    fun cleanupObsoleteApks() {
        val currentCode = getInstalledVersionInfo().versionCode
        updatesDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part")) {
                file.delete()
            } else if (file.name.endsWith(".apk")) {
                val archiveCode = inspectApkVersionCode(file)
                if (archiveCode == null || archiveCode <= currentCode) {
                    file.delete()
                }
            }
        }
    }

    suspend fun fetchRemoteVersionManifest(manifestUrl: String): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        val cleanUrl = manifestUrl.trim()
        if (!isAllowedUpdateUrl(cleanUrl)) {
            return@withContext UpdateCheckOutcome.Error(
                "Enter a valid HTTPS remote version manifest JSON URL."
            )
        }

        val installed = getInstalledVersionInfo()
        try {
            val rawJson = networkGuardian.executeGuarded(dedupKey = "version_check_$cleanUrl", maxRetries = 2) {
                val req = Request.Builder()
                    .url(cleanUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "VoiceGuardianAI/${installed.versionName}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Update server returned HTTP ${resp.code}")
                    }
                    resp.body?.string() ?: throw IllegalStateException("Empty JSON response from update server")
                }
            }

            val manifest = parseRemoteVersionManifest(rawJson, installed.packageName)
            networkGuardian.saveLastUpdateJson(rawJson)

            if (manifest.packageName.isNotBlank() && manifest.packageName != installed.packageName) {
                return@withContext UpdateCheckOutcome.DowngradeBlocked(
                    manifest,
                    "Package mismatch: remote manifest targets ${manifest.packageName}, installed app is ${installed.packageName}."
                )
            }
            if (Build.VERSION.SDK_INT < manifest.minSdk) {
                return@withContext UpdateCheckOutcome.DowngradeBlocked(
                    manifest,
                    "Device Android API ${Build.VERSION.SDK_INT} is below required minSdk ${manifest.minSdk}."
                )
            }

            when {
                manifest.latestVersionCode > installed.versionCode -> {
                    UpdateCheckOutcome.UpdateAvailable(manifest)
                }
                manifest.latestVersionCode < installed.versionCode -> {
                    UpdateCheckOutcome.DowngradeBlocked(
                        manifest,
                        "Anti-downgrade protection: remote versionCode ${manifest.latestVersionCode} is older than installed versionCode ${installed.versionCode}."
                    )
                }
                else -> {
                    UpdateCheckOutcome.UpToDate(manifest)
                }
            }
        } catch (e: Exception) {
            UpdateCheckOutcome.Error(e.localizedMessage ?: "Failed to check remote version JSON.")
        }
    }

    suspend fun downloadAndVerifyApk(
        manifest: RemoteVersionManifest,
        onProgress: (Float, Long, Long) -> Unit
    ): ApkVerificationStatus = withContext(Dispatchers.IO) {
        val installed = getInstalledVersionInfo()
        if (manifest.latestVersionCode <= installed.versionCode) {
            return@withContext ApkVerificationStatus.Failed(
                "Blocked downgrade or redundant install: target build ${manifest.latestVersionCode} <= installed ${installed.versionCode}."
            )
        }
        if (!isAllowedUpdateUrl(manifest.apkUrl)) {
            return@withContext ApkVerificationStatus.Failed(
                "APK download URL must use HTTPS: ${manifest.apkUrl}"
            )
        }
        val expectedSha = normalizeSha256(manifest.sha256)
        if (!SHA256_REGEX.matches(expectedSha)) {
            return@withContext ApkVerificationStatus.Failed(
                "Remote JSON is missing a valid 64-character SHA-256 checksum."
            )
        }

        val targetApk = File(updatesDir, "VoiceGuardianAI-v${manifest.latestVersionCode}.apk")
        val partFile = File(updatesDir, "VoiceGuardianAI-v${manifest.latestVersionCode}.apk.part")
        if (targetApk.exists()) {
            val existingVerification = verifyDownloadedApkFile(targetApk, expectedSha, installed)
            if (existingVerification is ApkVerificationStatus.Verified) {
                onProgress(1f, existingVerification.byteSize, existingVerification.byteSize)
                return@withContext existingVerification
            }
            targetApk.delete()
        }

        try {
            val req = Request.Builder()
                .url(manifest.apkUrl)
                .header("User-Agent", "VoiceGuardianAI/${installed.versionName}")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext ApkVerificationStatus.Failed("APK download failed with HTTP ${resp.code}")
                }
                val body = resp.body ?: return@withContext ApkVerificationStatus.Failed("Empty APK response body")
                val total = body.contentLength()
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val fraction = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            onProgress(fraction, downloaded, total)
                        }
                        output.flush()
                    }
                }
            }

            val verification = verifyDownloadedApkFile(partFile, expectedSha, installed)
            return@withContext when (verification) {
                is ApkVerificationStatus.Verified -> {
                    if (targetApk.exists()) targetApk.delete()
                    if (!partFile.renameTo(targetApk)) {
                        partFile.copyTo(targetApk, overwrite = true)
                        partFile.delete()
                    }
                    verification.copy(file = targetApk)
                }
                is ApkVerificationStatus.Failed -> {
                    partFile.delete()
                    verification
                }
                ApkVerificationStatus.NotDownloaded -> {
                    partFile.delete()
                    ApkVerificationStatus.Failed("Downloaded APK could not be read.")
                }
            }
        } catch (e: Exception) {
            partFile.delete()
            ApkVerificationStatus.Failed(e.localizedMessage ?: "APK download interrupted.")
        }
    }

    fun verifyDownloadedApkFile(
        apkFile: File,
        expectedSha256: String,
        installed: InstalledVersionInfo = getInstalledVersionInfo()
    ): ApkVerificationStatus {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return ApkVerificationStatus.NotDownloaded
        }
        val normalizedExpected = normalizeSha256(expectedSha256)
        if (!SHA256_REGEX.matches(normalizedExpected)) {
            return ApkVerificationStatus.Failed("Invalid expected SHA-256 format.")
        }

        val actualSha = computeSha256(apkFile)
        if (!actualSha.equals(normalizedExpected, ignoreCase = true)) {
            return ApkVerificationStatus.Failed(
                "SHA-256 integrity check failed! Expected ${normalizedExpected.take(12)}..., got ${actualSha.take(12)}..."
            )
        }

        val archiveInfo = readPackageArchiveInfo(apkFile)
            ?: return ApkVerificationStatus.Failed("Downloaded file is not a valid Android APK package.")

        val archivePkg = archiveInfo.packageName.orEmpty()
        if (archivePkg != installed.packageName) {
            return ApkVerificationStatus.Failed(
                "Package ID mismatch ($archivePkg != ${installed.packageName}). Installation blocked to protect user data."
            )
        }

        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (archiveVersionCode <= installed.versionCode) {
            return ApkVerificationStatus.Failed(
                "Anti-downgrade block: APK versionCode $archiveVersionCode is not newer than installed ${installed.versionCode}."
            )
        }

        return ApkVerificationStatus.Verified(
            file = apkFile,
            sha256 = actualSha,
            apkVersionCode = archiveVersionCode,
            apkVersionName = archiveInfo.versionName ?: archiveVersionCode.toString(),
            byteSize = apkFile.length()
        )
    }

    fun launchApkInstaller(
        context: Context,
        apkFile: File,
        expectedSha256: String
    ): InstallLaunchResult {
        val installed = getInstalledVersionInfo()
        val verification = verifyDownloadedApkFile(apkFile, expectedSha256, installed)
        if (verification is ApkVerificationStatus.Failed) {
            apkFile.delete()
            return InstallLaunchResult.Blocked(verification.reason)
        }
        if (verification !is ApkVerificationStatus.Verified) {
            return InstallLaunchResult.Blocked("Verified update APK is missing. Please download it again.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return InstallLaunchResult.NeedsUnknownSourcesPermission(permissionIntent)
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return InstallLaunchResult.LaunchedInstaller
    }

    private fun inspectApkVersionCode(apkFile: File): Long? {
        val info = readPackageArchiveInfo(apkFile) ?: return null
        return PackageInfoCompat.getLongVersionCode(info)
    }

    private fun readPackageArchiveInfo(apkFile: File) = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val DEFAULT_MANIFEST_URL =
            "https://github.com/jibon29903-eng/Voice-Guardian-AI/releases/latest/download/version.json"
        private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
        private const val UPDATE_CHANNEL_ID = "voice_guardian_updates"
        private const val UPDATE_NOTIFICATION_ID = 4102

        fun normalizeSha256(raw: String): String =
            raw.trim().lowercase(Locale.US).replace(":", "").replace(" ", "")

        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun isAllowedUpdateUrl(url: String): Boolean {
            val trimmed = url.trim().lowercase(Locale.US)
            return trimmed.startsWith("https://") ||
                trimmed.startsWith("http://127.0.0.1") ||
                trimmed.startsWith("http://localhost") ||
                trimmed.startsWith("http://10.0.2.2")
        }

        fun parseRemoteVersionManifest(rawJson: String, fallbackPackage: String): RemoteVersionManifest {
            val obj = JSONObject(rawJson)
            val versionCode = obj.optLong("latestVersionCode", obj.optLong("versionCode", -1L))
            val versionName = obj.optString("latestVersionName", obj.optString("versionName", "")).trim()
            require(versionCode > 0L && versionName.isNotBlank()) {
                "Remote JSON must include valid latestVersionCode (>0) and latestVersionName."
            }
            val pkg = obj.optString("packageName", fallbackPackage).trim().ifBlank { fallbackPackage }
            val minSdk = obj.optInt("minSdk", 24)
            val apkUrl = obj.optString("apkUrl", "").trim()
            val sha256 = normalizeSha256(obj.optString("sha256", ""))
            val releaseDate = obj.optString("releaseDate", "Unspecified").trim()
            val releaseNotes = obj.optString("releaseNotes", "").trim()
            val mandatory = obj.optBoolean("mandatory", false)

            val historyArr = obj.optJSONArray("history") ?: JSONArray()
            val historyList = buildList {
                for (i in 0 until historyArr.length()) {
                    val item = historyArr.optJSONObject(i) ?: continue
                    val hCode = item.optLong("versionCode", -1L)
                    val hName = item.optString("versionName", "").trim()
                    if (hCode > 0L && hName.isNotBlank()) {
                        add(
                            VersionHistoryEntry(
                                versionName = hName,
                                versionCode = hCode,
                                releaseDate = item.optString("releaseDate", "").trim(),
                                notes = item.optString("notes", "").trim()
                            )
                        )
                    }
                }
            }

            return RemoteVersionManifest(
                packageName = pkg,
                latestVersionName = versionName,
                latestVersionCode = versionCode,
                minSdk = minSdk,
                apkUrl = apkUrl,
                sha256 = sha256,
                releaseDate = releaseDate,
                releaseNotes = releaseNotes,
                mandatory = mandatory,
                history = historyList
            )
        }

        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun ensureUpdateChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(UPDATE_CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        UPDATE_CHANNEL_ID,
                        "App Updates",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifies when a verified Voice Guardian AI APK update is available."
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        fun showUpdateAvailableNotification(context: Context, manifest: RemoteVersionManifest): Boolean {
            if (!hasNotificationPermission(context)) return false
            ensureUpdateChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_route", "settings")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val summaryText = manifest.releaseNotes.ifBlank {
                "Version ${manifest.latestVersionName} (Build ${manifest.latestVersionCode}) is ready to download and verify."
            }

            val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("Update Available: Voice Guardian AI v${manifest.latestVersionName}")
                .setContentText("Build ${manifest.latestVersionCode} • SHA-256 verified")
                .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            return try {
                NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
                true
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
