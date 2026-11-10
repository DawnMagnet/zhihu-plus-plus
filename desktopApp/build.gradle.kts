import org.gradle.jvm.tasks.Jar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appVersionName = property("app.versionName").toString()
val desktopPackageVersion = if (appVersionName.count { it == '.' } >= 2) appVersionName else "$appVersionName.0"

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Keep the desktop classpath on the same Material 3 artifact as :shared.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.compose.material3:material3:${libs.versions.composeMaterial3.get()}")
    }
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    // JavaFX WebView 用于桌面端内嵌风控验证页面。
    // JavaFX POM 使用 ${javafx.platform} classifier，Gradle 不会自动解析。
    // 使用 resolutionStrategy 强制所有 JavaFX 模块使用平台 classifier。
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
    val javafxModules = listOf("javafx-base", "javafx-controls", "javafx-graphics", "javafx-web", "javafx-swing", "javafx-media")
    javafxModules.forEach { module ->
        implementation("org.openjfx:$module:${libs.versions.javafx.get()}:$fxClassifier")
    }
}

fun currentDesktopNativeExcludes(): List<String> {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val isArm64 = osArch == "aarch64" || osArch == "arm64"
    val sqliteTarget =
        when {
            osName.contains("mac") && isArm64 -> "osx_arm64"
            osName.contains("mac") -> "osx_x64"
            osName.contains("win") -> "windows_x64"
            osName.contains("linux") && isArm64 -> "linux_arm64"
            else -> "linux_x64"
        }
    val skikoTarget =
        when {
            osName.contains("mac") && isArm64 -> "macos-arm64"
            osName.contains("mac") -> "macos-x64"
            osName.contains("win") -> "windows-x64"
            osName.contains("linux") && isArm64 -> "linux-arm64"
            else -> "linux-x64"
        }
    val sqliteNatives =
        listOf(
            "natives/linux_arm64/**",
            "natives/linux_x64/**",
            "natives/osx_arm64/**",
            "natives/osx_x64/**",
            "natives/windows_x64/**",
        )
    val skikoNatives =
        listOf(
            "libskiko-linux-arm64.so",
            "libskiko-linux-x64.so",
            "libskiko-macos-arm64.dylib",
            "libskiko-macos-x64.dylib",
            "skiko-windows-x64.dll",
        )

    return sqliteNatives.filterNot { it == "natives/$sqliteTarget/**" } +
        skikoNatives.filterNot { it.contains(skikoTarget) }
}

tasks.withType<Jar>().configureEach {
    if (name == "packageReleaseUberJarForCurrentOS") {
        exclude(currentDesktopNativeExcludes())
    }
}

compose.desktop {
    application {
        mainClass = "com.github.zly2006.zhihu.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(true)
            optimize.set(false)
            configurationFiles.from(project.file("proguard-release.pro"))
        }

        nativeDistributions {
            // 对外发布的安装形式：Windows 用 MSI，Linux 用 AppImage（由 CI 用 appimagetool
            // 把 jpackage 的 app-image 目录封装成单文件）。macOS 走 macosApp 的 Kotlin/Native 构建。
            targetFormats(TargetFormat.AppImage, TargetFormat.Msi)
            packageName = "Zhihu++"
            packageVersion = desktopPackageVersion
            description = "Free and ad-free third-party Zhihu client"
            vendor = "zly2006"

            // jlink 裁剪内嵌运行时：默认只含 java.base/java.desktop/java.logging/jdk.crypto.ec，
            // 其余模块按依赖补齐（JavaFX WebView 的 JS 互操作、JDBC、HTTP、中文扩展字符集等）。
            // 依赖变化后用 ./gradlew :desktopApp:suggestModules 校对；注意 checkRuntime 不会
            // 校验模块是否齐全，缺模块只会在启动时报错，改完列表要实际跑一次安装包。
            modules(
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.security.jgss",
                "java.sql",
                "java.xml",
                "jdk.charsets",
                "jdk.jsobject",
                "jdk.unsupported",
                "jdk.zipfs",
            )

            linux {
                iconFile.set(project.file("src/main/resources/desktop-icon.png"))
            }
            windows {
                iconFile.set(project.file("desktop-icon.ico"))
                menu = true
                menuGroup = "Zhihu++"
                shortcut = true
                upgradeUuid = "84FED2C6-8F40-4FDA-B84A-616602D7A888"
            }
        }
    }
}
