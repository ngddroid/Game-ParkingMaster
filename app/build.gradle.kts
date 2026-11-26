plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ngstudio.inchperfect"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ngstudio.inchperfect"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Debug: locked levels can be unlocked through the simulated rewarded-ad dialog.
            buildConfigField("boolean", "ADS_SIMULATED", "true")
            buildConfigField("boolean", "DEBUG_TOOLS", "true")
        }
        release {
            buildConfigField("boolean", "ADS_SIMULATED", "false")
            buildConfigField("boolean", "DEBUG_TOOLS", "false")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            // Local testing only. Replace with your Play upload key before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all {
            it.maxHeapSize = "3g"
            it.systemProperty("levels", (project.findProperty("levels") ?: "").toString())
            it.systemProperty("preview", (project.findProperty("preview") ?: "").toString())
            it.systemProperty("java.awt.headless", "true")
            it.testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
