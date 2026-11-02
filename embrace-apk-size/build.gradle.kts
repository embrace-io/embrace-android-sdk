import com.android.build.api.artifact.SingleArtifact
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.math.abs

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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baselineApkDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val embraceApkDir: DirectoryProperty

    /** The `apk-size.properties` written by this task on main. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mainSizes: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun report() {
        val sizes = Properties()
        breakdown(baselineApkDir.get().asFile.singleApk()).forEach { (name, size) -> sizes["$BASELINE.$name"] = "$size" }
        breakdown(embraceApkDir.get().asFile.singleApk()).forEach { (name, size) -> sizes["$EMBRACE.$name"] = "$size" }
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

    private fun File.singleApk(): File = listFiles { file -> file.extension == "apk" }.orEmpty().singleOrNull()
        ?: error("Expected exactly one APK in $this")

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
        const val FIRST_COLUMN = 20
        const val COLUMN = 16
    }
}

val apkSizeReport = tasks.register<ApkSizeReportTask>("apkSizeReport") {
    group = "verification"
    description = "Prints how much the Embrace SDK adds to a release APK. -Pembrace.apkSizeMain=<path> compares with main."
    outputDir.set(layout.buildDirectory.dir("reports/apk-size"))
    mainSizes.set(layout.file(providers.gradleProperty("embrace.apkSizeMain").map(::File)))
}

androidComponents {
    beforeVariants { it.enable = it.buildType == "release" }
    onVariants { variant ->
        val apkDir = variant.artifacts.get(SingleArtifact.APK)
        apkSizeReport.configure {
            when (variant.flavorName) {
                "baseline" -> baselineApkDir.set(apkDir)
                "embrace" -> embraceApkDir.set(apkDir)
            }
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
