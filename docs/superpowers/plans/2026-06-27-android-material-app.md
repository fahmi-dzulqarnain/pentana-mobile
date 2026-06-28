# PENTANA Android (Material 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new Jetpack Compose Android app (`androidApp/`) against the existing `:shared` KMP module, at full UI parity with the iOS app, following the Material 3 handoff.

**Architecture:** State-driven navigation (one `PentanaApp` composable: `Scaffold` + `NavigationBar` + `ModalBottomSheet` hosts; no nav library). AndroidX `ViewModel` + `StateFlow`, manual DI via an `AppContainer` held by `Application`. `EncryptedSharedPreferences` `TokenStore`. The shared repos already do networking; the app supplies UI + a Main dispatcher (`kotlinx-coroutines-android`).

**Tech Stack:** Kotlin 2.1.21, AGP 8.7.2, Jetpack Compose (BOM 2024.12.01) Material 3, Lifecycle 2.8.7, Activity-Compose 1.9.3, security-crypto 1.1.0-alpha06, Ktor (via `:shared`).

**Spec:** `docs/superpowers/specs/2026-06-27-android-material-app-design.md` — read it; it embeds all token hex values and per-screen data bindings.
**Visual source of truth:** `~/Downloads/pentana-ios-android-ui-design 2/project/android/*.jsx` — screen tasks cite the exact component (e.g. `screens1.jsx → Bills`) for spacing/values. Recreate the visual output in Compose; don't port JSX structure.

---

## Conventions for screen tasks

Screens are verified by **build + visual** (`./gradlew :androidApp:assembleDebug`, then run on the emulator), not unit tests — per the spec, only pure logic gets unit tests (Tasks 3 & 8 & 10). Each screen task: create the file(s), implement the composable + ViewModel against the cited repo, compile, commit. "Match handoff X" means reproduce the layout/spacing/colors of component X from the cited jsx using the theme tokens + components built in Tasks 4–5.

Package root: `my.silentmode.pentana` (app), under `androidApp/src/main/kotlin/my/silentmode/pentana/`.

---

## Task 1: Module scaffold — builds an empty Compose app

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `androidApp/build.gradle.kts`
- Create: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/res/values/themes.xml`, `strings.xml`
- Create: `androidApp/src/main/kotlin/my/silentmode/pentana/MainActivity.kt`
- Create: `androidApp/src/main/kotlin/my/silentmode/pentana/App.kt`

- [ ] **Step 1: Add the app to the build**

In `settings.gradle.kts`, add after `include(":shared")`:
```kotlin
include(":androidApp")
```

- [ ] **Step 2: Add version-catalog entries**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
compose-bom = "2024.12.01"
activity-compose = "1.9.3"
lifecycle = "2.8.7"
security-crypto = "1.1.0-alpha06"
core-ktx = "1.13.1"
```
Under `[libraries]` add:
```toml
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "security-crypto" }
```
Under `[plugins]` add:
```toml
androidApplication = { id = "com.android.application", version.ref = "agp" }
kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 3: Create `androidApp/build.gradle.kts`**
```kotlin
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
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

dependencies {
    implementation(project(":shared"))

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
```

- [ ] **Step 4: Create `AndroidManifest.xml`**
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:theme="@style/Theme.Pentana"
        android:usesCleartextTraffic="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Pentana">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```
> `usesCleartextTraffic` is dev-only (LAN HTTP); a release build drops it and uses the HTTPS prod URL.

- [ ] **Step 5: Create `res/values/strings.xml` and `themes.xml`**

`strings.xml`:
```xml
<resources><string name="app_name">PENTANA</string></resources>
```
`themes.xml` (a base theme; Compose drives the real colors):
```xml
<resources>
    <style name="Theme.Pentana" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 6: Create `App.kt` (Application — AppContainer wired in Task 3)**
```kotlin
package my.silentmode.pentana

import android.app.Application

class App : Application()
```

- [ ] **Step 7: Create `MainActivity.kt` (placeholder Compose content)**
```kotlin
package my.silentmode.pentana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Text("PENTANA") }
    }
}
```

- [ ] **Step 8: Build to verify the module compiles**

Run: `./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Fix any version/compileSdk warnings; compileSdk 36 may warn but matches `:shared`.)

- [ ] **Step 9: Commit**
```bash
git add settings.gradle.kts gradle/libs.versions.toml androidApp/
git commit -m "feat(android): scaffold Compose app module against :shared"
```

---

## Task 2: Theme & design system

**Files:**
- Create: `androidApp/src/main/kotlin/my/silentmode/pentana/ui/theme/Color.kt`
- Create: `.../ui/theme/PentanaColors.kt`
- Create: `.../ui/theme/Type.kt`
- Create: `.../ui/theme/Shape.kt`
- Create: `.../ui/theme/Theme.kt`

