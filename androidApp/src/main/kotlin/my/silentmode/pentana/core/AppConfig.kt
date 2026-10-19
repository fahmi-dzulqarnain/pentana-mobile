package my.silentmode.pentana.core

import my.silentmode.pentana.BuildConfig

object AppConfig {
    // Set per build type in androidApp/build.gradle.kts: release is always production,
    // debug is the dev server (pentanaDevApiUrl). Mirrors iosApp AppConfig.baseURL.
    const val BASE_URL = BuildConfig.API_BASE_URL
}
