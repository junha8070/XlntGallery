plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)   // Room이 컴파일할 때 DB 코드를 자동 생성
}

android {
    namespace = "com.xlntsmmr.xlntgallery"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.xlntsmmr.xlntgallery"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation(libs.mlkit.text.recognition.korean)   // OCR (한국어+영어, 기기 내장 모델)
    implementation(libs.androidx.exifinterface)          // 사진 EXIF(위치·카메라) 읽기
    implementation(libs.onnxruntime.android)             // PaddleOCR 모델 실행 (ONNX Runtime)
    implementation(libs.androidx.datastore.preferences)  // 설정 저장
    implementation(libs.androidx.room.runtime)           // DB (SQLite를 편하게)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)           // 백그라운드 사진 분석
    implementation(libs.mlkit.genai.prompt)              // Gemini Nano (온디바이스 생성형 AI)
}