- [ ] **Step 1: `Color.kt` — raw token colors (light + dark) from the spec §4.1/§4.2**
```kotlin
package my.silentmode.pentana.ui.theme

import androidx.compose.ui.graphics.Color

// Light
val PrimaryL = Color(0xFF9A4A00); val OnPrimaryL = Color(0xFFFFFFFF)
val PrimaryContainerL = Color(0xFFFFDCC2); val OnPrimaryContainerL = Color(0xFF3A1A00)
val SecondaryL = Color(0xFF4B4196); val OnSecondaryL = Color(0xFFFFFFFF)
val SecondaryContainerL = Color(0xFFE5DEFF); val OnSecondaryContainerL = Color(0xFF150A5E)
val TertiaryL = Color(0xFF006C4C); val TertiaryContainerL = Color(0xFF88F8C5); val OnTertiaryContainerL = Color(0xFF002115)
val ErrorL = Color(0xFFBA1A1A); val OnErrorL = Color(0xFFFFFFFF); val ErrorContainerL = Color(0xFFFFDAD6); val OnErrorContainerL = Color(0xFF410002)
val SurfaceL = Color(0xFFFFF8F4); val OnSurfaceL = Color(0xFF211A14)
val OnSurfaceVariantL = Color(0xFF50453B); val OutlineL = Color(0xFF837469); val OutlineVariantL = Color(0xFFD6C3B6)
val ScLowestL = Color(0xFFFFFFFF); val ScLowL = Color(0xFFFCF1E9); val ScL = Color(0xFFF6ECE3); val ScHighL = Color(0xFFF1E6DD); val ScHighestL = Color(0xFFEBE0D7)
val SurfaceDimL = Color(0xFFE8E0D9)

// Dark
val PrimaryD = Color(0xFFFFB784); val OnPrimaryD = Color(0xFF532300)
val PrimaryContainerD = Color(0xFF763500); val OnPrimaryContainerD = Color(0xFFFFDCC2)
val SecondaryD = Color(0xFFC8BFFF); val OnSecondaryD = Color(0xFF2C2278)
val SecondaryContainerD = Color(0xFF433A8F); val OnSecondaryContainerD = Color(0xFFE5DEFF)
val TertiaryD = Color(0xFF6BDBAA); val TertiaryContainerD = Color(0xFF005138); val OnTertiaryContainerD = Color(0xFF88F8C5)
val ErrorD = Color(0xFFFFB4AB); val OnErrorD = Color(0xFF690005); val ErrorContainerD = Color(0xFF93000A); val OnErrorContainerD = Color(0xFFFFDAD6)
val SurfaceDarkD = Color(0xFF19120C); val OnSurfaceD = Color(0xFFEFE0D5)
val OnSurfaceVariantD = Color(0xFFD6C3B6); val OutlineD = Color(0xFF9F8D81); val OutlineVariantD = Color(0xFF50453B)
val ScLowestD = Color(0xFF130D08); val ScLowD = Color(0xFF221A13); val ScD = Color(0xFF261E16); val ScHighD = Color(0xFF312820); val ScHighestD = Color(0xFF3C332A)
val SurfaceBrightD = Color(0xFF41372F)
```

- [ ] **Step 2: `PentanaColors.kt` — domain/status extras + CompositionLocal**
```kotlin
package my.silentmode.pentana.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Tri(val color: Color, val container: Color, val onContainer: Color)
data class Pair2(val color: Color, val container: Color)

data class PentanaColors(
    val dues: Tri, val lunch: Tri, val activ: Tri, val proof: Tri,
    val ok: Pair2, val warn: Pair2, val bad: Pair2,
)

val LightPentana = PentanaColors(
    dues = Tri(Color(0xFF15489E), Color(0xFFD9E2FF), Color(0xFF001A43)),
    lunch = Tri(Color(0xFF9A4A00), Color(0xFFFFDCC2), Color(0xFF3A1A00)),
    activ = Tri(Color(0xFF006C4C), Color(0xFF88F8C5), Color(0xFF002115)),
    proof = Tri(Color(0xFF5B3FD0), Color(0xFFE6DEFF), Color(0xFF1C0066)),
    ok = Pair2(Color(0xFF006C4C), Color(0xFF88F8C5)),
    warn = Pair2(Color(0xFF8A5000), Color(0xFFFFDDB3)),
    bad = Pair2(Color(0xFFBA1A1A), Color(0xFFFFDAD6)),
)
val DarkPentana = PentanaColors(
    dues = Tri(Color(0xFFAFC6FF), Color(0xFF1A3878), Color(0xFFD9E2FF)),
    lunch = Tri(Color(0xFFFFB784), Color(0xFF763500), Color(0xFFFFDCC2)),
    activ = Tri(Color(0xFF6BDBAA), Color(0xFF005138), Color(0xFF88F8C5)),
    proof = Tri(Color(0xFFCBBEFF), Color(0xFF43308F), Color(0xFFE6DEFF)),
    ok = Pair2(Color(0xFF6BDBAA), Color(0xFF005138)),
    warn = Pair2(Color(0xFFFFB95C), Color(0xFF5E3F00)),
    bad = Pair2(Color(0xFFFFB4AB), Color(0xFF93000A)),
)

val LocalPentanaColors = staticCompositionLocalOf { LightPentana }
```

