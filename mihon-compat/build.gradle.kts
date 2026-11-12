import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

android {
    namespace = "eu.kanade.tachiyomi.compat"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaTarget.target)
        targetCompatibility = JavaVersion.toVersion(javaTarget.target)
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        jvmTarget.set(javaTarget)
        jvmDefault.set(JvmDefaultMode.ENABLE)
        optIn.addAll(
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.ax.okhttp.mockwebserver)

    // Kept public: the app and the extensions link against these types.
    api(libs.ax.okhttp)
    // OkHttp 5 keeps the compression algorithms in separate artifacts.
    api(libs.ax.okhttp.brotli)
    api(libs.ax.okhttp.zstd)
    // DNS over HTTPS, the same option Mihon offers for sites an ISP blocks by DNS.
    api(libs.ax.okhttp.dnsoverhttps)
    api(libs.jsoup)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.json.okio)
    api(libs.kotlinx.serialization.protobuf)
    api(libs.rxjava)
    api(libs.injekt)
    api(libs.preference.ktx)
    api(libs.ax.coroutines.android)

    implementation(libs.ax.core.ktx)
    implementation(libs.webkit)
    implementation(libs.quickjs)

    implementation(platform(libs.ax.compose.bom))
    implementation("androidx.compose.runtime:runtime")
}
