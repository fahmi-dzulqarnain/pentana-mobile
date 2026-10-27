plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "my.silentmode.pentana"
    compileSdk = 36

    defaultConfig {
        applicationId = "my.silentmode.pentana"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    // Sideloaded APKs only update in place when every release carries the same signature,
    // so the keystore lives outside the repo and its location and passwords come from
    // ~/.gradle/gradle.properties. Without them the release build is left unsigned.
    val releaseStoreFile = providers.gradleProperty("pentanaReleaseStoreFile").orNull
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("pentanaReleaseStorePassword").get()
                keyAlias = providers.gradleProperty("pentanaReleaseKeyAlias").get()
                keyPassword = providers.gradleProperty("pentanaReleaseKeyPassword").get()
            }
        }
    }

    // Release builds always talk to production. Debug builds talk to a dev server, by default
    // this Mac on the office network; override with pentanaDevApiUrl in ~/.gradle/gradle.properties
    // (e.g. http://10.0.2.2:8000/api/v1 for the emulator).
    val devApiUrl = providers.gradleProperty("pentanaDevApiUrl")
        .getOrElse("http://192.168.0.177:8000/api/v1")

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$devApiUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "API_BASE_URL", "\"https://pentana.silentmode.net/api/v1\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true // VERSION_CODE for the sideloaded update check, API_BASE_URL
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    implementation(project(":shared"))
    // ApiClient's public constructor signature references Ktor's HttpClientEngine, so the
    // app needs ktor-client-core on its compile classpath (:shared exposes it as implementation).
    implementation(libs.ktor.client.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)

    testImplementation(kotlin("test"))
}
