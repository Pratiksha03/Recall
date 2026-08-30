plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")   // annotation processor, Kotlin's answer to APT
}

android {
    namespace = "com.recall.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.recall.app"
        minSdk = 26          // Android 8.0 — covers ~98% of phones in use
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Material 3's TopAppBar and friends are still marked experimental.
        // Opting in here keeps the @OptIn boilerplate out of every screen file.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }
    buildFeatures {
        compose = true
    }
}

// Room writes the database schema as JSON here on every build. Check these files
// into git: they are what lets you (and Room's migration tests) see exactly what
// changed between versions. See "Changing the database" in the README.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // --- Compose: one BOM pins every compose artifact to a matching version ---
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // --- Room: SQLite with annotations. Closest thing to JPA/Hibernate here. ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Coil: loads image files into Compose ---
    implementation("io.coil-kt:coil-compose:2.7.0")

    // --- WorkManager: runs the daily reminder even when the app is closed ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- plain JVM unit tests: ./gradlew test ---
    testImplementation("junit:junit:4.13.2")
}
