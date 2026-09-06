import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.necmat.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.necmat.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 34
        versionName = "1.33"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // github = distribuție prin GitHub Releases (cu self-update)
    // play   = distribuție prin Google Play (fără self-update; Play face actualizările)
    flavorDimensions += "dist"
    productFlavors {
        create("github") {
            dimension = "dist"
            isDefault = true
            // APK-ul universal ar avea ~45 MB din cauza bibliotecilor native ML Kit
            // pentru 4 arhitecturi; telefoanele actuale sunt arm64 → ~12 MB.
            ndk { abiFilters += listOf("arm64-v8a") }
        }
        create("play") {
            dimension = "dist"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile") ?: "../necmat-release.keystore")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // recunoaștere de text pe dispozitiv (scanarea actului de identitate), model latin inclus în APK
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // detectarea conturului actului + îndreptare (modul livrat prin serviciile Google Play)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    // camera în aplicație pentru scanarea actului în timp real
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    testImplementation("junit:junit:4.13.2")
    // implementarea reală org.json pentru testele locale (în android.jar e mock-uită)
    testImplementation("org.json:json:20240303")
    // teste instrumentate (ex. PdfPreviewTest generează PDF-uri de verificare pe emulator)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
