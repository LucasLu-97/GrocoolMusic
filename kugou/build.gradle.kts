plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

/**
 * 本地诊断入口：用真实编译产物打酷狗网关，验证 Kotlin 实现的签名与解析。
 * 不参与 `check` / CI（CI runner 位于境外，会触发酷狗人机验证）。
 * 用法：`gradle :kugou:gatewayProbe`（需要国内网络）。
 */
tasks.register<JavaExec>("gatewayProbe") {
    group = "verification"
    description = "Probe the real Kugou gateway with the compiled Kotlin implementation"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.grocool.kugou.GatewayProbeKt")
}
