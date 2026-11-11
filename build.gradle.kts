plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("clean") {
    delete(rootProject.layout.buildDirectory)
}
