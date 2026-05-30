import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val baseUrl: String = localProps.getProperty("BASE_URL", "http://10.0.2.2:8080/")
val amapKey: String = localProps.getProperty("AMAP_KEY", "")
val iflytekAppId: String = localProps.getProperty("IFLYTEK_APPID", "")

// 发布签名配置：从 client/keystore.properties 读（该文件 .gitignore，绝不进仓库）。
// 缺省时 release 构建沿用 debug 签名，不阻断本地构建；正式发布前必须配置真签名。
//
// 一次性生成密钥（在 client/ 目录执行）：
//   keytool -genkeypair -v \
//     -keystore keystore/release.jks \
//     -keyalg RSA -keysize 2048 -validity 10000 \
//     -alias guiderun
// 然后按 keystore.properties.example 模板创建 client/keystore.properties。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps: Properties? = if (keystorePropsFile.exists()) {
    Properties().apply { load(keystorePropsFile.inputStream()) }
} else null

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

detekt {
    // 基于内置默认规则集 + 项目 yml 覆盖；只扫主源码，跳过构建产物 / generated 代码
    buildUponDefaultConfig = true
    // CI 默认关闭 autoCorrect 防止误改；本地需要批量修复时临时改 true 跑一次再复位
    autoCorrect = false
    source.setFrom("src/main/java")
    config.setFrom("$projectDir/detekt-config.yml")
    // 不设 baseline：首次跑出全部问题供清理；如要锁住已有问题再生成 baseline
}

android {
    namespace = "com.guiderun.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guiderun.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "AMAP_KEY", "\"$amapKey\"")
        buildConfigField("String", "IFLYTEK_APPID", "\"$iflytekAppId\"")
        manifestPlaceholders["AMAP_KEY"] = amapKey

        ndk {
            // 讯飞 MSC SDK 仅提供 arm64-v8a / armeabi-v7a 两个 ABI，限制打包避免 x86 模拟器加载失败
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    signingConfigs {
        // 仅当 keystore.properties 存在时注册 release 签名；缺省时 release 用 debug 签名兜底
        keystoreProps?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // V1/V2/V3 全启用（兼容 API 26→34）
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 全量优化 + 混淆 + 死代码剔除
            isMinifyEnabled = true
            // 同步压缩未使用资源（依赖 isMinifyEnabled = true 才生效）
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 配置存在则用真签名；否则 AGP 默认 fallback 到 debug 签名，构建仍可通过（但无法上架）
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
        debug {
            // debug 关闭混淆方便调试 + 加快构建
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Location
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    // Navigation Fragment (XML-based screens for BlindActivity)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.material)
    implementation(libs.swiperefreshlayout)

    // Amap 3D Map SDK
    implementation(libs.amap.map3d)

    // 讯飞 MSC SDK (语音听写 IAT + 命令词识别 ASR)
    implementation(files("libs/Msc.jar"))

    // Logging
    implementation(libs.timber)

    // Detekt formatting 子规则（ktlint 包装），用于补 UnusedImports 等格式化检查
    detektPlugins(libs.detekt.formatting)

    // TRTC (voice call) - disabled for now due to Maven repo TLS issues
    // TODO: uncomment when repo is accessible
    // implementation(libs.trtc)

    // Test
    testImplementation(libs.junit)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
