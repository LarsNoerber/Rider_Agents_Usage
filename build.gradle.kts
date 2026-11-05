
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.2")
    }
}

intellijPlatform {
    instrumentCode = false

    pluginVerification {
        ides {
            create(IntelliJPlatformType.Rider, "2026.2.3.1")
        }
    }

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }
        vendor {
            name = "Lars Nörber"
            url = "https://github.com/larsnoerber/Rider_Agents_Usage"
        }
        changeNotes = """
            <h3>AgentMeter 1.2.6</h3>
            <ul>
              <li>Use the public IDE product-code API for Android Studio detection, removing the PlatformUtils internal-API usages reported by Marketplace verification.</li>
              <li>Add Gemini in Android Studio: show each reported model's remaining quota and available reset time in the overview.</li>
              <li>Select the matching Gemini quota service for personal, Business and Enterprise accounts.</li>
              <li>Show Gemini's reported plan, including explicitly identified Free accounts, and choose all or individual status-bar models in settings.</li>
              <li>Display a named, quota-colored remaining percentage for every reported Gemini model directly in the status bar.</li>
              <li>Enable Gemini by default in Android Studio when no selection is stored, while preserving explicit saved choices.</li>
              <li>Label successful Gemini reads as local state reads and show quota measurement age as unknown. Gemini controls quota updates.</li>
              <li>Improve model label readability, preserve full model IDs in tooltips, and group Gemini with the other provider widgets.</li>
              <li>Explain in settings when the optional Gemini plugin is unavailable. AgentMeter reads its existing state without accessing credentials.</li>
            </ul>
        """.trimIndent()
    }
}

kotlin {
    jvmToolchain(25)
}

tasks {
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    register<Exec>("buildVsCodeExtension") {
        group = "build"
        description = "Compile and package the Visual Studio Code extension."
        workingDir = layout.projectDirectory.dir("vscode").asFile
        if (isWindows) {
            commandLine("cmd.exe", "/d", "/c", "npm run package")
        } else {
            commandLine("npm", "run", "package")
        }
    }

    register("buildAllExtensions") {
        group = "build"
        description = "Build all editor packages supported on the current operating system."
        dependsOn("buildPlugin", "buildVsCodeExtension")
        if (isWindows) dependsOn("buildVisualStudioExtension")
    }

    register<Exec>("buildVisualStudioExtension") {
        group = "build"
        description = "Build and package the Visual Studio 2022/2026 extension on Windows."
        workingDir = layout.projectDirectory.asFile
        commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "visualstudio/build.ps1")
        doFirst { check(isWindows) { "Visual Studio packaging requires Windows and Visual Studio MSBuild." } }
    }

    processResources {
        from(listOf("LICENSE", "EULA.md")) {
            into("META-INF")
        }
    }

    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        compilerOptions.freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}
