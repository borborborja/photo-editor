package com.hinnka.mycamera.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.hinnka.mycamera.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * The FOSS updater only contacts the project's public GitHub releases API.
 * It downloads the Preview APK, verifies its identity and signing certificate,
 * then delegates installation to Android's package installer.
 */
data class AppUpdateRelease(
    val versionName: String?,
    val downloadUrl: String,
    val fileName: String
)

object AppUpdateManager {
    private const val releasesUrl = "https://api.github.com/repos/borborborja/photo-editor/releases/latest"
    private const val previewAssetName = "app-default-preview.apk"
    private val _readyApk = MutableStateFlow<File?>(null)
    val readyApk: StateFlow<File?> = _readyApk.asStateFlow()
    private val client = OkHttpClient()

    suspend fun checkForUpdate(currentVersion: String = BuildConfig.VERSION_NAME): AppUpdateRelease? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(releasesUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Photo-Editor-FOSS")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext null
                check(response.isSuccessful) { "Update lookup failed: ${response.code}" }
                val responseBody = response.body ?: return@withContext null
                val release = JSONObject(responseBody.string())
                val versionName = release.optString("tag_name").removePrefix("v")
                if (!isNewerVersion(versionName, currentVersion)) return@withContext null
                val assets = release.optJSONArray("assets") ?: return@withContext null
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name") == previewAssetName) {
                        val url = asset.optString("browser_download_url")
                        if (url.isNotBlank()) {
                            return@withContext AppUpdateRelease(versionName, url, previewAssetName)
                        }
                    }
                }
                null
            }
        }

    fun startSilentUpdate(context: Context) = Unit

    suspend fun downloadApk(context: Context, release: AppUpdateRelease): File {
        return withContext(Dispatchers.IO) {
            val updatesDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(updatesDirectory, release.fileName)
            val temporary = File(updatesDirectory, "${release.fileName}.download")
            val request = Request.Builder().url(release.downloadUrl)
                .header("Accept", "application/vnd.android.package-archive")
                .header("User-Agent", "Photo-Editor-FOSS")
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "APK download failed: ${response.code}" }
                val body = response.body ?: error("Empty APK response")
                FileOutputStream(temporary).use { output -> body.byteStream().use { it.copyTo(output) } }
            }
            check(temporary.length() > 0L) { "Empty APK download" }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Unable to finalize APK download" }
            check(isCompatibleUpdate(context, target)) { "Downloaded APK is not a compatible update" }
            target
        }
    }

    fun consumeReadyApk(apkFile: File?) {
        if (_readyApk.value == apkFile) _readyApk.value = null
    }

    fun startInstall(context: Context, apkFile: File): Boolean {
        if (!isCompatibleUpdate(context, apkFile)) return false
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun isCompatibleUpdate(context: Context, apkFile: File): Boolean {
        val packageManager = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
        if (archive.packageName != context.packageName || archive.longVersionCode <= BuildConfig.VERSION_CODE) return false
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val archiveSigners = signingCertificateDigests(archive.signingInfo) ?: return false
        val installedSigners = signingCertificateDigests(installed.signingInfo) ?: return false
        return archiveSigners == installedSigners
    }

    private fun signingCertificateDigests(signingInfo: android.content.pm.SigningInfo?): List<String>? =
        signingInfo?.apkContentsSigners?.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }?.sorted()
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateParts = candidate.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
    val currentParts = current.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
    val count = maxOf(candidateParts.size, currentParts.size)
    return (0 until count).firstOrNull { index ->
        (candidateParts.getOrElse(index) { 0 }) != (currentParts.getOrElse(index) { 0 })
    }?.let { index -> candidateParts.getOrElse(index) { 0 } > currentParts.getOrElse(index) { 0 } } ?: false
}
