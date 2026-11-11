// 统一发布签名密钥：路径由 CI 环境变量 RELEASE_KEYSTORE 注入，
// 私钥本体只存在于 GitHub Actions Secrets，绝不进入公开仓库。
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE")
    ?.takeIf { it.isNotBlank() && file(it).isFile }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
        release {
            // M0 骨架阶段不开混淆，避免干扰签名与产物排查。
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
}