- [ ] **Step 3: `Type.kt` — M3 type scale + money styles (spec §4.3)**

Use the Roboto Flex/Roboto default (`FontFamily.Default`); map the spec sizes/weights into a `Typography`. Add money `TextStyle`s as top-level vals (`moneyLarge` 34sp/W600/-0.5sp, `moneyMedium` 18sp/W600), both with `fontFeatureSettings = "tnum"`. Provide the full `Typography(...)` with displayLarge 48, headlineLarge 30, headlineMedium 26, titleLarge 20, titleMedium 16, bodyLarge 16, bodyMedium 14, labelMedium 12, labelLarge 14 (weights/letter-spacing per `tokens.css`).

- [ ] **Step 4: `Shape.kt`**
```kotlin
package my.silentmode.pentana.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PentShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
val HeroShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 10.dp)
```

- [ ] **Step 5: `Theme.kt` — `PentanaTheme` with dynamic color (API 31+) + brand fallback**
```kotlin
package my.silentmode.pentana.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = PrimaryL, onPrimary = OnPrimaryL, primaryContainer = PrimaryContainerL, onPrimaryContainer = OnPrimaryContainerL,
    secondary = SecondaryL, onSecondary = OnSecondaryL, secondaryContainer = SecondaryContainerL, onSecondaryContainer = OnSecondaryContainerL,
    tertiary = TertiaryL, tertiaryContainer = TertiaryContainerL, onTertiaryContainer = OnTertiaryContainerL,
    error = ErrorL, onError = OnErrorL, errorContainer = ErrorContainerL, onErrorContainer = OnErrorContainerL,
    surface = SurfaceL, onSurface = OnSurfaceL, onSurfaceVariant = OnSurfaceVariantL,
    outline = OutlineL, outlineVariant = OutlineVariantL,
    surfaceContainerLowest = ScLowestL, surfaceContainerLow = ScLowL, surfaceContainer = ScL,
    surfaceContainerHigh = ScHighL, surfaceContainerHighest = ScHighestL, surfaceDim = SurfaceDimL,
    background = SurfaceL, onBackground = OnSurfaceL,
)
private val DarkScheme = darkColorScheme(
    primary = PrimaryD, onPrimary = OnPrimaryD, primaryContainer = PrimaryContainerD, onPrimaryContainer = OnPrimaryContainerD,
    secondary = SecondaryD, onSecondary = OnSecondaryD, secondaryContainer = SecondaryContainerD, onSecondaryContainer = OnSecondaryContainerD,
    tertiary = TertiaryD, tertiaryContainer = TertiaryContainerD, onTertiaryContainer = OnTertiaryContainerD,
    error = ErrorD, onError = OnErrorD, errorContainer = ErrorContainerD, onErrorContainer = OnErrorContainerD,
    surface = SurfaceDarkD, onSurface = OnSurfaceD, onSurfaceVariant = OnSurfaceVariantD,
    outline = OutlineD, outlineVariant = OutlineVariantD,
    surfaceContainerLowest = ScLowestD, surfaceContainerLow = ScLowD, surfaceContainer = ScD,
    surfaceContainerHigh = ScHighD, surfaceContainerHighest = ScHighestD, surfaceBright = SurfaceBrightD,
    background = SurfaceDarkD, onBackground = OnSurfaceD,
)

@Composable
fun PentanaTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalPentanaColors provides if (dark) DarkPentana else LightPentana) {
        MaterialTheme(colorScheme = scheme, typography = PentTypography, shapes = PentShapes, content = content)
    }
}
```
(Define `PentTypography` in `Type.kt`.)

- [ ] **Step 6: Build to verify the theme compiles**

Run: `./gradlew :androidApp:assembleDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**
```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/ui/theme/
git commit -m "feat(android): M3 theme — colors, type, shape, dynamic color"
```

---

## Task 3: Platform seams (AppConfig, TokenStore, AppContainer)

**Files:**
- Create: `.../core/AppConfig.kt`, `.../core/PrefsTokenStore.kt`, `.../core/AppContainer.kt`
- Modify: `.../App.kt`

- [ ] **Step 1: `AppConfig.kt` (mirror iOS `AppConfig.swift`)**
```kotlin
package my.silentmode.pentana.core

object AppConfig {
    // Mirror iosApp AppConfig.baseURL. Update IP per network (`ipconfig getifaddr en0`);
    // swap to https://pentana.silentmode.net/api/v1 for a release build.
    const val BASE_URL = "http://192.168.0.177:8000/api/v1"
}
```

- [ ] **Step 2: `PrefsTokenStore.kt` (EncryptedSharedPreferences-backed `TokenStore`)**
```kotlin
package my.silentmode.pentana.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import my.silentmode.pentana.shared.TokenStore

