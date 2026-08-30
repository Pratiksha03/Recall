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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 shrinks and optimises; more importantly a release build is the only
            // one where Compose's bundled baseline profiles apply, so the framework
            // code is AOT-compiled instead of interpreted on first run. That is the
            // single biggest difference in perceived smoothness on a real phone.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Signed with the debug key purely so you can install it for testing.
            // This is NOT a distributable build: it cannot go on the Play Store, and
            // anyone's debug keystore can sign an update over it. Generating a real
            // upload key is a separate step, only needed if you ever publish.
            signingConfig = signingConfigs.getByName("debug")
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

    // --- zstd: Anki compresses the collection inside a .colpkg/.apkg with it ---
    // The AAR carries a prebuilt native library per ABI (~0.5 MB each). There is no
    // zstd in the JDK or in Android, and no usable pure-Java one, so this is the price
    // of reading anything exported by Anki 2.1.50 or later. Pinned below 1.5.7-13,
    // where the AAR started demanding compileSdk 37.
    implementation("com.github.luben:zstd-jni:1.5.7-12@aar")

    // --- WorkManager: runs the daily reminder even when the app is closed ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- plain JVM unit tests: ./gradlew test ---
    testImplementation("junit:junit:4.13.2")

    // --- on-device tests: ./gradlew connectedDebugAndroidTest ---
    // Only the package importer needs these: unzipping, zstd and SQLite are all real
    // Android machinery, so there is nothing to check without a device.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
