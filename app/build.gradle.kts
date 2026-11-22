plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // BEGIN(Firebase:Crashlytics)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    // END(Firebase:Crashlytics)
}

android {
    namespace = "fr.cyring.battman"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.cyring.battman"
        minSdk = 26
        targetSdk = 37
        versionCode = 22
        versionName = "0.22"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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

    @Suppress("UnstableApiUsage")
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // ML Kit GenAI capabilities
    implementation(libs.genai.prompt)
    implementation(libs.genai.summarization)
    // com.google.mlkit.genai.imagedescription
    implementation(libs.mlkit.genai.image.description)
    // BEGIN(Firebase:Crashlytics)
    implementation(platform(libs.firebase.bom))
    implementation(libs.google.firebase.crashlytics)
    implementation(libs.google.firebase.analytics)
    // END(Firebase:Crashlytics)
}