import org.gradle.api.Project

/**
 * Number of JVM forks to use for a module's unit tests. Each fork pays for its own sandbox (e.g. Robolectric)
 * so extra forks only help when there are enough test classes to amortize that cost.
 */
fun Project.testForkCount(): Int {
    val classCount = fileTree("src").matching {
        include("test/**/*Test.kt", "test/**/*Test.java", "integrationTest/**/*Test.kt")
    }.files.size
    val cpuLimit = Runtime.getRuntime().availableProcessors() / 3 + 1
    return minOf(cpuLimit, classCount / CLASSES_PER_FORK).coerceAtLeast(1)
}

/**
 * Heap for a module's unit test forks. Only the SDK and core modules hold enough tests per fork to need more than 1g.
 */
fun Project.testHeapSize(): String = if (path in LARGE_TEST_MODULES) "2g" else "1g"

private const val CLASSES_PER_FORK = 8
private val LARGE_TEST_MODULES = setOf(":embrace-android-sdk", ":embrace-android-core")
