plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.fishking.core.database"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.getByName("test").assets.srcDir("schemas")
}

dependencies {
    api(project(":core:model"))
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    ksp("androidx.room:room-compiler:2.7.2")

    testImplementation(kotlin("test"))
    testImplementation("androidx.room:room-testing:2.7.2")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Include freshly exported Room schema in the same invocation's migration-test APK.
tasks.matching { it.name == "mergeDebugUnitTestAssets" }.configureEach {
    dependsOn("kspDebugKotlin")
}
