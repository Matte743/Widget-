import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The Glyph Matrix SDK licence does not allow redistribution, so the AAR is not
// committed. It is fetched from Nothing's official repository on first build.
val glyphSdkUrl =
    "https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/main/glyph-matrix-sdk-2.0.aar"
val glyphSdk = file("libs/glyph-matrix-sdk-2.0.aar")
if (!glyphSdk.exists()) {
    logger.lifecycle("Downloading Glyph Matrix SDK from $glyphSdkUrl")
    glyphSdk.parentFile.mkdirs()
    URI(glyphSdkUrl).toURL().openStream().use { input ->
        glyphSdk.outputStream().use { output -> input.copyTo(output) }
    }
}

android {
    namespace = "com.matte743.nothingqs"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.matte743.nothingqs"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // A fixed key so every build can be installed over the previous one.
        create("shared") {
            storeFile = file("widget.keystore")
            storePassword = "android"
            keyAlias = "widget"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
