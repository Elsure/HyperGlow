// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 uses built-in Kotlin (its bundled compiler is 2.2.x). miuix-ui 0.9.3 is compiled with
// Kotlin 2.4.0, whose metadata a 2.2 compiler cannot read. Putting a newer Kotlin Gradle Plugin on
// the buildscript classpath overrides the built-in compiler version — this is the supported route;
// applying org.jetbrains.kotlin.android instead does NOT work with AGP 9 (it casts the AGP
// extension to the removed BaseExtension type). Keep this version in sync with `kotlin` in
// gradle/libs.versions.toml.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
