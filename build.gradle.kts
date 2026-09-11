plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
}

// Robolectric uses java.io.tmpdir for its dependency lock. On this Windows
// machine that property can resolve to C:\, where ordinary test workers cannot
// create the lock file. Keep every module's test runtime inside its own build
// directory so verification never depends on writing to the system-drive root.
subprojects {
    val testRuntimeTemp = layout.buildDirectory.dir("tmp/test-runtime-v24").get().asFile
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        doFirst { testRuntimeTemp.mkdirs() }
        systemProperty("java.io.tmpdir", testRuntimeTemp.absolutePath)
        systemProperty("user.home", testRuntimeTemp.absolutePath)
    }
}
