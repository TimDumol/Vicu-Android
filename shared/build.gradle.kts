plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        // The expect/actual classes (Logger, Base64Decoder, PlatformContext, ...) are in Beta.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // The AGP 9 library plugin for Kotlin Multiplatform (the old com.android.library plugin is not
    // compatible with the Kotlin Multiplatform plugin from AGP 9).
    androidLibrary {
        namespace = "com.rendyhd.vicu.shared"
        compileSdk = 36
        minSdk = 26

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }

        // The unit tests of this module (androidHostTest and commonTest) run on the JVM.
        withHostTest {
            // Shared code logs through android.util.Log; unit tests must not crash on it.
            isReturnDefaultValues = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.cascade.editor)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.androidx.navigation.compose)
            implementation(libs.reorderable)
            implementation(libs.coil.compose)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.tink.android)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        // The platform.* bindings (Foundation, UIKit, Security) are marked experimental. Every iOS
        // source set opts in, since a source set must use the opt-ins of the ones it depends on.
        matching { it.name.startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.sqlite.jdbc)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

ksp {
    // Where Room exports the schema of each database version (the migration tests read these).
    // Set here rather than through the Room Gradle plugin, which does not configure the target
    // of the AGP 9 Kotlin Multiplatform library plugin and would export nothing.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

// The iOS tests read test-fixtures/ from this repository (the simulator runs on this Mac and can
// read its files), at the path written into a generated constant.
val generateIosTestPaths by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/iosTestPaths/kotlin")
    val repoRoot = rootDir.absolutePath
    inputs.property("repoRoot", repoRoot)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/rendyhd/vicu/util/TestPaths.kt").asFile
        file.parentFile.mkdirs()
        val escaped = repoRoot.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        file.writeText(
            "package com.rendyhd.vicu.util\n\n" +
                "internal object TestPaths {\n    const val REPO_ROOT = \"$escaped\"\n}\n",
        )
    }
}
kotlin.sourceSets.matching { it.name == "iosTest" }.configureEach { kotlin.srcDir(generateIosTestPaths) }

// The Kotlin Multiplatform library plugin has no `test` task (only testAndroidHostTest and
// allTests, which also lists the iOS targets), so `./gradlew test` would skip this module's tests.
tasks.register("test") {
    group = "verification"
    description = "Runs the JVM unit tests of this module (commonTest and androidHostTest)."
    dependsOn("testAndroidHostTest")
}
