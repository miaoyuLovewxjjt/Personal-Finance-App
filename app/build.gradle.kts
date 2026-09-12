import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/* ---------------- 签名配置（可移植） ----------------
 * 签名口令不入库：依次从 keystore.properties（本地、已 gitignore）、
 * gradle 属性（~/.gradle/gradle.properties 或 -P）、环境变量读取。
 * 找不到 keystore 或口令时不再中断构建：
 *   debug   → 回退系统 debug 签名（任何环境克隆后可直接 installDebug）
 *   release → 产出未签名包（app-release-unsigned.apk）；补上 keystore 即为正式签名包
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey)
        ?: (findProperty(propKey) as String?)
        ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val projectKeystore = rootProject.file(
    signingValue("keystorePath", "PIXELBOOK_KEYSTORE_PATH") ?: "keystore/pixelbook.keystore"
)
val storePass = signingValue("pixelbookStorePassword", "PIXELBOOK_STORE_PASSWORD")
val keyPass = signingValue("pixelbookKeyPassword", "PIXELBOOK_KEY_PASSWORD")
val keyAliasName = signingValue("pixelbookKeyAlias", "PIXELBOOK_KEY_ALIAS") ?: "pixelbook"
val canSignRelease = projectKeystore.exists() && storePass != null && keyPass != null

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
        if (canSignRelease) {
            create("release") {
                storeFile = projectKeystore
                storePassword = storePass
                keyAlias = keyAliasName
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        // 有正式 keystore 时 debug 也用它签名：debug/release 可互相覆盖安装，不出现签名不一致；
        // 没有时沿用 AGP 默认的 debug 签名（自动生成 ~/.android/debug.keystore）
        debug {
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }
}

if (!canSignRelease) {
    logger.lifecycle(
        "[PixelBook] 未找到 keystore/pixelbook.keystore 或签名口令：" +
            "debug 使用系统 debug 签名，release 产出未签名包。" +
            "需正式签名时，复制 keystore.properties.example 为 keystore.properties 并填写。"
    )
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

    testImplementation(libs.junit)
}
