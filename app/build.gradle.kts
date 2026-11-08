import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val amapWebKey = (localProperties.getProperty("amap.web.key") ?: "").trim()

android {
    namespace = "com.gpsproxy.dev"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gpsproxy.dev"
        minSdk = 27
        // NewBlackbox 引擎要求宿主 targetSdk ≤28 以保证容器兼容性
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "AMAP_WEB_KEY", "\"$amapWebKey\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // BlackBox 虚拟化容器要求宿主 targetSdk ≤28，这是产品约束而非疏漏。
        disable += "ExpiredTargetSdkVersion"
        // 容器内核只支持 arm 设备，x86_64/ChromeOS 不在支持范围。
        disable += "ChromeOsAbiSupport"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":Bcore"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // JVM 单测：不依赖设备，纯逻辑与 Fake 覆盖核心推导/状态机/编解码。
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // Android 自带 org.json 在单测里是空实现，测试时用真实实现替换。
    testImplementation("org.json:json:20240303")
    // DataStore 的纯 JVM 核心，用于测试设置映射而不启动 Android。
    testImplementation("androidx.datastore:datastore-preferences-core:1.2.1")
}
