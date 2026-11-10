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

@file:OptIn(ExperimentalEncodingApi::class)

import buildlogic.gitHash
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

plugins {
    id("com.android.application")
    id("com.mikepenz.aboutlibraries.plugin.android")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("kotlin-parcelize")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

android {
    namespace = "com.github.zly2006.zhihu"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.github.zly2006.zhplus"
        minSdk = 27
        targetSdk = 35
        versionCode = property("app.versionCode").toString().toIntOrNull() ?: 1
        versionName = property("app.versionName").toString()

        testInstrumentationRunner = "com.github.zly2006.zhihu.ZhihuInstrumentedTestRunner"
    }

    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
            buildConfigField("boolean", "IS_LITE", "false")
        }
        create("lite") {
            dimension = "version"
            isDefault = true
            buildConfigField("boolean", "IS_LITE", "true")
            applicationIdSuffix = ".lite"
//            versionNameSuffix = "-lite"
        }
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("en", "zh")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    signingConfigs {
        if (System.getenv("signingKey") != null) {
            register("env") {
                storeFile =
                    file("zhihu.jks").apply {
                        writeBytes(Base64.decode(System.getenv("signingKey")))
                    }
                storePassword = System.getenv("keyStorePassword")
                keyAlias = System.getenv("keyAlias")
                keyPassword = System.getenv("keyPassword")
            }
        }
    }

    buildTypes {
        val gitHash = gitHash(rootProject.projectDir)
        debug {
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            manifestPlaceholders["zhihuBuildType"] = "debug"
            manifestPlaceholders["zhihuGitHash"] = gitHash
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            manifestPlaceholders["zhihuBuildType"] = "release"
            manifestPlaceholders["zhihuGitHash"] = gitHash
            // Release signing secrets are optional. Fork and PR builds sign with the
            // debug key so the lite release APK is installable.
            signingConfig =
                if (System.getenv("signingKey") != null) {
                    signingConfigs.getByName("env")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            merges +=
                listOf(
                    "META-INF/LICENSE-MIT.txt",
                    "META-INF/LICENSE-UPSTREAM-MIT.txt",
                    "META-INF/LICENSE-APACHE-2.0.txt",
                    "META-INF/NOTICE.md",
                )
            excludes +=
                listOf(
                    "META-INF/DEPENDENCIES",
//                    "META-INF/*.version",
                    "META-INF/**/LICENSE",
                    "META-INF/**/LICENSE.txt",
                    "META-INF/proguard/*",
                    "**.kotlin_module",
                    "kotlin-tooling-metadata.json",
                    "DebugProbesKt.bin",
//                    "META-INF/*.kotlin_module",
                )
        }
    }

    androidComponents {
        beforeVariants(selector().all()) { variantBuilder ->
            val flavorName = variantBuilder.flavorName
            if (variantBuilder.buildType == "release") {
                val minify =
                    when (flavorName) {
                        "lite" -> true
                        else -> false
                    }
                variantBuilder.isMinifyEnabled = minify
                variantBuilder.shrinkResources = minify
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.freeCompilerArgs.add("-Xdebug")
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}

// Keep the app classpath on the same Material 3 artifact as :shared.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.compose.material3:material3:${libs.versions.composeMaterial3.get()}")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.coroutines.core)
    implementation(libs.ktor.client.core.jvm)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation.jvm)
    implementation(libs.ktor.serialization.json)
    implementation(libs.datetime)
    implementation(libs.browser)

    implementation("me.zly2006.markdown:markdown-renderer:${property("markdown.version")}")

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3.android)
    implementation(libs.coil.gif)
    implementation(libs.telephoto)

    implementation(libs.material.kolor)

    implementation(libs.jsoup)

    // ZXing for QR code scanning
    implementation(libs.zxing.embedded)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.navigation.compose)

    implementation(libs.webkit)
    implementation(libs.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)
    implementation(libs.compose.components.resources.android)
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.aboutlibraries.compose)
    "fullImplementation"(project(":sentence_embeddings"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // HanLP for Chinese NLP
    "fullImplementation"(libs.hanlp)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ktor.client.mock)
}
