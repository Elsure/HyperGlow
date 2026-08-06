plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.elsure.hyperglow"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.elsure.hyperglow"
        minSdk = 35
        targetSdk = 36
        versionCode = 4
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI signing: set KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD env vars + place release.keystore at project root.
    val ciSign = System.getenv("KEYSTORE_PASSWORD") != null
    if (ciSign) {
        signingConfigs {
            create("release") {
                storeFile = file("../release.keystore")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (ciSign) signingConfig = signingConfigs.getByName("release")
            // R8 on. The Xposed entry class is loaded BY NAME from assets/xposed_init, so the keep
            // rules in src/main/keepRules/rules.keep are what stop it being renamed or stripped —
            // if that ever breaks, the module installs fine but silently never hooks anything.
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Xposed API: PROVIDED by the LSPosed runtime; compile-only and NOT packaged into the APK.
    // Drop XposedBridgeApi-82.jar into app/libs/ (see app/libs/README.txt).
    compileOnly(fileTree("libs") { include("*.jar") })

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.miuix)
    implementation(libs.miuix.preference)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}