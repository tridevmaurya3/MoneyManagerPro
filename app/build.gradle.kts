import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.androidApplication)
    id("com.google.gms.google-services")
}

val expectedReleaseCertificateSha256 =
    "4e1d6e232a2a0d32274e312a1f15f560b728d7cabab6db4050a66e995c976eac"

fun certificateSha256(
    storeFile: java.io.File,
    storePassword: String,
    keyAlias: String
): String {
    var lastError: Exception? = null

    for (storeType in listOf("JKS", "PKCS12")) {
        try {
            val keyStore = KeyStore.getInstance(storeType)
            FileInputStream(storeFile).use { input ->
                keyStore.load(input, storePassword.toCharArray())
            }

            val certificate = keyStore.getCertificate(keyAlias)
                ?: throw GradleException(
                    "Signing alias '$keyAlias' was not found in ${storeFile.absolutePath}"
                )

            return MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
        } catch (error: Exception) {
            lastError = error
        }
    }

    throw GradleException(
        "Unable to read the configured MoneyManagerPro signing keystore: " +
            storeFile.absolutePath,
        lastError
    )
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

    val configuredCertificateSha256 = certificateSha256(
        configuredStoreFile,
        keystoreProperties.getProperty("storePassword"),
        keystoreProperties.getProperty("keyAlias")
    )

    if (!configuredCertificateSha256.equals(
            expectedReleaseCertificateSha256,
            ignoreCase = true
        )
    ) {
        throw GradleException(
            "WRONG MoneyManagerPro signing certificate. " +
                "Expected SHA-256: $expectedReleaseCertificateSha256, " +
                "but configured keystore is: $configuredCertificateSha256. " +
                "Use the ORIGINAL Android debug.keystore that signed the existing installed app."
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
                "the ORIGINAL Money Manager Pro signing keystore. " +
                "For the currently installed app this is the Android debug.keystore " +
                "with SHA-256 $expectedReleaseCertificateSha256."
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
