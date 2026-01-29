plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.cameraxproject"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cameraxproject"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // --- MediaPipe ---
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // --- CameraX ---
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // --- Android ---
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // --- Splash ---
    implementation("androidx.core:core-splashscreen:1.0.1")

    // --- Recycler ---
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // --- ViewPager2 ---
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // ✅ DOT INDICATOR (BEST)
    implementation("com.tbuonomo:dotsindicator:5.0")

    //---Tensor Flow
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    implementation ("com.google.android.material:material:1.11.0")


}