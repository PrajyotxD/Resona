plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Bridge depends on innertube module
    implementation(project(":innertube"))
    
    // Kotlin coroutines dependencies (isolated to bridge module only)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Ktor dependencies for exception handling
    implementation(libs.ktor.client.core)
    
    // OkHttp for stream URL validation
    implementation(libs.okhttp)
    
    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

// Commented out due to Gradle configuration issue
// tasks.withType<Test> {
//     useJUnitPlatform()
// }