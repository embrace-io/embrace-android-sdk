import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "io.embrace.android.embracesdk.minified"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.embrace.android.embracesdk.minified"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("minified") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFiles("test-proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    testBuildType = "minified"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

/**
 * Writes the names that must survive R8 to an androidTest asset
 */
abstract class GenerateExpectedNamesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val serviceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bytecodeFeatures: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configInstrumentationFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val lines = mutableListOf<String>()

        serviceFiles.files.sorted().forEach { file ->
            file.readLines()
                .map { it.substringBefore('#').trim() }
                .filter(String::isNotEmpty)
                .forEach { lines += "provider $it" }
        }

        // e.g. "owner": "io/embrace/.../FcmBytecodeEntrypoint", "name": "onMessageReceived", "descriptor": "(L...;)V"
        val insert = Regex(
            "\"insert\"\\s*:\\s*\\{\\s*\"owner\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*," +
                "\\s*\"descriptor\"\\s*:\\s*\"([^\"]+)\""
        )
        insert.findAll(bytecodeFeatures.get().asFile.readText()).forEach {
            val (owner, name, descriptor) = it.destructured
            lines += "method ${owner.replace('/', '.')} $name ${descriptor.paramCount()}"
        }

        // e.g. boolMethod("isNativeCrashCaptureEnabled") in EnabledFeatureConfigInstrumentation.kt
        val configMethod = Regex("[a-zA-Z]+Method\\(\\s*\"([a-zA-Z0-9]+)\"")
        configInstrumentationFiles.files.sorted().forEach { file ->
            val cls = "io.embrace.android.embracesdk.internal.config.instrumented." + when (val base = file.name.removeSuffix("Instrumentation.kt")) {
                "SharedObjectFilesMap" -> "Base64SharedObjectFilesMapImpl"
                else -> "${base}Impl"
            }
            val names = configMethod.findAll(file.readText()).map { it.groupValues[1] }.toList()
            check(names.isNotEmpty()) { "No instrumented config methods found in $file" }
            names.forEach { lines += "method $cls $it -1" }
        }

        check(lines.any { it.startsWith("provider ") }) { "No InstrumentationProvider services files found" }
        check(lines.any { it.contains("BytecodeEntrypoint") }) { "No bytecode entrypoints found" }

        outputDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
            resolve("expected_names.txt").writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    /** Counts the parameters in a JVM method descriptor such as `(Ljava/lang/String;Z)V`. */
    private fun String.paramCount(): Int {
        val params = substring(indexOf('(') + 1, indexOf(')'))
        var count = 0
        var i = 0
        while (i < params.length) {
            while (params[i] == '[') i++
            i = if (params[i] == 'L') params.indexOf(';', i) + 1 else i + 1
            count++
        }
        return count
    }
}

val root = rootProject.layout.projectDirectory
val generateExpectedNames = tasks.register<GenerateExpectedNamesTask>("generateExpectedNames") {
    serviceFiles.from(
        root.asFileTree.matching {
            include("embrace-android-*/src/main/resources/META-INF/services/io.embrace.android.embracesdk.internal.arch.InstrumentationProvider")
            exclude("*-fakes/**")
        }
    )
    bytecodeFeatures.set(root.file("embrace-gradle-plugin/src/main/resources/bytecode_instrumentation_features.json"))
    configInstrumentationFiles.from(
        root.dir("embrace-gradle-plugin/src/main/java/io/embrace/android/gradle/plugin/instrumentation/config/arch/sdk")
            .asFileTree.matching { include("*Instrumentation.kt") }
    )
    outputDir.set(layout.buildDirectory.dir("generated/expectedNames"))
}

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(
            generateExpectedNames,
            GenerateExpectedNamesTask::outputDir,
        )
    }
}

dependencies {
    implementation(project(":embrace-android-sdk"))
    implementation(project(":embrace-android-instrumentation-huc"))
    implementation(project(":embrace-android-instrumentation-androidx-navigation"))
    implementation(project(":embrace-android-instrumentation-compose-tap"))

    // libraries the SDK's instrumentation targets, so R8 sees the same classpath as a customer app
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose)
    implementation(libs.opentelemetry.kotlin.sdk.api)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
