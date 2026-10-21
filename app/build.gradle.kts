import java.util.Properties

plugins {
    id("com.android.application")
}

val signingProps = Properties()
val signingPropsFile = rootProject.file("signing.properties")
if (signingPropsFile.exists()) signingProps.load(signingPropsFile.inputStream())

// Stable release signing is injected from encrypted Actions secrets or
// untracked local signing.properties. The key must never enter Git history.
fun releaseCredential(property: String, environment: String): String? =
    signingProps.getProperty(property) ?: System.getenv(environment)

android {
    namespace = "df.root"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "df.root"
        minSdk = 32
        targetSdk = 36
        versionCode = 414
        versionName = "4.14-rmp"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(releaseCredential("KEYSTORE_FILE", "RMP_KEYSTORE_FILE")
                ?: "../keystore.jks")
            storePassword = releaseCredential("KEYSTORE_PASSWORD", "RMP_KEYSTORE_PASSWORD")
            keyAlias = releaseCredential("KEY_ALIAS", "RMP_KEY_ALIAS")
            keyPassword = releaseCredential("KEY_PASSWORD", "RMP_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // Do not shrink original DFRoot code during first stable signing migration.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        checkReleaseBuilds = false
    }

    externalNativeBuild {
        cmake {
            path("src/main/jni/CMakeLists.txt")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(output.versionName.map { vn -> "RMP_${vn}.apk" })
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}
