import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
}

// 倒计时的协程若没跑到归零就结束测试体，runTest 收尾排空队列会静默挂死测试任务；
// 这里给它一个上限，让挂死表现为失败而不是无限等待
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(5))
}

android {
    namespace = "com.github.lany192.gomoku"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.github.lany192.gomoku"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("test.jks")
            storePassword = "dev123456"
            keyAlias = "test"
            keyPassword = "dev123456"
        }
    }

    buildTypes {
        debug {
            // 与 release 共用同一签名，避免换签后覆盖安装失败
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}