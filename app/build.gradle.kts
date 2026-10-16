import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hemma.fixaudio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hemma.fixaudio"
        minSdk = 28
        // 33 not 34: avoids Android 14's foreground-service type rules for a sideloaded app
        targetSdk = 33
        versionCode = 3
        versionName = "1.2"
    }

    // release key stays local (gitignored); without it only debug builds work
    val keystore = rootProject.file("keystore.properties")
    if (keystore.exists()) {
        val props = Properties().apply { keystore.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("dev.mobile:dadb:2.0.0")
    testImplementation("junit:junit:4.13.2")
}
