plugins {
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

