import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())

// Firebase reads its project from google-services.json. The file is not in git. Without it,
// the app builds with no crash reports and no events. See docs/release.md.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
}

android {
    namespace = "com.jktdeveloper.habitto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jktdeveloper.habitto"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("supabase.anon_key", "")}\"")
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${localProps.getProperty("google.web_client_id", "")}\"",
        )
    }

    // The upload key and its passwords are in local.properties, not in git. See docs/release.md.
    val uploadStoreFile = localProps.getProperty("release.store_file")
    signingConfigs {
        if (uploadStoreFile != null) {
            create("release") {
                storeFile = file(uploadStoreFile)
                storePassword = localProps.getProperty("release.store_password")
                keyAlias = localProps.getProperty("release.key_alias")
                keyPassword = localProps.getProperty("release.key_password")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays off for the closed test. supabase-kt, Ktor and kotlinx.serialization need
            // keep rules first, and a stack trace without obfuscation needs no mapping file.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":mobile:shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.compose.activity)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.sqldelight.android.driver)
}
