plugins {
    id("com.android.application")
}

val releaseKeystorePath = providers.environmentVariable("LAN_WIRELESS_KEYBOARD_KEYSTORE").orNull
val releaseKeystorePassword = providers.environmentVariable("LAN_WIRELESS_KEYBOARD_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("LAN_WIRELESS_KEYBOARD_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LAN_WIRELESS_KEYBOARD_KEY_PASSWORD").orNull
val releaseSigningValues = linkedMapOf(
    "LAN_WIRELESS_KEYBOARD_KEYSTORE" to releaseKeystorePath,
    "LAN_WIRELESS_KEYBOARD_KEYSTORE_PASSWORD" to releaseKeystorePassword,
    "LAN_WIRELESS_KEYBOARD_KEY_ALIAS" to releaseKeyAlias,
    "LAN_WIRELESS_KEYBOARD_KEY_PASSWORD" to releaseKeyPassword,
)
val configuredReleaseSigningValues = releaseSigningValues.count { !it.value.isNullOrBlank() }
require(configuredReleaseSigningValues == 0 || configuredReleaseSigningValues == releaseSigningValues.size) {
    val missing = releaseSigningValues.filterValues { it.isNullOrBlank() }.keys.joinToString()
    "Release signing configuration is incomplete. Missing environment variables: $missing"
}
val hasReleaseSigning = configuredReleaseSigningValues == releaseSigningValues.size
val releaseArtifactTasks = setOf(
    "assembleRelease",
    "bundleRelease",
    "packageRelease",
    "packageReleaseBundle",
    "signReleaseBundle",
)
gradle.taskGraph.whenReady {
    val releaseArtifactScheduled = allTasks.any { task ->
        task.project == project && task.name in releaseArtifactTasks
    }
    require(!releaseArtifactScheduled || hasReleaseSigning) {
        "Release builds require all LAN_WIRELESS_KEYBOARD release signing environment variables. " +
            "Use assembleDebug for an unsigned local build."
    }
}

android {
    namespace = "com.local.virtualkeyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.local.virtualkeyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseSigningConfig = if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = file(requireNotNull(releaseKeystorePath))
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = releaseSigningConfig
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += "AndroidGradlePluginVersion"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
