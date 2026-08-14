import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.androidApplication)
    id("com.google.gms.google-services")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore = keystorePropertiesFile.exists()

if (hasReleaseKeystore) {
    FileInputStream(keystorePropertiesFile).use {
        keystoreProperties.load(it)
    }

    val requiredSigningKeys = listOf(
        "storeFile",
        "storePassword",
        "keyAlias",
        "keyPassword"
    )

    val missingSigningKeys = requiredSigningKeys.filter {
        keystoreProperties.getProperty(it).isNullOrBlank()
    }

    if (missingSigningKeys.isNotEmpty()) {
        throw GradleException(
            "Invalid keystore.properties. Missing: ${missingSigningKeys.joinToString()}"
        )
    }

    val configuredStoreFile = rootProject.file(
        keystoreProperties.getProperty("storeFile")
    )

    if (!configuredStoreFile.exists()) {
        throw GradleException(
            "Release keystore not found: ${configuredStoreFile.absolutePath}"
        )
    }
}

android {
    namespace = "com.example.moneymanagerpro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.moneymanagerpro"
        minSdk = 24
        targetSdk = 34
        versionCode = 28
        versionName = "3.7"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(
                    keystoreProperties.getProperty("storeFile")
                )
                storePassword =
                    keystoreProperties.getProperty("storePassword")
                keyAlias =
                    keystoreProperties.getProperty("keyAlias")
                keyPassword =
                    keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug APK must never collide with the installed production app.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = false

            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }

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

// A production update must always be signed with the same private release key.
// This blocks accidental unsigned/differently-signed release artifacts from
// being produced by normal Gradle release tasks.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.project == project &&
            task.name.contains("release", ignoreCase = true) &&
            (
                task.name.startsWith("assemble", ignoreCase = true) ||
                    task.name.startsWith("bundle", ignoreCase = true) ||
                    task.name.startsWith("package", ignoreCase = true)
                )
    }

    if (releaseRequested && !hasReleaseKeystore) {
        throw GradleException(
            "MoneyManagerPro release signing is not configured. " +
                "Create keystore.properties in the project root and point it to " +
                "the ORIGINAL Money Manager Pro release .jks/.keystore. " +
                "Do not create a new key for an app update."
        )
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)

    implementation("com.google.guava:guava:33.6.0-android")
    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
    implementation("androidx.biometric:biometric:1.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
