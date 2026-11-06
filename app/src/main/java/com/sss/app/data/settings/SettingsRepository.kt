package com.sss.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "sss_settings")

enum class SortOrder { OLDEST, NEWEST }

data class AppSettings(
    val sortFolders: SortOrder = SortOrder.OLDEST,
    val sortOther: SortOrder = SortOrder.NEWEST,
    val gridColumns: Int = 3,
    val dateHeaders: Boolean = true,
    val autoCrop: Boolean = true,
    val autoRotateWide: Boolean = true,
    val haptic: Boolean = true,
    val bubbleSize: Int = 1,          // 0 = S, 1 = M, 2 = L
    val bubbleOpacity: Float = 0.6f,  // opacity when idle
    val snapEdge: Boolean = true,
    val onboardingDone: Boolean = false,
    val capturePaused: Boolean = false,
    val bubbleX: Int = -1,
    val bubbleY: Int = -1,
    val pdfQuality: Int = 1,          // 0 high, 1 medium, 2 low
    val pdfPerPage: Int = 1
)

object SettingsKeys {
    val SORT_FOLDERS = stringPreferencesKey("sort_folders")
    val SORT_OTHER = stringPreferencesKey("sort_other")
    val GRID_COLUMNS = intPreferencesKey("grid_columns")
    val DATE_HEADERS = booleanPreferencesKey("date_headers")
    val AUTO_CROP = booleanPreferencesKey("auto_crop")
    val AUTO_ROTATE = booleanPreferencesKey("auto_rotate_wide")
    val HAPTIC = booleanPreferencesKey("haptic")
    val BUBBLE_SIZE = intPreferencesKey("bubble_size")
    val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
    val SNAP_EDGE = booleanPreferencesKey("snap_edge")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val CAPTURE_PAUSED = booleanPreferencesKey("capture_paused")
    val BUBBLE_X = intPreferencesKey("bubble_x")
    val BUBBLE_Y = intPreferencesKey("bubble_y")
    val PDF_QUALITY = intPreferencesKey("pdf_quality")
    val PDF_PER_PAGE = intPreferencesKey("pdf_per_page")
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            val d = AppSettings()
            fun order(key: Preferences.Key<String>, def: SortOrder) =
                p[key]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: def
            AppSettings(
                sortFolders = order(SettingsKeys.SORT_FOLDERS, d.sortFolders),
                sortOther = order(SettingsKeys.SORT_OTHER, d.sortOther),
                gridColumns = p[SettingsKeys.GRID_COLUMNS] ?: d.gridColumns,
                dateHeaders = p[SettingsKeys.DATE_HEADERS] ?: d.dateHeaders,
                autoCrop = p[SettingsKeys.AUTO_CROP] ?: d.autoCrop,
                autoRotateWide = p[SettingsKeys.AUTO_ROTATE] ?: d.autoRotateWide,
                haptic = p[SettingsKeys.HAPTIC] ?: d.haptic,
                bubbleSize = p[SettingsKeys.BUBBLE_SIZE] ?: d.bubbleSize,
                bubbleOpacity = p[SettingsKeys.BUBBLE_OPACITY] ?: d.bubbleOpacity,
                snapEdge = p[SettingsKeys.SNAP_EDGE] ?: d.snapEdge,
                onboardingDone = p[SettingsKeys.ONBOARDING_DONE] ?: d.onboardingDone,
                capturePaused = p[SettingsKeys.CAPTURE_PAUSED] ?: d.capturePaused,
                bubbleX = p[SettingsKeys.BUBBLE_X] ?: d.bubbleX,
                bubbleY = p[SettingsKeys.BUBBLE_Y] ?: d.bubbleY,
                pdfQuality = p[SettingsKeys.PDF_QUALITY] ?: d.pdfQuality,
                pdfPerPage = p[SettingsKeys.PDF_PER_PAGE] ?: d.pdfPerPage
            )
        }
        .distinctUntilChanged()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}