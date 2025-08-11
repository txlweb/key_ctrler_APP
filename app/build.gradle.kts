plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.idlike.kctrl.mgr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.idlike.kctrl.mgr"
        minSdk = 29  // Android 10 (API 29)
        targetSdk = 35  // Android 15 (API 35)
        versionCode = 10020
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 启用矢量图支持
        vectorDrawables.useSupportLibrary = true
        
        // 资源优化配置
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        release {
            // 启用代码混淆和优化
            isMinifyEnabled = true
            // 启用资源压缩
            isShrinkResources = true
            // 启用代码裁剪
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 启用APK分割
            isCrunchPngs = true
            // 移除调试信息
            isDebuggable = false
            isJniDebuggable = false
            isRenderscriptDebuggable = false
        }
        debug {
            // Debug版本也启用轻量级优化
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = false
    }
    kotlinOptions {
        jvmTarget = "1.8"
        // Kotlin编译优化
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all",
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions"
        )
    }
    
    // 打包优化配置
    packagingOptions {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
                "**/*.kotlin_metadata",
                "**/*.version",
                "**/*.properties",
                "**/kotlin/**",
                "META-INF/com.android.tools/**",
                "META-INF/proguard/**",
                "META-INF/maven/**",
                "reference.conf"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
    
    // 构建优化
    buildFeatures {
        buildConfig = false
        viewBinding = false
        dataBinding = false
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }
    
    // APK优化配置
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
    
    
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // 莫奈取色支持（移除重复的Material库）
    implementation("androidx.palette:palette-ktx:1.0.0")
    
    // UI组件（使用更轻量级版本）
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.androidx.swiperefreshlayout)

    // 仅在需要时添加测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}