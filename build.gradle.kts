import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

plugins {
    kotlin("multiplatform") version Deps.JetBrains.Kotlin.VERSION
    application
    id("com.github.johnrengelman.shadow") version Deps.Plugins.Shadow.VERSION
}

group = "com.hoffi"
version = "1.0.0"
val theMainClass by extra { "com.hoffi.web.AppKt" }
tasks.register<CheckVersionsTask>("checkVersions") // implemented in buildSrc/src/main/kotlin/Deps.kt

repositories {
    mavenCentral()
}

fun hostNative(
    kotlinNativeTargetWithHostTests: KotlinNativeTargetWithHostTests,
    buildGradle: Build_gradle
) {
    kotlinNativeTargetWithHostTests.binaries {
        executable {
            entryPoint(buildGradle.theMainClass.replaceAfterLast(".", "main"))
        }
    }
}


kotlin {
    jvm {
        //withJava() // applies the Gradle java plugin to allow the multiplatform project to have both Java and Kotlin source files (src/jvmMain/java/...)

        // compilations.all {
        //     kotlinOptions.jvmTarget = "1.8"
        // }

    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" ->
            macosX64("native") { hostNative(this, this@Build_gradle) }
        hostOs == "Linux" ->
            linuxX64("native") { hostNative(this, this@Build_gradle) }
        isMingwX64 ->
            mingwX64("native") { hostNative(this, this@Build_gradle) }
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    // https://kotlinlang.org/docs/mpp-share-on-platforms.html#share-code-in-libraries
    // To enable usage of platform-dependent libraries in shared source sets, add the following to your `gradle.properties`
    // kotlin.mpp.enableGranularSourceSetsMetadata=true
    // kotlin.native.enableDependencyPropagation=false
    sourceSets {
        val commonMain by getting  { // predefined by gradle multiplatform plugin
            dependencies {
                //implementation("io.github.microutils:kotlin-logging:2.0.6")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Deps.Misc.DATETIME.VERSION}")
            }
        }
        val commonTest by getting {
            dependencies {
            }
        }

        val jvmMain by getting {
            //print("${name} dependsOn: ")
            //println(dependsOn.map { it.name }.joinToString())
            dependencies {
                implementation("ch.qos.logback:logback-classic:${Deps.Logging.logback.version}")
                implementation("org.slf4j:slf4j-api:${Deps.Logging.slf4j_VERSION}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val nativeMain by getting { // named("macMain") {
            dependencies {

            }
        }
        val nativeTest by getting { // named("macTest") {
            dependencies {

            }
        }
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
        from(kotlin.jvm().compilations.getByName("main").output)
        configurations = mutableListOf(kotlin.jvm().compilations.getByName("main").compileDependencyFiles as Configuration)
    }
    // val build by existing {
    //     dependsOn(shadowCreate)
    // }
    getByName<JavaExec>("run") {
        classpath += objects.fileCollection().from(named("compileKotlinJvm"), configurations.named("jvmRuntimeClasspath"))
    }
}

// Helper tasks to speed up things and don't waste time
//=====================================================
// 'c'ompile 'c'ommon
val cc by tasks.registering {
    dependsOn(":compileKotlinMetadata",
        ":compileKotlinJvm",     ":compileKotlinNative",
        ":compileTestKotlinJvm", ":compileTestKotlinNative")
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
