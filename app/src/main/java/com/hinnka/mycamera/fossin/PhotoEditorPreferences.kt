package com.hinnka.mycamera.fossin

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.photoEditorPreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "photo_editor_preferences",
)

/** Preferences owned by Photo Editor. Camera preferences intentionally remain separate. */
internal enum class PhotoEditorThemeMode { System, Light, Dark }
internal enum class PhotoEditorExportMetadata { Preserve, RemoveLocation, Minimal }

internal data class PhotoEditorPreferences(
    val theme: PhotoEditorThemeMode = PhotoEditorThemeMode.System,
    val gesturesEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val touchIndicatorEnabled: Boolean = true,
    val jpegQuality: Int = 96,
    val exportMaxEdge: Int = 4096,
    val rawExportMaxEdge: Int = 0,
    val metadata: PhotoEditorExportMetadata = PhotoEditorExportMetadata.Preserve,
)

internal class PhotoEditorPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext

    val preferences: Flow<PhotoEditorPreferences> = appContext.photoEditorPreferencesStore.data.map { values ->
        PhotoEditorPreferences(
            theme = values[THEME]?.let { name ->
                runCatching { PhotoEditorThemeMode.valueOf(name) }.getOrNull()
            } ?: PhotoEditorThemeMode.System,
            // Preserve the existing gesture setting when the user upgrades to this release.
            gesturesEnabled = values[GESTURES]
                ?: appContext.getSharedPreferences("fossin_editor_preferences", Context.MODE_PRIVATE)
                    .getBoolean("gesture_mode_enabled", true),
            hapticsEnabled = values[HAPTICS] ?: true,
            touchIndicatorEnabled = values[TOUCH_INDICATOR] ?: true,
            jpegQuality = (values[JPEG_QUALITY] ?: 96).coerceIn(80, 100),
            exportMaxEdge = values[EXPORT_MAX_EDGE] ?: 4096,
            rawExportMaxEdge = values[RAW_EXPORT_MAX_EDGE] ?: 0,
            metadata = values[METADATA]?.let { name ->
                runCatching { PhotoEditorExportMetadata.valueOf(name) }.getOrNull()
            } ?: PhotoEditorExportMetadata.Preserve,
        )
    }

    suspend fun update(transform: (PhotoEditorPreferences) -> PhotoEditorPreferences) {
        appContext.photoEditorPreferencesStore.edit { values ->
            val current = PhotoEditorPreferences(
                theme = values[THEME]?.let { runCatching { PhotoEditorThemeMode.valueOf(it) }.getOrNull() }
                    ?: PhotoEditorThemeMode.System,
                gesturesEnabled = values[GESTURES]
                    ?: appContext.getSharedPreferences("fossin_editor_preferences", Context.MODE_PRIVATE)
                        .getBoolean("gesture_mode_enabled", true),
                hapticsEnabled = values[HAPTICS] ?: true,
                touchIndicatorEnabled = values[TOUCH_INDICATOR] ?: true,
                jpegQuality = (values[JPEG_QUALITY] ?: 96).coerceIn(80, 100),
                exportMaxEdge = values[EXPORT_MAX_EDGE] ?: 4096,
                rawExportMaxEdge = values[RAW_EXPORT_MAX_EDGE] ?: 0,
                metadata = values[METADATA]?.let { runCatching { PhotoEditorExportMetadata.valueOf(it) }.getOrNull() }
                    ?: PhotoEditorExportMetadata.Preserve,
            )
            val next = transform(current)
            values[THEME] = next.theme.name
            values[GESTURES] = next.gesturesEnabled
            values[HAPTICS] = next.hapticsEnabled
            values[TOUCH_INDICATOR] = next.touchIndicatorEnabled
            values[JPEG_QUALITY] = next.jpegQuality.coerceIn(80, 100)
            values[EXPORT_MAX_EDGE] = next.exportMaxEdge
            values[RAW_EXPORT_MAX_EDGE] = next.rawExportMaxEdge
            values[METADATA] = next.metadata.name
        }
    }

    suspend fun clearTransientData() {
        appContext.cacheDir.resolve("fossin-raw-imports").deleteRecursively()
        appContext.cacheDir.resolve("shared").deleteRecursively()
        appContext.cacheDir.resolve("updates").deleteRecursively()
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val GESTURES = booleanPreferencesKey("gestures_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val TOUCH_INDICATOR = booleanPreferencesKey("touch_indicator_enabled")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val EXPORT_MAX_EDGE = intPreferencesKey("export_max_edge")
        val RAW_EXPORT_MAX_EDGE = intPreferencesKey("raw_export_max_edge")
        val METADATA = stringPreferencesKey("export_metadata")
    }
}
