plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.zero0space.dometour"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.zero0space.dometour"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = libs.versions.spatialBom.get()

        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters.add("arm64-v8a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions { jvmTarget = "11" }

    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.design)
    implementation(libs.spatial.sense)
    implementation(libs.spatial.tracking)
    // NOTE: the official sample also declares debugImplementation ui-tooling
    // and implementation ui-tooling-preview, both version-less (relying on
    // graph resolution). They are only needed for @Preview in Android Studio,
    // which this scaffold does not use, so they are deliberately omitted.
}

configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}
