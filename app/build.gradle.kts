plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.walletconnect"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.epubreader.mainnet"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

configurations.configureEach {
    // Убираем старый BouncyCastle (jdk15on), чтобы не было конфликтов с jdk18on
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk15on")
}

dependencies {
    // AndroidX и Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material:1.7.0")  // Для ModalBottomSheet
    implementation("androidx.compose.runtime:runtime-livedata:1.7.0")
    
    // Solana Mobile SDKs
    implementation(libs.solana.web3)           // Transaction building, PublicKey, PDA
    implementation(libs.solana.rpc.core)       // RPC client for Solana
    implementation(libs.solana.mwa.client)     // Mobile Wallet Adapter (Phantom, Solflare)
    implementation(libs.kborsh)                // Borsh serialization
    implementation(libs.multimult)             // Base58 encoding
    implementation(libs.kotlinx.serialization.json)  // JSON/Borsh serialization

    // Desugaring для современных Java API
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    // BouncyCastle для криптографии (Ed25519)
    implementation(libs.bouncycastle.bcprov.jdk18on)
    
    // Навигация Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Логирование
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // HTML parsing for EPUB
    implementation(libs.jsoup)
    
    // OkHttp для RPC запросов
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Тестирование
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}