class PrefsTokenStore(context: Context) : TokenStore {
    private val prefs by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "pentana_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    override fun get(): String? = prefs.getString(KEY, null)
    override fun save(token: String) { prefs.edit().putString(KEY, token).apply() }
    override fun clear() { prefs.edit().remove(KEY).apply() }
    private companion object { const val KEY = "auth_token" }
}
```
> If security-crypto causes a build/runtime issue, fall back to a plain `SharedPreferences` impl with the same body (drop the MasterKey + schemes).

- [ ] **Step 3: `AppContainer.kt` (manual DI — the 6 used repos)**
```kotlin
package my.silentmode.pentana.core

import android.content.Context
import my.silentmode.pentana.shared.*

class AppContainer(context: Context) {
    private val client = ApiClient(AppConfig.BASE_URL, PrefsTokenStore(context.applicationContext), engine = null)
    val auth = AuthRepository(client)
    val dashboard = DashboardRepository(client)
    val bills = BillsRepository(client)
    val lunch = LunchRepository(client)
    val activities = ActivitiesRepository(client)
    val notifications = NotificationsRepository(client)
}
```

- [ ] **Step 4: Hold the container in `App.kt`**
```kotlin
package my.silentmode.pentana

import android.app.Application
import my.silentmode.pentana.core.AppContainer

class App : Application() {
    lateinit var container: AppContainer; private set
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}
```

- [ ] **Step 5: Build → `BUILD SUCCESSFUL`.** Run: `./gradlew :androidApp:assembleDebug`

- [ ] **Step 6: Commit**
```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/core androidApp/src/main/kotlin/my/silentmode/pentana/App.kt
git commit -m "feat(android): platform seams — AppConfig, encrypted TokenStore, AppContainer"
```

---

## Task 4: Core UI components

**Files (one file per group):**
- Create: `.../ui/components/Icons.kt` (map handoff Lucide names → `Icons.*`)
- Create: `.../ui/components/Chips.kt` (`StatusChip` + `ChipKind`)
- Create: `.../ui/components/Cards.kt` (`PentElevatedCard`, `PentFilledCard`, `DomainStatCard`, `LeadingIcon`)
- Create: `.../ui/components/ListItems.kt` (`PentListItem`, `SectionHeader`, `HairlineDivider`)
- Create: `.../ui/components/Inputs.kt` (`PentTextField`, `PentDropdown`, `PentCheckbox`, `SingleSelectRow`, `PhotoPickerTile`)
- Create: `.../ui/components/Buttons.kt` (`PentButton` variants, `SubmitFab`)
- Create: `.../ui/components/States.kt` (`EmptyState`, `LoadingState`, `ErrorState`, `Money` text)
- Create: `.../ui/components/Chrome.kt` (`PentTopBar`, `PentNavBar`, `NavDest`)

- [ ] **Step 1: `Chips.kt` — the CHIP map (handoff `primitives.jsx → CHIP/Chip`)**

Define `enum class ChipKind { Paid, Partial, Unpaid, Overdue, Registered, Waitlisted, Open, Closed, VoteNow, Responded }` and a `@Composable fun StatusChip(kind, label?, modifier)` reproducing each kind's color/container/label/icon (check/clock)/dot/outline using `MaterialTheme.colorScheme` + `LocalPentanaColors.current` (ok/warn/bad). Mapping (from spec): paid/registered/responded→ok+check; partial/votenow→warn+dot; waitlisted→warn+clock; unpaid/closed→outline+onSurfaceVariant; overdue→bad+dot; open→activ. Height ~26dp, rounded 8dp.

- [ ] **Step 2: `Cards.kt`** — `LeadingIcon(icon, container, onContainer, size)` (tonal rounded box); `PentElevatedCard` (surfaceContainerLow, 16dp, `tonalElevation`/`shadowElevation` ~1dp); `PentFilledCard` (surfaceContainerHigh, 16dp); `DomainStatCard(icon, domain: Tri, title, onClick, content)` matching `screens1.jsx → HomeItem` (46dp leading icon, titleLarge, trailing `Icons.Default.ChevronRight`).

- [ ] **Step 3: `ListItems.kt`** — `PentListItem(leadingIcon?, headline, supporting?, trailingValue?/valueSub?, trailing?, last)` matching `primitives.jsx → ListItem` (hairline at left=78dp when leading, else 18dp; value in money style). `SectionHeader(text)` (primary, titleSmall, padding 16/20/8). `HairlineDivider`.

- [ ] **Step 4: `Buttons.kt`** — `PentButton(text, onClick, variant = Filled|Tonal|Outlined|Text|Destructive, enabled, loading, leadingIcon?, modifier)`; loading → `CircularProgressIndicator` 20dp inside. Height 48dp, fully rounded. `SubmitFab(onClick)` = M3 `ExtendedFloatingActionButton` (primaryContainer) icon `Icons.Default.Upload`, "Submit proof".

- [ ] **Step 5: `Inputs.kt`** — `PentTextField` (filled M3 `TextField`, supporting/error text, leading icon, `singleLine`/multiline); `PentDropdown` (`ExposedDropdownMenuBox` over options); `PentCheckbox(label, sub?, checked, onCheckedChange)`; `SingleSelectRow(title, sub?, selected, onClick)` (`RadioButton` + texts, `selectableGroup` used by caller); `PhotoPickerTile(selected: PickedPhoto?, onPick)` — outlined dashed empty ("Add receipt photo") vs selected (thumb + name + size + clear), matching `primitives.jsx → PhotoSurface`.

- [ ] **Step 6: `States.kt`** — `Money(text, style)` (prefixes nothing; callers pass "MYR x"); `EmptyState(icon, color, container, title, body?, action?)`; `LoadingState()` (centered `CircularProgressIndicator` + "Loading…"); `ErrorState(message, onRetry)` (`EmptyState` cloud-off + Retry tonal).

- [ ] **Step 7: `Chrome.kt`** — `enum class NavDest(icon, label) { Home, Bills, Lunch, Activities }`; `PentTopBar(title: String?, unread: Int, onAvatar, onBell)` = M3 `LargeTopAppBar`-style: action row with circular avatar (initials) lead + `BadgedBox { Badge { unread, "9+" cap } }` bell trail, optional headline title (null on Home). `PentNavBar(active, onSelect)` = `NavigationBar` with 4 `NavigationBarItem`s (pill indicator via `NavigationBarItemDefaults.colors(indicatorColor = secondaryContainer)`).

- [ ] **Step 8: Add `@Preview`s** for `StatusChip`, `DomainStatCard`, `PentNavBar` (light + dark via `PentanaTheme`).

- [ ] **Step 9: Build → `BUILD SUCCESSFUL`.** Run: `./gradlew :androidApp:assembleDebug`

- [ ] **Step 10: Commit**
```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/ui/components
git commit -m "feat(android): M3 component library (chrome, cards, chips, inputs, states)"
```

---

## Task 5: Formatting & logic utils (TDD)

**Files:**
- Create: `.../core/Format.kt`
- Test: `androidApp/src/test/kotlin/my/silentmode/pentana/FormatTest.kt`

- [ ] **Step 1: Write failing tests `FormatTest.kt`**
```kotlin
package my.silentmode.pentana

