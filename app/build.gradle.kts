import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// --- Signing -----------------------------------------------------------------------------
// The keystore is the owner's, is never generated here, and is never committed (see
// .gitignore). Values come from `keystore.properties` at the repo root, or from
// LUMEN_KEYSTORE_* environment variables, in that order. Release assembly fails outright
// when neither is present: a "release" quietly signed with the debug key could never be
// updated in place later, so it is worse than no build at all.
//
// `-Plumen.unsignedRelease=true` is the one escape hatch — it produces an *unsigned* APK
// for verifying the R8-shrunk build on an emulator before a keystore exists. It cannot
// be installed on a phone as-is, which is the point.
val keystoreProperties: Properties? =
    rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }

fun signingValue(property: String, env: String): String? =
    keystoreProperties?.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "LUMEN_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "LUMEN_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "LUMEN_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "LUMEN_KEY_PASSWORD")
val releaseSigningConfigured =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { it != null }
val unsignedReleaseAllowed = providers.gradleProperty("lumen.unsignedRelease")
    .map { it.toBoolean() }.getOrElse(false)

android {
    namespace = "com.sidharth.lumen"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.sidharth.lumen"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        // versionCode: MAJOR*10000 + MINOR*100 + PATCH. versionName mirrors it.
        versionCode = 10502
        versionName = "1.5.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Null when unconfigured — and then `verifyReleaseSigning` below refuses to
            // let the build continue, so this never silently produces a debug-keyed APK.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // Required by Readium's AARs — the build refuses to package them without it.
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        // MigrationTestHelper reads the exported v1 schema JSON from test assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Refuses to build a release without signing material, unless explicitly told the APK is
// for emulator verification only. Registered as a task so the message is the first thing
// a failed `assembleRelease` prints, not something buried after a four-minute R8 pass.
val verifyReleaseSigning by tasks.registering {
    val configured = releaseSigningConfigured
    val unsignedOk = unsignedReleaseAllowed
    doFirst {
        if (!configured && !unsignedOk) {
            throw GradleException(
                """
                |
                |  Release signing is not configured, so this build stops here.
                |
                |  Lumen never signs a release with the debug key. Either:
                |    1. create keystore.properties at the repo root (see README "Signing"),
                |    2. or export LUMEN_KEYSTORE_FILE / LUMEN_KEYSTORE_PASSWORD /
                |       LUMEN_KEY_ALIAS / LUMEN_KEY_PASSWORD,
                |    3. or, ONLY to test the shrunk build on an emulator, pass
                |       -Plumen.unsignedRelease=true to get an uninstallable unsigned APK.
                |
                """.trimMargin(),
            )
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

ksp {
    // Schemas are committed. Highlights are the durable asset (§4), and a migration test
    // needs the old schema on disk to migrate *from*.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)

    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.navigator.media.tts)
    implementation(libs.media3.session)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
