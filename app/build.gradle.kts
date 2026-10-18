import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

android {
    namespace = "com.kodastream.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kodastream.app"
        // 26 keeps Jackson 2.13.1 (required by the extension API) safe and covers
        // practically every device in use. NewPipe (inside the engine) needs the
        // desugaring enabled below on anything older than API 33.
        minSdk = 26
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // Extensions (.cs3 files) are linked against the engine's class and
            // method names at runtime. R8 would rename or strip them, so shrinking
            // stays OFF (CloudStream itself ships the same way).
            isMinifyEnabled = false
            isShrinkResources = false
            // First builds are signed with the debug key so the APK installs
            // straight away. Swap in a real keystore before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(javaTarget.target)
        targetCompatibility = JavaVersion.toVersion(javaTarget.target)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Same form CloudStream's own app module uses for these settings.
tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        jvmTarget.set(javaTarget)
        jvmDefault.set(JvmDefaultMode.ENABLE)
        optIn.addAll(
            // Material 3 Expressive (same opt-ins Koda uses)
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            // Engine APIs that the plugin loader has to touch
            "com.lagradost.cloudstream3.InternalAPI",
            "com.lagradost.cloudstream3.Prerelease",
            // Media3 marks most of its player classes as unstable
            "androidx.media3.common.util.UnstableApi",
        )
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio) // NIO flavour needed by NewPipeExtractor

    // ---- The CloudStream engine (MainAPI, extractors, networking) ----
    implementation(project(":library"))

    // The engine declares these as `implementation`, so they are on the runtime
    // classpath but NOT visible at compile time. This module (and the extensions
    // it loads) needs them, so they are repeated here, same as CloudStream's app.
    implementation(libs.nicehttp)
    // CloudStream's TLS provider: some providers fail against Google/CDN
    // endpoints without it. Pinned, same as the original app.
    implementation(libs.conscrypt.android)
    implementation(libs.jsoup)
    implementation(libs.ksoup)
    implementation(libs.rhino)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.newpipeextractor)
    implementation("com.google.code.gson:gson:2.11.0") // used by many extensions
    implementation("me.xdrop:fuzzywuzzy:1.4.0") // used by many extensions
    implementation(libs.ax.okhttp)
    implementation(libs.ax.coroutines.android)

    // ---- Koda's UI stack: Compose + Material 3 Expressive ----
    implementation(libs.ax.core.ktx)
    implementation(libs.ax.documentfile) // SAF DocumentFile for the download location
    implementation(libs.appcompat) // MainActivity is an AppCompatActivity, like CloudStream's
    implementation(libs.ax.lifecycle.runtime.ktx)
    implementation(libs.ax.lifecycle.viewmodel.compose)
    implementation(libs.ax.activity.compose)
    implementation(platform(libs.ax.compose.bom))
    implementation(libs.ax.compose.ui)
    implementation(libs.ax.compose.ui.graphics)
    implementation(libs.ax.compose.material3)
    implementation(libs.ax.compose.icons.extended)
    implementation(libs.ax.compose.ui.text.google.fonts)
    implementation(libs.ax.navigation.compose)
    // HCT colour generation used by Koda's palette system
    implementation(libs.ax.material.colors)
    implementation(libs.ax.coil.compose)

    // ---- Player ----
    implementation(libs.ax.media3.exoplayer)
    // The original CloudStream player's codec/renderers layer (software
    // codecs, NextRenderersFactory, preview timebar).
    implementation(libs.nextlib.media3ext)
    implementation(libs.ax.media3.exoplayer.dash)
    implementation(libs.ax.media3.exoplayer.hls)
    implementation(libs.ax.media3.ui)
    implementation(libs.ax.media3.datasource.okhttp)
}