import my.silentmode.pentana.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test fun money_prefixes_myr() = assertEquals("MYR 70.00", myr("70.00"))
    @Test fun firstName_takes_first_token() = assertEquals("Aisyah", firstName("Aisyah Rahman"))
    @Test fun firstName_handles_blank() = assertEquals("there", firstName(""))
    @Test fun excerpt_strips_html_and_truncates() =
        assertEquals("Hello world", excerpt("<p>Hello <b>world</b></p>"))
    @Test fun relativeTime_minutes() =
        assertEquals("2h", relativeTime(epochMillisNow = 7_200_000L, createdMillis = 0L))
}
```

- [ ] **Step 2: Run → FAIL** (`unresolved reference`). Run: `./gradlew :androidApp:testDebugUnitTest`

- [ ] **Step 3: Implement `Format.kt`** — `myr(s)` → `"MYR $s"`; `firstName(full)` → `full.trim().substringBefore(' ').ifBlank { "there" }`; `excerpt(html, max=140)` → strip tags via `Regex("<[^>]*>")`, collapse whitespace, trim, ellipsize; `relativeTime(epochMillisNow, createdMillis)` → "now/<m>m/<h>h/<d>d" buckets; plus an ISO-8601 parse helper `parseIso(String?): Long?` (use `java.time.Instant`/`OffsetDateTime`, minSdk 26 has java.time).

- [ ] **Step 4: Run → PASS.** Run: `./gradlew :androidApp:testDebugUnitTest`

- [ ] **Step 5: Commit**
```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/core/Format.kt androidApp/src/test
git commit -m "feat(android): formatting utils (money, first name, excerpt, relative time) + tests"
```

---

## Task 6: Session, app shell, Login

**Files:**
- Create: `.../ui/session/SessionViewModel.kt`, `.../ui/VmFactory.kt`
- Create: `.../feature/login/LoginScreen.kt`, `.../feature/login/LoginViewModel.kt`
- Create: `.../PentanaApp.kt`
- Modify: `.../MainActivity.kt`

- [ ] **Step 1: `VmFactory.kt`** — a `viewModelFactory {}` helper (or `ViewModelProvider.Factory`) that builds VMs from the `AppContainer` (read `App.container`). Provide a `@Composable fun appContainer(): AppContainer = (LocalContext.current.applicationContext as App).container`.

- [ ] **Step 2: `SessionViewModel.kt`** — state `data class SessionState(user: UserDto?, unread: Int, bootstrapping: Boolean)`; `bootstrap()` (if `auth.isLoggedIn()` → `me()` + `refreshBadge()`, else done; on failure `auth.logout()`), `onLoggedIn(user)`, `logout()` (`auth.logout()`), `refreshBadge()` (`notifications().unreadCount`), `markRead()` (`notifications.markAllRead()` → unread 0). All in `viewModelScope` with try/catch.

- [ ] **Step 3: `LoginViewModel.kt`** — fields email/password, `state: Idle|Loading|Error(msg)`; `submit(onSuccess: (UserDto)->Unit)` → `auth.login(email, password, "Android")`; map failure to "The provided credentials are incorrect." Disable submit until both non-blank.

- [ ] **Step 4: `LoginScreen.kt`** — match `screens1.jsx → Login`: centered mark image (Task 12 adds the asset; use a placeholder `Icons` box until then), "PENTANA" headline (letterSpacing 0.12em), "Member sign in", `PentTextField` email (leading Mail) + password (leading Lock, error supporting text), full-width filled `PentButton` "Sign in" (disabled until filled, loading spinner), "Need access? Claim your account on the web" hint, error → `Snackbar` via the host. Calls `onSuccess` → `session.onLoggedIn`.

- [ ] **Step 5: `PentanaApp.kt`** — root composable wrapped in `PentanaTheme`:
  - collect `SessionViewModel.state` via `collectAsStateWithLifecycle`; `LaunchedEffect(Unit){ session.bootstrap() }`.
  - if `bootstrapping` → `LoadingState()`; else if `user == null` → `LoginScreen`; else the tab `Scaffold`:
    - `topBar = PentTopBar(title by tab, unread, onAvatar = { showProfile = true }, onBell = { scope: markRead + showNotifications = true })`
    - `bottomBar = PentNavBar(active, onSelect)`
    - body = `when (active) { Home -> HomeScreen(...); Bills -> BillsScreen(...); Lunch -> LunchScreen(...); Activities -> ActivitiesScreen(...) }` (screens added in later tasks; stub with `LoadingState()` placeholders that compile now).
    - sheet state (`rememberSaveable`) for Profile/Notifications/SubmitProof/Registration; render the `ModalBottomSheet`s (added in their tasks).
  - `rememberSaveable` for `active: NavDest`.

- [ ] **Step 6: `MainActivity.kt`** — `setContent { PentanaApp() }`.

- [ ] **Step 7: Build → `BUILD SUCCESSFUL`.** Run: `./gradlew :androidApp:assembleDebug`

- [ ] **Step 8: Run on emulator and verify Login renders, sign-in reaches the dev API**

Start the API: `php artisan serve --host=0.0.0.0 --port=8000` (in `pentana-system`). Launch the app (Android Studio, or `./gradlew :androidApp:installDebug` + launch). Expected: Login screen in light/dark; entering valid member creds advances past login.

- [ ] **Step 9: Commit**
```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana
git commit -m "feat(android): session, app shell (Scaffold+NavBar), Login"
```

---

## Task 7: Home dashboard

**Files:** Create `.../feature/home/HomeViewModel.kt`, `.../feature/home/HomeScreen.kt`; wire into `PentanaApp.kt`.

- [ ] **Step 1: `HomeViewModel.kt`** — `state: Loading|Error|Content(DashboardDto)`; `load()`/`refresh()` → `dashboard.dashboard()`.
- [ ] **Step 2: `HomeScreen.kt`** — match `screens1.jsx → Home`: greeting hero (`primaryContainer`, `HeroShape`, date via `parseIso`/now + `firstName(user.name)`), then 4 `DomainStatCard`s bound to `DashboardDto` per spec §6.2 (dues/lunch/activities/proofs), each `onClick` → `onSwitchTab(dest)`. Celebratory all-clear card when `totalOutstanding == "0.00"` && `pendingProofsCount == 0` && nextLunch responded/none. Wrap in `PullToRefreshBox`. Loading/Error states.
- [ ] **Step 3: Wire** `HomeScreen` into `PentanaApp` (replace stub), passing `onSwitchTab`.
- [ ] **Step 4: Build → SUCCESSFUL.** `./gradlew :androidApp:assembleDebug`
- [ ] **Step 5: Run** — verify Home loads real dashboard data, cards switch tabs.
- [ ] **Step 6: Commit** `git commit -am "feat(android): Home dashboard"`

---

## Task 8: Bills + Submit-proof sheet

**Files:** Create `.../feature/bills/BillsViewModel.kt`, `BillsScreen.kt`, `SubmitProofSheet.kt`, `.../core/Photo.kt`; Test `.../bills/SubmitProofValidationTest.kt`.

- [ ] **Step 1 (TDD): Write `SubmitProofValidationTest.kt`** — `assertTrue(canSubmitProof(amount="70.00", hasPhoto=true))`, `assertFalse(canSubmitProof("", true))`, `assertFalse(canSubmitProof("70.00", false))`.
- [ ] **Step 2: Run → FAIL.** `./gradlew :androidApp:testDebugUnitTest`
- [ ] **Step 3: Implement** `canSubmitProof(amount, hasPhoto) = amount.isNotBlank() && hasPhoto` (in `BillsViewModel.kt` or `Format.kt`). Run → PASS.
- [ ] **Step 4: `Photo.kt`** — `data class PickedPhoto(bytes: ByteArray, name: String, sizeLabel: String)`; `fun readPhoto(context, uri): PickedPhoto` via `contentResolver.openInputStream(uri).readBytes()` + `DocumentFile`/cursor for display name.
- [ ] **Step 5: `BillsViewModel.kt`** — `state: Loading|Error|Content(summary, bills)`; `load()` → `bills.summary()` + `bills.bills()`. Submit state: `Idle|Submitting|Error|Success`; `submit(amount, note, photo)` → `bills.submitPaymentProof(photo.bytes, photo.name, amount, note)`; on success refresh + signal Home refresh.
- [ ] **Step 6: `BillsScreen.kt`** — match `screens1.jsx → Bills`: summary card (`secondaryContainer`, "TOTAL OUTSTANDING" + `moneyLarge`, credit + unpaid stats), `SectionHeader("Bill history")`, `PentFilledCard` of `PentListItem`s (receipt icon, month, "Due X · Paid Y", trailing outstanding money + `StatusChip(status)`), `SubmitFab` (bottom-end) → opens sheet. Empty "No bills yet." `PullToRefreshBox`.
- [ ] **Step 7: `SubmitProofSheet.kt`** — `ModalBottomSheet` match `screens1.jsx → SubmitProof`: amount field (leading banknote), note multiline, `PhotoPickerTile` with `rememberLauncherForActivityResult(PickVisualMedia)`, footer `PentButton` (disabled per `canSubmitProof`, submitting spinner, success "Submitted" ok-color); error Snackbar. Dismiss + refresh on success.
- [ ] **Step 8: Wire** Bills + sheet into `PentanaApp`.
- [ ] **Step 9: Build + test green.** `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest`
- [ ] **Step 10: Run** — verify bills list + submit a proof against the dev API.
- [ ] **Step 11: Commit** `git commit -am "feat(android): Bills + submit payment proof sheet"`

---

## Task 9: Lunch

**Files:** Create `.../feature/lunch/LunchViewModel.kt`, `LunchScreen.kt`.

- [ ] **Step 1: `LunchViewModel.kt`** — `state: Loading|Error|Content(List<LunchDto>)`; `load()` → `lunch.lunches()`; `choose(lunchId, optionId)` → `lunch.chooseOption(...)`; `notAttending(lunchId)` → `lunch.markNotAttending(...)`; replace the updated lunch in state.
- [ ] **Step 2: `LunchScreen.kt`** — match `screens2.jsx → Lunch/LunchCard`: per-lunch `PentFilledCard` (menu title + "{date} · {caterer}" + status chip); open → `SingleSelectRow`s for `options` + "Not attending" (reflect `myMealOptionId`, `selectableGroup`); closed → locked summary supporting text (ordered X / not attending / no order) per spec §6.5; footer deadline row. Empty "No upcoming lunches." `PullToRefreshBox`.
- [ ] **Step 3: Wire** into `PentanaApp`.
- [ ] **Step 4: Build → SUCCESSFUL.** Run + verify voting persists.
- [ ] **Step 5: Commit** `git commit -am "feat(android): Lunch voting"`

---

## Task 10: Activities + Registration sheet (dynamic form, TDD)

**Files:** Create `.../feature/activities/ActivitiesViewModel.kt`, `ActivitiesScreen.kt`, `RegistrationSheet.kt`, `.../feature/activities/RegForm.kt`; Test `.../activities/RegFormTest.kt`.

- [ ] **Step 1 (TDD): `RegFormTest.kt`** — given `questions = [QuestionDto("name","Name",required=true), QuestionDto("diet","Diet",required=false)]`: `assertFalse(requiredAnswered(questions, mapOf()))`; `assertTrue(requiredAnswered(questions, mapOf("name" to "Aisyah")))`; checkbox encodes: `assertEquals("true", checkboxValue(true))`.
- [ ] **Step 2: Run → FAIL.** `./gradlew :androidApp:testDebugUnitTest`
- [ ] **Step 3: `RegForm.kt`** — `requiredAnswered(questions, answers)` → all `required` keys present & non-blank; `checkboxValue(b) = if (b) "true" else "false"`. Run → PASS.
- [ ] **Step 4: `ActivitiesViewModel.kt`** — `state: Loading|Error|Content(List<ActivityDto>)`; `load()` → `activities.activities()`; `register(id, answers)` → `activities.register(...)`; `cancel(id)` → `activities.cancel(...)`; replace updated activity.
- [ ] **Step 5: `ActivitiesScreen.kt`** — match `screens2.jsx → Activities/ActivityCard`: `PentElevatedCard` per activity (title + spots chip from `spotsLeft`/`myStatus`, date + location rows, `excerpt(description)`), state action per spec §6.6 (register→open sheet if `questions` non-empty else `register(id, emptyMap)`; registered/waitlisted→cancel; closed→label). Empty "No upcoming activities."
- [ ] **Step 6: `RegistrationSheet.kt`** — `ModalBottomSheet` match `screens2.jsx → ActivityRegistration`: tertiaryContainer blurb, render `questions` by type (text/textarea→`PentTextField`, select→`PentDropdown(options)`, checkbox→`PentCheckbox`), `*` on required; footer Register disabled until `requiredAnswered`, submitting spinner; error Snackbar. Submit → `register(id, answers)`; dismiss + refresh.
- [ ] **Step 7: Wire** into `PentanaApp`.
- [ ] **Step 8: Build + tests green.** `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest`
- [ ] **Step 9: Run** — register for an activity with questions; cancel one.
- [ ] **Step 10: Commit** `git commit -am "feat(android): Activities + dynamic registration form"`

---

## Task 11: Profile + Notifications sheets

**Files:** Create `.../feature/profile/ProfileSheet.kt`, `.../feature/notifications/NotificationsViewModel.kt`, `NotificationsSheet.kt`.

- [ ] **Step 1: `ProfileSheet.kt`** — `ModalBottomSheet` match `screens2.jsx → Profile`: avatar (84dp initials), name, email; "Membership" `PentFilledCard` (Category=`memberCategory`, Birthday=`birthday`, Credit balance=`myr(credit)` ok color); separate card "Sign out" (destructive, log-out) → `session.logout()`.
- [ ] **Step 2: `NotificationsViewModel.kt`** — `state: Loading|Error|Content(List<NotificationDto>)`; `load()` → `notifications.notifications()`; on open call `session.markRead()`.
- [ ] **Step 3: `NotificationsSheet.kt`** — `ModalBottomSheet` match `screens2.jsx → Notifications/NotifRow`: rows with keyword-derived leading icon (spec §6.9), title/body/relativeTime, unread dot + surfaceContainerHigh bg; "Mark all read" text button. Empty "No notifications yet." `LaunchedEffect` on show → `session.markRead()` (clears badge).
- [ ] **Step 4: Wire** both sheets' visibility into `PentanaApp` (avatar → profile; bell → notifications).
- [ ] **Step 5: Build → SUCCESSFUL.** Run + verify Sign out returns to Login; opening notifications clears the badge.
- [ ] **Step 6: Commit** `git commit -am "feat(android): Profile + Notifications sheets"`

---

## Task 12: List states, app icon, polish

**Files:** Create `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`, `res/drawable/ic_launcher_foreground.*`, `res/values/ic_launcher_background.xml`; add `res/drawable/pentana_mark.png`; touch screens for empty/error parity.

- [ ] **Step 1: Add brand asset** — copy `~/Downloads/pentana-ios-android-ui-design 2/project/assets/pentana-mark.png` → `androidApp/src/main/res/drawable/pentana_mark.png`; use it in `LoginScreen` (76dp rounded) and the Home hero faint watermark.
- [ ] **Step 2: Adaptive icon** — foreground = the mark, background = orange `#F7931E` (per `brand.jsx → BrandIcon`); add the `mipmap-anydpi-v26` XML referencing them; add a monochrome layer for Android 13+ themed icons.
- [ ] **Step 3: Verify** each tab's loading/empty/error renders via `LoadingState`/`EmptyState`/`ErrorState` (match `states.jsx`) — fix any screen missing a branch.
- [ ] **Step 4: Build → SUCCESSFUL.** Run + visually confirm launcher icon + states.
- [ ] **Step 5: Commit** `git commit -am "feat(android): app icon, brand asset, list-state polish"`

