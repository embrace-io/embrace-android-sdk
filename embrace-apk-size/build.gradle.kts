import com.android.build.api.artifact.SingleArtifact
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.math.abs

buildscript {
    dependencies {
        classpath(libs.smali.dexlib2)
    }
}

plugins {
    id("com.android.application")
}

android {
    namespace = "io.embrace.android.embracesdk.apksize"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "io.embrace.android.embracesdk.apksize"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"

        // the ABI used by most devices
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    flavorDimensions += "sdk"
    productFlavors {
        create("baseline") {
            dimension = "sdk"
        }
        create("embrace") {
            dimension = "sdk"
            proguardFile("embrace-proguard-rules.pro")
        }
    }

    lint {
        checkDependencies = false
        checkReleaseBuilds = false
    }
}

/**
 * Compares the baseline and embrace APKs by size.
 */
abstract class ApkSizeReportTask : DefaultTask() {

    interface Flavor {
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NONE)
        val apkDir: DirectoryProperty

        /** R8's mapping, to give the dex classes their original names. */
        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        val mapping: RegularFileProperty

        /** The runtime classpath's class jars, to tell which library each dex class came from. */
        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NONE)
        val classpath: ConfigurableFileCollection

        /** The library name of each [classpath] jar, by its absolute path. */
        @get:Input
        val libraries: MapProperty<String, String>
    }

    @get:Nested
    abstract val baseline: Flavor

    @get:Nested
    abstract val embrace: Flavor

    /** The `apk-size.properties` written by this task on main. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mainSizes: RegularFileProperty

    /** The embrace flavor's unstripped native libraries, whose debug info attributes [EMBRACE_NATIVE_LIB] to its sources. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val embraceJniLibs: ConfigurableFileCollection

    /** The NDK, for its LLVM tools. */
    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun report() {
        val sizes = Properties()
        breakdown(baseline.apk()).forEach { (name, size) -> sizes["$BASELINE.$name"] = "$size" }
        breakdown(embrace.apk()).forEach { (name, size) -> sizes["$EMBRACE.$name"] = "$size" }
        val main = mainSizes.orNull?.asFile?.let { file -> Properties().apply { file.inputStream().use(::load) } }

        val rows = (CATEGORIES + TOTAL).map { name ->
            val baseline = checkNotNull(sizes.size(BASELINE, name))
            val embrace = checkNotNull(sizes.size(EMBRACE, name))
            val cost = embrace - baseline
            val mainCost = main?.let { m -> m.size(EMBRACE, name)?.let { e -> m.size(BASELINE, name)?.let { e - it } } }
            Row(name, baseline, embrace, cost, mainCost?.let { cost - it })
        }
        val total = rows.last()
        val percent = String.format(Locale.US, "%+.1f%%", total.cost * 100.0 / total.baseline)
        val incomparable = if (main == null) emptyList() else rows.filter { it.vsMain == null }.map { it.name }
        if (incomparable.isNotEmpty()) {
            logger.warn("Main's sizes have nothing for $incomparable, so those rows aren't compared.")
        }

        val baselineDex = dexByLibrary(baseline)
        val embraceDex = dexByLibrary(embrace)
        val dexLibraries = grownLibraries(baselineDex - UNATTRIBUTED, embraceDex - UNATTRIBUTED)
        val unattributed = LibraryRow(
            "Not attributed to a class (strings, ids, annotations)",
            baselineDex.getValue(UNATTRIBUTED),
            embraceDex.getValue(UNATTRIBUTED),
        )
        val dexTotal = LibraryRow("Total dex (uncompressed)", baselineDex.values.sum(), embraceDex.values.sum())

        val baselineNative = nativeByLibrary(baseline.apk())
        val embraceNative = nativeByLibrary(embrace.apk())
        val nativeLibraries = grownLibraries(baselineNative, embraceNative)
        val nativeTotal = LibraryRow("Total native libraries", baselineNative.values.sum(), embraceNative.values.sum())
        val nativeSources = embraceNativeSources(embrace.apk())

        val headings = listOfNotNull("Without SDK", "With SDK", "SDK cost", main?.let { "vs main" })
        val text = buildString {
            appendLine()
            appendLine("Embrace SDK APK size impact (release, R8, arm64-v8a)")
            appendLine()
            appendLine(textRow(listOf("") + headings))
            rows.forEach { row ->
                if (row === total) appendLine("-".repeat(FIRST_COLUMN + COLUMN * headings.size))
                appendLine(textRow(row.cells(main != null)))
            }
            appendLine()
            appendLine("The Embrace SDK changes the APK size by ${total.cost.signedHumanSize()} (${total.cost} bytes, $percent).")
            total.vsMain?.let { appendLine("Compared with main, the SDK's cost changes by ${it.signedHumanSize()} ($it bytes).") }
        }
        val markdown = buildString {
            appendLine("### Embrace SDK APK size impact")
            appendLine()
            appendLine(
                "Release build, R8 minified, arm64-v8a." +
                    if (main != null) " _vs main_ is the change in SDK cost compared with main." else ""
            )
            appendLine()
            appendLine(headings.joinToString(" | ", "| | ", " |"))
            appendLine("|---" + "|---:".repeat(headings.size) + "|")
            rows.forEach { row ->
                val bold = row === total
                appendLine(row.cells(main != null).joinToString(" | ", "| ", " |") { if (bold) "**$it**" else it })
            }
            if (incomparable.isNotEmpty()) {
                appendLine()
                appendLine("Main's sizes have nothing for ${incomparable.joinToString()}, so those rows show n/a.")
            }
            appendLine()
            appendLine("#### Code by library")
            appendLine()
            appendLine("<details>")
            appendLine("<summary>${dexLibraries.libraryCount()} the SDK adds or grows</summary>")
            appendLine()
            appendLine(
                "Uncompressed dex bytes, so they don't add up to the compressed _Code (dex)_ row above. Libraries " +
                    "that shrink, where R8 optimizes them differently once the SDK is included, aren't listed."
            )
            appendLine()
            appendLibraryTable(dexLibraries, listOf(unattributed), dexTotal)
            appendLine()
            appendLine("</details>")
            appendLine()
            appendLine("#### Native code by library")
            appendLine()
            appendLine("<details>")
            appendLine("<summary>${nativeLibraries.libraryCount("native ")} the SDK adds or grows</summary>")
            appendLine()
            appendLine("Compressed sizes in the APK. Libraries that shrink aren't listed.")
            appendLine()
            appendLibraryTable(nativeLibraries, emptyList(), nativeTotal)
            appendLine()
            appendLine("</details>")
            appendLine()
            appendLine("#### `$EMBRACE_NATIVE_LIB` by source")
            appendLine()
            appendLine("<details>")
            appendLine("<summary>What the ${nativeSources.values.sum().humanSize()} of `$EMBRACE_NATIVE_LIB` is made of</summary>")
            appendLine()
            appendLine(
                "Each function and variable is attributed to the source file its debug info names. Templates " +
                    "instantiated for libunwindstack's types count as libunwindstack."
            )
            appendLine()
            appendNativeSourceTable(nativeSources)
            appendLine()
            appendLine("</details>")
        }

        outputDir.get().asFile.apply {
            mkdirs()
            resolve("apk-size.md").writeText(markdown)
            resolve("apk-size.properties").outputStream().use { sizes.store(it, null) }
        }
        logger.quiet(text)
    }

    private class Row(val name: String, val baseline: Long, val embrace: Long, val cost: Long, val vsMain: Long?)

    private fun Row.cells(compared: Boolean): List<String> = listOfNotNull(
        name,
        baseline.humanSize(),
        embrace.humanSize(),
        cost.signedHumanSize(),
        if (compared) vsMain?.signedHumanSize() ?: "n/a" else null,
    )

    /** Null if there is no size for [name]. The total sums every key, so it survives category renames. */
    private fun Properties.size(flavor: String, name: String): Long? = if (name == TOTAL) {
        stringPropertyNames().filter { it.startsWith("$flavor.") }.takeIf { it.isNotEmpty() }?.sumOf { getProperty(it).toLong() }
    } else {
        getProperty("$flavor.$name")?.toLong()
    }

    private class LibraryRow(val name: String, val baseline: Long, val embrace: Long) {
        val cost: Long get() = embrace - baseline
    }

    /** The libraries the SDK makes bigger, biggest cost first. */
    private fun grownLibraries(baseline: Map<String, Long>, embrace: Map<String, Long>): List<LibraryRow> =
        (baseline.keys + embrace.keys)
            .map { LibraryRow(it, baseline[it] ?: 0, embrace[it] ?: 0) }
            .filter { it.cost > 0 }
            .sortedByDescending { it.cost }

    private fun List<LibraryRow>.libraryCount(kind: String = ""): String =
        "$size ${kind}librar${if (size == 1) "y" else "ies"}"

    private fun LibraryRow.cells(label: String): List<String> = listOf(
        label,
        if (baseline == 0L) "–" else baseline.humanSize(),
        if (embrace == 0L) "–" else embrace.humanSize(),
        cost.signedHumanSize(),
    )

    private fun StringBuilder.appendLibraryTable(rows: List<LibraryRow>, notes: List<LibraryRow>, total: LibraryRow) {
        appendLine("| Library | Without SDK | With SDK | SDK cost |")
        appendLine("|---|---:|---:|---:|")
        rows.forEach { appendLine(it.cells("`${it.name}`").joinToString(" | ", "| ", " |")) }
        notes.forEach { appendLine(it.cells("_${it.name}_").joinToString(" | ", "| ", " |")) }
        appendLine(total.cells(total.name).joinToString(" | ", "| ", " |") { "**$it**" })
    }

    private fun StringBuilder.appendNativeSourceTable(sources: Map<String, Long>) {
        val total = sources.values.sum()
        fun cells(label: String, size: Long) =
            listOf(label, size.humanSize(), String.format(Locale.US, "%.1f%%", size * 100.0 / total)).joinToString(" | ", "| ", " |")
        appendLine("| Source | Size | Share |")
        appendLine("|---|---:|---:|")
        sources.filterKeys { it !in NATIVE_METADATA }.entries.sortedByDescending { it.value }
            .forEach { (source, size) -> appendLine(cells(source, size)) }
        NATIVE_METADATA.forEach { appendLine(cells("_${it}_", sources.getValue(it))) }
        appendLine("| **Total** | **${total.humanSize()}** | **100%** |")
    }

    /**
     * Splits [EMBRACE_NATIVE_LIB] in [apk] by the source each symbol's code or data came from, using the
     * debug info of the unstripped library. The rest of the file is unwind tables and other ELF metadata.
     */
    private fun embraceNativeSources(apk: File): Map<String, Long> {
        val packaged = temporaryDir.resolve(EMBRACE_NATIVE_LIB)
        ZipFile(apk).use { zip ->
            val entry = checkNotNull(zip.getEntry("lib/$ABI/$EMBRACE_NATIVE_LIB")) { "No $EMBRACE_NATIVE_LIB in $apk" }
            zip.getInputStream(entry).use { input -> packaged.outputStream().use { input.copyTo(it) } }
        }
        val unstripped = embraceJniLibs.asFileTree.matching { include("**/$ABI/$EMBRACE_NATIVE_LIB") }.singleFile

        val symbols = ndkTool("llvm-nm", "--defined-only", "--print-size", "--demangle", unstripped.path).lines()
            .mapNotNull { SYMBOL.matchEntire(it)?.destructured }
            .filter { (_, _, type) -> type.single() in SIZED_SYMBOL_TYPES }
        val files = ndkTool(
            "llvm-symbolizer", "--obj=${unstripped.path}", "--functions=none", "--no-inlines",
            input = symbols.joinToString("\n") { (address) -> "0x$address" },
        ).lines().filter { it.isNotEmpty() }
        check(files.size == symbols.size) { "llvm-symbolizer gave ${files.size} locations for ${symbols.size} symbols" }

        val sizes = mutableMapOf<String, Long>()
        symbols.zip(files).forEach { (symbol, file) ->
            val (_, size, _, name) = symbol
            sizes.merge(nativeSource(file, name), size.toLong(16), Long::plus)
        }
        val sections = ndkTool("llvm-size", "-A", packaged.path).lines()
            .mapNotNull { line -> line.split(Regex("\\s+")).takeIf { it.size == 3 }?.let { (name, size) -> size.toLongOrNull()?.let { name to it } } }
            .toMap()
        sizes[UNWIND_TABLES] = UNWIND_SECTIONS.sumOf { sections[it] ?: 0L }
        sizes[ELF_OTHER] = packaged.length() - sizes.values.sum()
        return sizes
    }

    private fun nativeSource(file: String, name: String): String = when {
        "/3rdparty/" in file -> file.substringAfter("/3rdparty/").substringBefore('/').removeSuffix("-ndk") + " (vendored)"
        "/src/main/cpp/" in file -> "Embrace native code"
        "unwindstack" in name -> "libunwindstack (vendored)"
        "itanium_demangle" in name || name == "__cxa_demangle" -> "C++ name demangler (libc++abi)"
        else -> "C++ runtime (libc++, libc++abi, libunwind) and compiler builtins"
    }

    private fun ndkTool(tool: String, vararg args: String, input: String = ""): String {
        val executable = ndkDirectory.get().asFile.resolve("toolchains/llvm/prebuilt").listFiles().orEmpty().singleOrNull()
            ?.resolve("bin/$tool") ?: error("Expected one LLVM toolchain in ${ndkDirectory.get()}")
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(listOf(executable.path) + args)
            standardInput = input.byteInputStream()
            standardOutput = output
        }
        return output.toString()
    }

    private fun Flavor.apk(): File = apkDir.get().asFile.singleApk()

    private fun File.singleApk(): File = listFiles { file -> file.extension == "apk" }.orEmpty().singleOrNull()
        ?: error("Expected exactly one APK in $this")

    /**
     * Splits the APK's uncompressed dex bytes by the library each class came from. dexlib2 doesn't count the
     * bytes classes share (string data, ids) and some of their own (annotations), so those go to [UNATTRIBUTED].
     */
    private fun dexByLibrary(flavor: Flavor): Map<String, Long> {
        val originalNames = flavor.mapping.get().asFile.useLines { lines ->
            lines.filter { it.isNotEmpty() && !it[0].isWhitespace() && !it.startsWith("#") && " -> " in it }
                .associate { line -> line.removeSuffix(":").split(" -> ").let { (name, obfuscated) -> obfuscated to name } }
        }
        val jarLibraries = flavor.libraries.get()
        val classLibraries = HashMap<String, String>()
        flavor.classpath.files.forEach { jar ->
            val library = jarLibraries.getValue(jar.absolutePath)
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().map { it.name }.filter { it.endsWith(".class") }.forEach { entry ->
                    classLibraries.putIfAbsent(entry.removeSuffix(".class").replace('/', '.'), library)
                }
            }
        }

        // R8 adds classes no jar has, such as Foo$$ExternalSyntheticLambda0, so try the outer classes too
        fun library(name: String): String =
            generateSequence(name) { it.substringBeforeLast('$', "").ifEmpty { null } }
                .firstNotNullOfOrNull { classLibraries[it] } ?: UNKNOWN_LIBRARY

        val sizes = mutableMapOf(UNATTRIBUTED to 0L)
        ZipFile(flavor.apk()).use { zip ->
            zip.entries().asSequence().filter { it.name.endsWith(".dex") }.forEach { entry ->
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val classSizes = DexBackedDexFile(Opcodes.getDefault(), bytes).classes.map { classDef ->
                    val name = classDef.type.removeSurrounding("L", ";").replace('/', '.')
                    sizes.merge(library(originalNames[name] ?: name), classDef.size.toLong(), Long::plus)
                    classDef.size.toLong()
                }
                sizes.merge(UNATTRIBUTED, bytes.size - classSizes.sum(), Long::plus)
            }
        }
        return sizes
    }

    /** The compressed size of each native library in [apk], by its file name. */
    private fun nativeByLibrary(apk: File): Map<String, Long> = ZipFile(apk).use { zip ->
        zip.entries().asSequence().filter { it.name.startsWith("lib/") && !it.isDirectory }
            .groupingBy { it.name.substringAfterLast('/') }
            .fold(0L) { size, entry -> size + entry.compressedSize }
    }

    /**
     * Splits the APK's file size into categories by each entry's compressed size. Zip headers and
     * the signing block are counted as "Other" so the categories add up to the file size.
     */
    private fun breakdown(apk: File): Map<String, Long> {
        val sizes = CATEGORIES.associateWith { 0L }.toMutableMap()
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                sizes.merge(category(entry.name), entry.compressedSize, Long::plus)
            }
        }
        sizes.merge(OTHER, apk.length() - sizes.values.sum(), Long::plus)
        return sizes
    }

    private fun category(name: String): String = when {
        name.endsWith(".dex") -> "Code (dex)"
        name.startsWith("lib/") -> "Native libraries"
        name.startsWith("res/") || name == "resources.arsc" -> "Resources"
        else -> OTHER
    }

    private fun textRow(cells: List<String>): String =
        cells[0].padEnd(FIRST_COLUMN) + cells.drop(1).joinToString("") { it.padStart(COLUMN) }

    private fun Long.humanSize(): String {
        val magnitude = abs(this)
        return when {
            magnitude >= MIB -> String.format(Locale.US, "%.2f MiB", this.toDouble() / MIB)
            magnitude >= KIB -> String.format(Locale.US, "%.1f KiB", this.toDouble() / KIB)
            else -> "$this B"
        }
    }

    private fun Long.signedHumanSize(): String = if (this > 0) "+${humanSize()}" else humanSize()

    private companion object {
        const val KIB = 1024L
        const val MIB = 1024L * 1024L
        const val BASELINE = "baseline"
        const val EMBRACE = "embrace"
        const val OTHER = "Other"
        const val TOTAL = "Total APK"
        val CATEGORIES = listOf("Code (dex)", "Native libraries", "Resources", OTHER)
        const val UNATTRIBUTED = "<unattributed>"
        const val UNKNOWN_LIBRARY = "Unknown library"
        const val ABI = "arm64-v8a"
        const val EMBRACE_NATIVE_LIB = "libembrace-native.so"
        const val UNWIND_TABLES = "Unwind and exception tables (.eh_frame, .gcc_except_table)"
        const val ELF_OTHER = "Other ELF data (relocations, dynamic symbols, unnamed constants, padding)"
        val NATIVE_METADATA = listOf(UNWIND_TABLES, ELF_OTHER)
        val UNWIND_SECTIONS = listOf(".eh_frame", ".eh_frame_hdr", ".gcc_except_table")

        /** Code and data symbols. Others, such as .bss, take no bytes in the file. */
        val SIZED_SYMBOL_TYPES = "tTwWrRdDvV".toSet()

        /** An llvm-nm line with a size: address, size, type and name. */
        val SYMBOL = Regex("([0-9a-f]+) ([0-9a-f]+) (\\S) (.+)")
        const val FIRST_COLUMN = 20
        const val COLUMN = 16
    }
}

