package fr.moovie.tv.shared

import fr.moovie.tv.BuildConfig

actual val platformName: String = "Android TV"

actual val appVersionName: String = BuildConfig.VERSION_NAME

actual val openSubtitlesApiKey: String = BuildConfig.OPENSUBTITLES_API_KEY
