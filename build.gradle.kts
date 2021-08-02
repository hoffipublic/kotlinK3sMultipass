plugins {
    kotlin("jvm") version Deps.JetBrains.Kotlin.VERSION
    kotlin("plugin.serialization") version Deps.JetBrains.Kotlin.VERSION
    application
    id("com.github.johnrengelman.shadow") version Deps.Plugins.Shadow.VERSION
}

group = "com.hoffi"
version = "1.0.0"
val theMainClass by extra { "com.hoffi.infra.AppKt" }
tasks.register<CheckVersionsTask>("checkVersions") // implemented in buildSrc/src/main/kotlin/Deps.kt

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8") {
        version {
            strictly(Deps.JetBrains.Kotlin.VERSION)
        }
    }
    implementation("org.jetbrains.kotlin:kotlin-reflect") { // e.g. for LoggerDelegate
        version {
            strictly(Deps.JetBrains.Kotlin.VERSION)
        }
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Deps.Misc.KOTLINXJSON.VERSION}")
    implementation("net.mamoe.yamlkt:yamlkt:${Deps.Misc.KOTLINXJSON.yamlVersion}")

    implementation("io.arrow-kt:arrow-core:${Deps.Core.Arrow.VERSION}")
    implementation("ch.qos.logback:logback-classic:${Deps.Logging.logbackVersion}")
    implementation(Deps.Logging.slf4jApi.full())

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Deps.Misc.DATETIME.VERSION}")
    implementation("com.github.ajalt.clikt:clikt:${Deps.Misc.CLIKT.VERSION}")
    implementation("com.bkahlert.koodies:koodies:1.9.4")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions{
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

application {
    mainClass.set(theMainClass)
}

tasks {
    val shadowCreate by creating(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        manifest { attributes["Main-Class"] = theMainClass }
        archiveClassifier.set("fat")
        mergeServiceFiles()
    }
    // val build by existing {
    //     dependsOn(shadowCreate)
    // }
}

// Helper tasks to speed up things and don't waste time
//=====================================================
// 'c'ompile 'c'ommon
val cc by tasks.registering {
    dependsOn(
        ":compileKotlin",
        ":compileTestKotlin")
}

// ################################################################################################
// #####    pure informational stuff on stdout    #################################################
// ################################################################################################
tasks.register("printClasspath") {
    group = "misc"
    description = "print classpath"
    doLast {
        //project.getConfigurations().filter { it.isCanBeResolved }.forEach {
        //    println(it.name)
        //}
        //println()
        val targets = listOf(
            "metadataCommonMainCompileClasspath",
            "commonMainApiDependenciesMetadata",
            "commonMainImplementationDependenciesMetadata",
            "jvmCompileClasspath",
            "kotlinCompilerClasspath"
        )
        targets.forEach { targetConfiguration ->
            println("$targetConfiguration:")
            println("=".repeat("$targetConfiguration:".length))
            project.getConfigurations()
                .getByName(targetConfiguration).files
                // filters only existing and non-empty dirs
                .filter { (it.isDirectory() && it.listFiles().isNotEmpty()) || it.isFile() }
                .forEach { println(it) }
        }
    }
}