---

## Task 13: Final verification, docs, push

- [ ] **Step 1: Full build + tests**

Run: `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest :shared:allTests`
Expected: all `BUILD SUCCESSFUL` / tests pass.

- [ ] **Step 2: README + status** — add an "Android" section to `README.md` (how to run: set `AppConfig.BASE_URL`, start the API, `:androidApp:installDebug`); note passkeys/push deferred. Update any status table.

- [ ] **Step 3: Commit docs** `git commit -am "docs: Android app run instructions + status"`

- [ ] **Step 4: Push**
```bash
git push origin main
```

---

## Self-review notes
- **Spec coverage:** scaffold(T1) · theme/dynamic-color(T2) · seams/TokenStore(T3) · components incl. chrome/chips/FAB/photo(T4) · logic+tests(T5) · session/shell/login(T6) · Home+celebratory(T7) · Bills+proof(T8) · Lunch(T9) · Activities+dynamic form(T10) · Profile+Notifications+badge(T11) · states+icon(T12) · verify/docs/push(T13). All §5–§10 screens/components covered.
- **Deferred** (passkeys, FCM push, Coil) intentionally have no task.
- **Types** are taken verbatim from `:shared` (UserDto, DashboardDto, BillDto, LunchDto, ActivityDto, QuestionDto, NotificationDto) and repo signatures in the spec — no invented members.
