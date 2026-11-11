// 统一发布签名密钥：路径由 CI 环境变量 RELEASE_KEYSTORE 注入，
// 私钥本体只存在于 GitHub Actions Secrets，绝不进入公开仓库。
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE")
    ?.takeIf { it.isNotBlank() && file(it).isFile }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.grocool.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.grocool.music"
        minSdk = 26
        targetSdk = 35
        // versionCode 由 CI 用 github.run_number 注入，保证单调递增、可覆盖安装。
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "0.1.0-dev"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // 本地 debug 包与统一签名的 release 包并存，避免调试包覆盖正式包。
            // 也让本机构建的 APK 能随时装到真机验证，不影响覆盖安装链路。
            applicationIdSuffix = ".debug"
            // 与 CI release 的 versionCode 区分，避免 ADB 安装时误判降级。
            versionNameSuffix = "-debug"
        }
        release {
            // M1 阶段仍不开混淆，避免干扰签名与产物排查。
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":kugou"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.android)
}
