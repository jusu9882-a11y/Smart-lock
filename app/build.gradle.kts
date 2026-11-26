plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.vanta.smartlock"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.vanta.smartlock"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

val handLandmarkerModel = layout.projectDirectory.file("src/main/assets/hand_landmarker.task").asFile
val handLandmarkerSha256 = "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"

val downloadHandLandmarkerModel by tasks.registering {
    outputs.file(handLandmarkerModel)
    inputs.property("modelSha256", handLandmarkerSha256)
    doLast {
        fun sha256(file: java.io.File): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        if (handLandmarkerModel.exists() && sha256(handLandmarkerModel) == handLandmarkerSha256) {
            return@doLast
        }

        handLandmarkerModel.parentFile.mkdirs()
        val sources = listOf(
            "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task",
            "https://github.com/sanderdesnaijer/mediapipe-model-mirrors/releases/download/v1/hand_landmarker.task"
        )
        var downloaded = false
        var lastFailure: Exception? = null
        for (source in sources) {
            try {
                val connection = java.net.URL(source).openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 120_000
                }
                connection.getInputStream().use { input ->
                    handLandmarkerModel.outputStream().use { output -> input.copyTo(output) }
                }
                if (sha256(handLandmarkerModel) == handLandmarkerSha256) {
                    downloaded = true
                    break
                }
                handLandmarkerModel.delete()
                throw org.gradle.api.GradleException("Hand Landmarker model checksum mismatch from $source")
            } catch (error: Exception) {
                lastFailure = error
                handLandmarkerModel.delete()
            }
        }
        check(downloaded) {
            "Could not download the pinned offline hand model. Last error: ${lastFailure?.message ?: "unknown error"}"
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadHandLandmarkerModel)
}
