plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePath = findProperty("PIXELIMS_STORE_FILE") as String?

android {
    namespace = "com.takaisaisei.pixelims"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.takaisaisei.pixelims"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0-beta1"

        // Force the Android 12/13 detached apply path.
        val forceDetached = (System.getenv("FORCE_DETACHED")
            ?: findProperty("FORCE_DETACHED") as String?)?.toBoolean() ?: false
        buildConfigField("boolean", "FORCE_DETACHED", forceDetached.toString())
    }

    signingConfigs {
        create("release") {
            storeFile = keystorePath?.let { file(it) }
            storePassword = findProperty("PIXELIMS_STORE_PASSWORD") as String?
            keyAlias = findProperty("PIXELIMS_KEY_ALIAS") as String?
            keyPassword = findProperty("PIXELIMS_KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        release {
            signingConfig =
                if (keystorePath != null) signingConfigs.getByName("release") else null
            optimization {
                enable = false
            }
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hiddenapibypass)
    implementation(libs.kadb)
}
