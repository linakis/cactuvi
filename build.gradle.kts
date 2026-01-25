// Top-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("io.realm.kotlin:gradle-plugin:1.16.0")
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
    id("com.ncorti.ktfmt.gradle") version "0.18.0" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// Create a task to run all code quality checks
tasks.register("codeQualityCheck") {
    group = "verification"
    description = "Run all code quality checks (formatting, linting, static analysis)"
    
    dependsOn(":app:spotlessCheck")
    dependsOn(":app:detekt")
}

// Create a task to auto-fix code quality issues
tasks.register("codeQualityFix") {
    group = "formatting"
    description = "Auto-fix all code quality issues (formatting, linting)"
    
    dependsOn(":app:spotlessApply")
    dependsOn(":app:ktlintFormat")
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.ncorti.ktfmt.gradle")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Configure ktlint
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        ignoreFailures.set(false)
        debug.set(false)
        outputToConsole.set(true)
        outputColorName.set("RED")
    }

    // Configure ktfmt
    configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
        kotlinLangStyle()
    }

    // Configure Spotless
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude(
                "**/build/**",
                "**/generated/**",
                "**/gen/**",
                "**/.gradle/**",
                "**/R.kt",
                "**/BuildConfig.kt"
            )
            // Use ktfmt instead of ktlint for Spotless (less strict)
            ktfmt("0.47").kotlinlangStyle()
            trimTrailingWhitespace()
            endWithNewline()
        }
        
        kotlinGradle {
            target("**/*.gradle.kts")
            targetExclude("**/build/**")
            ktfmt("0.47").kotlinlangStyle()
            trimTrailingWhitespace()
            endWithNewline()
        }
        
        format("xml") {
            target("**/res/**/*.xml", "**/AndroidManifest.xml")
            targetExclude("**/build/**")
            trimTrailingWhitespace()
            indentWithSpaces(4)
            endWithNewline()
        }
    }
    
    // Configure Detekt
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom("$rootDir/config/detekt/detekt.yml")
        baseline = file("$rootDir/config/detekt/baseline.xml")
        parallel = true
        ignoreFailures = true // Don't fail build initially
        basePath = projectDir.absolutePath
        
        source.setFrom(
            "src/main/java",
            "src/main/kotlin"
        )
    }
    
    // Make check task run linters
    tasks.named("check") {
        dependsOn("spotlessCheck")
        dependsOn("detekt")
    }
}