val apkSizeReport = tasks.register<ApkSizeReportTask>("apkSizeReport") {
    group = "verification"
    description = "Prints how much the Embrace SDK adds to a release APK. -Pembrace.apkSizeMain=<path> compares with main."
    outputDir.set(layout.buildDirectory.dir("reports/apk-size"))
    mainSizes.set(layout.file(providers.gradleProperty("embrace.apkSizeMain").map(::File)))
    ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory)
}

androidComponents {
    beforeVariants { it.enable = it.buildType == "release" }
    onVariants { variant ->
        val classJars = variant.runtimeConfiguration.incoming.artifactView {
            attributes { attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar") }
        }.artifacts
        apkSizeReport.configure {
            val flavor = when (variant.flavorName) {
                "baseline" -> baseline
                "embrace" -> embrace
                else -> error("Unexpected flavor ${variant.flavorName}")
            }
            flavor.apkDir.set(variant.artifacts.get(SingleArtifact.APK))
            flavor.mapping.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
            flavor.classpath.from(classJars.artifactFiles)
            if (flavor === embrace) {
                embraceJniLibs.from(
                    variant.runtimeConfiguration.incoming.artifactView {
                        attributes { attribute(Attribute.of("artifactType", String::class.java), "android-jni") }
                    }.files
                )
            }
            flavor.libraries.set(
                classJars.resolvedArtifacts.map { artifacts ->
                    artifacts.associate { artifact ->
                        val library = when (val id = artifact.id.componentIdentifier) {
                            is ProjectComponentIdentifier -> id.projectPath
                            is ModuleComponentIdentifier -> "${id.group}:${id.module}"
                            else -> id.displayName
                        }
                        artifact.file.absolutePath to library
                    }
                }
            )
        }
    }
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.firebase.messaging)

    "embraceImplementation"(project(":embrace-android-sdk"))
}

tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "lint" })
}
