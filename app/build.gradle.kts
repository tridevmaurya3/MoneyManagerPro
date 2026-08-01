plugins {
    alias(libs.plugins.androidApplication)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.moneymanagerpro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.moneymanagerpro"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_1_8

        targetCompatibility =
            JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(
        libs.appcompat
    )

    implementation(
        libs.material
    )

    implementation(
        libs.activity
    )

    implementation(
        libs.constraintlayout
    )

    implementation(
        "androidx.drawerlayout:drawerlayout:1.2.0"
    )

    /*
     * Firebase libraries के compatible versions manage करता है।
     *
     * firebase-auth और firebase-firestore के सामने अलग-अलग
     * versions नहीं लगाने हैं।
     */
    implementation(
        platform(
            "com.google.firebase:firebase-bom:34.16.0"
        )
    )

    /*
     * Firebase Login, Signup और Account Management।
     */
    implementation(
        "com.google.firebase:firebase-auth"
    )

    /*
     * Encrypted Cloud Backup metadata और encrypted chunks।
     */
    implementation(
        "com.google.firebase:firebase-firestore"
    )

    /*
     * Firestore/Firebase runtime में उपयोग होने वाली
     * AndroidX Preferences DataStore classes।
     *
     * यह dependency निम्न crash को ठीक करती है:
     *
     * NoClassDefFoundError:
     * androidx.datastore.preferences.PreferenceDataStoreDelegateKt
     */
    implementation(
        "androidx.datastore:datastore-preferences:1.2.1"
    )

    /*
     * Permission-free UPI QR Scanner।
     */
    implementation(
        "com.google.android.gms:play-services-code-scanner:16.1.0"
    )

    /*
     * Room Local Database।
     */
    implementation(
        libs.room.runtime
    )

    annotationProcessor(
        libs.room.compiler
    )

    /*
     * Bill Reminder और Automatic Backup Workers।
     */
    implementation(
        libs.work.runtime
    )

    /*
     * WorkManager की ListenableFuture class के लिए पूरा
     * Android-compatible Guava package।
     */
    implementation(
        "com.google.guava:guava:33.6.0-android"
    )

    /*
     * पुराने standalone ListenableFuture artifact के साथ
     * duplicate-class conflict रोकता है।
     *
     * वास्तविक ListenableFuture class ऊपर वाले Guava Android
     * package से मिलेगी।
     */
    implementation(
        "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava"
    )

    /*
     * PIN और Biometric Protection।
     */
    implementation(
        "androidx.biometric:biometric:1.1.0"
    )

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.ext.junit
    )

    androidTestImplementation(
        libs.espresso.core
    )
}