/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

// Force Material 3 to the artifact shipped with Compose Multiplatform 1.12.
// material-kolor 6 strictly constrains this version in common metadata, but that
// constraint does not reach jvmMain/androidMain. Without the force, platform
// compilations resolve an older Material 3 and common code that uses the newer
// API fails with invisible/HIDDEN references.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.compose.material3:material3:${libs.versions.composeMaterial3.get()}")
        // org.tiqian 走 Central snapshot 仓（维护者手动发布的 dev 通道，正式 alpha
        // 发布后钉回固定版本）。短缓存让新发布的 snapshot 十分钟内可见。
        cacheChangingModulesFor(10, "minutes")
    }
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        exclude("build/generated/**")
        exclude("**/build/generated/ksp/**")
        exclude("**/ksp/**")
        exclude { it.file.absolutePath.contains("/build/generated/") }
    }
}

tasks.withType<KtLintFormatTask>().configureEach {
    exclude("**/generated/**")
    exclude("build/generated/**")
    exclude("**/build/generated/ksp/**")
    exclude("**/ksp/**")
    exclude { it.file.absolutePath.contains("/build/generated/") }
}

tasks
    .withType<GenerateReportsTask>()
    .matching { it.name in setOf("ktlintAndroidMainSourceSetFormat", "ktlintJvmMainSourceSetFormat") }
    .configureEach {
        enabled = false
    }

mapOf(
    "runKtlintFormatOverAndroidMainSourceSet" to
        listOf("src/androidMain/kotlin"),
    "runKtlintFormatOverAndroidFullSourceSet" to
        listOf("src/androidFull/kotlin", "src/tiqianMarkdownMain/kotlin"),
    "runKtlintFormatOverJvmMainSourceSet" to listOf("src/jvmMain/kotlin"),
    "runKtlintFormatOverCommonMainSourceSet" to listOf("src/commonMain/kotlin"),
    "runKtlintFormatOverJvmTestSourceSet" to listOf("src/jvmTest/kotlin"),
).forEach { (taskName, sourcePaths) ->
    tasks.withType<KtLintFormatTask>().matching { it.name == taskName }.configureEach {
        setSource(
            sourcePaths.map { sourcePath ->
                fileTree(sourcePath) {
                    include("**/*.kt")
                }
            },
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "com.github.zly2006.zhihu.shared"
        compileSdk = 37
        minSdk = 27

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
    }
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    macosArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val androidFull =
            create("androidFull") {
                dependsOn(commonMain.get())
                kotlin.srcDir("src/tiqianMarkdownMain/kotlin")
                dependencies {
                    implementation("org.tiqian:markdown-compose:0.1.0-SNAPSHOT")
                    implementation("org.tiqian:math-font-stix:0.1.0-SNAPSHOT")
                }
            }
        val androidLite =
            create("androidLite") {
                dependsOn(commonMain.get())
            }
        val androidVariantTaskPattern = Regex("^(?:assemble|bundle|install|test|connected|compile)(Full|Lite)")
        val requestedAndroidVariants =
            gradle.startParameter.taskNames
                .mapNotNull { taskName ->
                    val task = taskName.substringAfterLast(':')
                    androidVariantTaskPattern
                        .find(task)
                        ?.groupValues
                        ?.get(1)
                        ?.lowercase()
                }
        val selectedAndroidVariants = requestedAndroidVariants.toSet()
        require(selectedAndroidVariants.size <= 1) {
            "Full and Lite Android tasks require separate Gradle invocations"
        }
        when (selectedAndroidVariants.singleOrNull()) {
            "full" -> androidMain.get().dependsOn(androidFull)
            "lite" -> androidMain.get().dependsOn(androidLite)
            null -> Unit
        }

        commonMain.dependencies {
            api(project(":shared-local-db"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.core)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.material.kolor)
            implementation(libs.ksoup)
            implementation("me.zly2006.markdown:markdown-renderer:${property("markdown.version")}")
            implementation(libs.datetime)
            implementation(libs.kotlinx.io)
            implementation(libs.serialization.json)
            implementation(libs.aboutlibraries.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain {
            dependencies {
                implementation(libs.activity.compose)
                implementation(libs.browser)
                implementation(libs.core.ktx)
                implementation(libs.lifecycle.livedata.ktx)
                implementation(libs.media)
                implementation(libs.webkit)
                implementation(libs.zxing.embedded)
                implementation(libs.zxing.core)
                implementation(libs.coil.gif)
                implementation(libs.coil.network.ktor3.android)
                implementation(libs.ktor.client.android)
                implementation(libs.telephoto)
                implementation(libs.jsoup)
            }
        }
        // JavaFX POM 使用 ${javafx.platform} classifier，Gradle 不会自动解析，
        // 需要显式指定平台 classifier 以获取含实际类的 jar（裸 jar 只含空 MANIFEST）。
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val fxClassifier =
            when {
                osName.contains("mac") && (osArch == "aarch64" || osArch == "arm64") -> "mac-aarch64"
                osName.contains("mac") -> "mac"
                osName.contains("win") -> "win"
                osName.contains("linux") && (osArch == "aarch64" || osArch == "arm64") -> "linux-aarch64"
                else -> "linux"
            }
        val javafxModules = listOf("javafx-base", "javafx-graphics", "javafx-controls", "javafx-web", "javafx-swing")

        jvmMain {
            kotlin.srcDir("src/tiqianMarkdownMain/kotlin")
        }
        jvmMain.dependencies {
            implementation(libs.compose.ui.backhandler.desktop)
            implementation(libs.navigationevent)
            implementation("org.tiqian:markdown-compose:0.1.0-SNAPSHOT")
            implementation("org.tiqian:math-font-stix:0.1.0-SNAPSHOT")
            implementation(libs.coil.network.ktor3)
            implementation(libs.sqlite.bundled)
            implementation(compose.desktop.currentOs)
            implementation(libs.zxing.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.coroutines.swing)
            // JavaFX WebView 用于桌面端内嵌风控验证页面。
            // JavaFX 模块依赖关系：
            //   javafx-web → javafx-controls → javafx-graphics → javafx-base
            //              → javafx-media → javafx-graphics
            //   javafx-swing → javafx-graphics
            //   Platform 在 javafx-graphics 中，Scene 也在 javafx-graphics 中
            // 使用 compileOnly 避免将 JavaFX 传递给依赖 shared 的其他模块
            javafxModules.forEach { compileOnly("org.openjfx:$it:${libs.versions.javafx.get()}:$fxClassifier") }
        }
        macosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(libs.jsoup)
        }
    }
}
