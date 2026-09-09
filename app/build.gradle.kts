plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.miaoyu03.pixelbook"
    buildFeatures {
        buildConfig = true
    }
    compileSdk = 35

    defaultConfig {
        applicationId = "com.miaoyu03.pixelbook"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk 24 < API 26：java.time 等需要 core library desugaring，否则 Android 7.x 崩溃
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/pixelbook.keystore")
            // 密码优先取用户级 gradle.properties（~/.gradle/gradle.properties 的 pixelbookStorePassword），
            // 缺省回退默认值（旧配置兼容；不随仓库公开）
            storePassword = (findProperty("pixelbookStorePassword") as String?) ?: "pixelbook123"
            keyAlias = "pixelbook"
            keyPassword = (findProperty("pixelbookKeyPassword") as String?) ?: "pixelbook123"
        }
    }

    buildTypes {
        // debug 也使用项目正式 keystore 签名：debug/release 可互相覆盖安装，不出现签名不一致
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.documentfile)
}
