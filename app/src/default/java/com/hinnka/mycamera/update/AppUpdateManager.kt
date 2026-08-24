package com.hinnka.mycamera.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * FOSS builds do not contact a vendor update service or install APKs from the
 * network. Updates are distributed through the repository that built the app.
 */
data class AppUpdateRelease(
    val versionName: String?,
    val downloadUrl: String,
    val fileName: String
)

object AppUpdateManager {
    val readyApk: StateFlow<File?> = MutableStateFlow(null)

    suspend fun checkForUpdate(currentVersion: String = ""): AppUpdateRelease? = null

    fun startSilentUpdate(context: Context) = Unit

    suspend fun downloadApk(context: Context, release: AppUpdateRelease): File {
        throw UnsupportedOperationException("Remote updates are disabled in the FOSS build")
    }

    fun consumeReadyApk(apkFile: File?) = Unit

    fun startInstall(context: Context, apkFile: File): Boolean = false
}
