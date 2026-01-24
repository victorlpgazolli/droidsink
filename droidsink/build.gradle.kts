import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.androidApplication)
}


group = "me.user"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinxSerializationJson)
        }
        androidMain.dependencies {
            implementation(libs.kotlin.stdlib)
        }
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
                if (hostOs == "Mac OS X") {
                    freeCompilerArgs += listOf(
                        "-linker-options", "-macosx_version_min 15.0",
                        "-linker-options", "-framework IOKit",
                        "-linker-options", "-framework CoreFoundation",
                        "-linker-options", "-framework Security"
                    )
                }
            }
        }
        compilations.getByName("main") {
            val libusb by cinterops.creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/libusb.def"))
                if (hostOs == "Mac OS X") {
                    includeDirs("/opt/homebrew/include")
                }
            }
        }
    }

}


android {
    namespace = "dev.victorlpgazolli.mobilesink"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.victorlpgazolli.mobilesink"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
