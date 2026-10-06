plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

abstract class GitCommitCountSource : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        return try {
            val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim().toInt()
        } catch (_: Exception) { 1 }
    }
}

android {
    namespace = "com.mymusicplayer"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.mymusicplayer.musemeta"
        minSdk = 29
        targetSdk = 35
        val commitCount = providers.of(GitCommitCountSource::class) {}.get()
        versionCode = commitCount
        versionName = "1.0.${commitCount}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        multiDexKeepProguard = file("multidex-keep.pro")
    }

    signingConfigs {
        // Explicit signing for CI, driven by environment variables. AGP's implicit
        // debug-keystore lookup is environment dependent: on GitHub runners the
        // store path reported by signingReport was correct and the keystore file
        // was present and unmodified, yet the APK still came out signed by a
        // different key. Never depend on that lookup for a published artifact.
        create("releaseFromEnv") {
            val storePath = providers.environmentVariable("RELEASE_STORE_FILE").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Local sideloading keeps using the debug keystore. CI sets
            // RELEASE_STORE_FILE and signs with the keystore it supplies, so the
            // published artifact is reproducible and not at the mercy of wherever
            // AGP happens to look for debug.keystore.
            signingConfig = if (providers.environmentVariable("RELEASE_STORE_FILE").isPresent) {
                signingConfigs.getByName("releaseFromEnv")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isDebuggable = true
        }
    }

    lint {
        // Surface issues during build but don't hard-fail CI; review output before publish.
        abortOnError = false
        checkReleaseBuilds = true
        // jAudiotagger pulls in AWT/Swing stubs; harmless in an Android app.
        disable += setOf("InvalidPackage")
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("compile") && it.name.contains("JavaWithJavac") }.configureEach {
        enabled = false
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // Multidex
    implementation(libs.multidex)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Media3
implementation(libs.media3.exoplayer)
implementation(libs.media3.session)
implementation(libs.media3.ui)
implementation(libs.androidx.media)

    // Image loading
    implementation(libs.coil.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // jAudiotagger - audio metadata read/write
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)

    // Android test deps (for Room in-memory tests)
    androidTestImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.junit.platform.launcher)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
