package io.embrace.android.gradle.integration.framework

import io.embrace.android.gradle.config.TestMatrix
import okhttp3.mockwebserver.MockWebServer
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger

class PluginIntegrationTestRule : ExternalResource() {

    private val uniqueDirName: AtomicInteger = AtomicInteger(0)

    private var tmpDir: File? = null
    private lateinit var server: MockWebServer
    private lateinit var apiServer: FakeApiServer
    private lateinit var assertionInterface: AssertionInterface
    private lateinit var setupInterface: SetupInterface
    private lateinit var baseUrl: String

    override fun before() {
        apiServer = FakeApiServer()
        setupInterface = SetupInterface(apiServer)
        assertionInterface = AssertionInterface(apiServer)
        server = MockWebServer().apply {
            dispatcher = apiServer
            start()
        }
        baseUrl = server.url("api").toString()
    }

    override fun after() {
        tmpDir?.deleteRecursively()
        server.shutdown()
    }

    /**
     * Runs a simple integration test for a single Gradle task. Generally speaking most test cases
     * can be written this way if you need to run something once, then assert that the file system
     * was changed appropriately. If you need to run multiple builds for one test, e.g. in order
     * to test that a task handles caching correctly, then you can simply call this function
     * multiple times in a single JUnit test case.
     */
    @Suppress("LongParameterList")
    fun runTest(

        /**
         * The path to the fixture directory, relative to embrace-gradle-plugin-integration-tests/fixtures.
         */
        fixture: String,

        /**
         * The name of the task to run.
         */
        task: String = "testTask",

        /**
         * The versions of Gradle/AGP to use.
         */
        testMatrix: TestMatrix = TestMatrix.MaxVersion,

        /**
         * The type of project. For Android projects, this class will automatically add
         * additional MVP files such as an AndroidManifest.xml, as this reduces
         * the boilerplate required in test fixtures.
         */
        projectType: ProjectType = ProjectType.JVM,

        /**
         * The path to the android project root, relative to the parent folder. This is used on hosted SDK tests, where you also need
         * to set up the parent folder of the android project.
         *
         * For example, if the android project is located at `fixtures/react-native-android/android`,
         * then the value of this parameter should be "android".
         */
        androidProjectRoot: String? = null,

        /**
         * Extra arguments to add on top of the task name.
         */
        additionalArgs: List<String> = emptyList(),

        /**
         * The expected outcome of the task.
         */
        expectedOutcome: TaskOutcome = TaskOutcome.SUCCESS,

        /**
         * Whether a debugger should be attached to the TestKit build. This stops the build
         * proceeding until a remote debugger is attached.
         */
        attachDebugger: Boolean = false,

        /**
         * Custom assertions to run after the build has finished. The assertions should focus on
         * the state of the file system.
         */
        assertions: AssertionInterface.(projectDir: File) -> Unit,

        /**
         * Custom setup to run before the build is executed. This could be used to set up a certain file system state, or even
         * assert that some preconditions are met before the build is run.
         */
        setup: SetupInterface.(projectDir: File) -> Unit = {},
    ) {
        // only copy the test fixture if it hasn't already been copied as this function can be
        // invoked multiple times per test case
        val dir = tmpDir ?: prepareTempDirectory(fixture, projectType, androidProjectRoot)
        tmpDir = dir
        val args = prepareCommandArgs(
            task,
            projectType,
            baseUrl,
            additionalArgs,
            attachDebugger,
            testMatrix
        )

        // run any preconditions before the build
        setupInterface.setup(dir)
        val result = executeGradleBuild(args, testMatrix.gradle)

        // run assertions on the build outcome
        assertTaskOutcome(result, task, expectedOutcome)
        assertionInterface.assertions(dir)
    }

    /**
     * Copies the test fixture to a temporary directory. This ensures a clean build happens each
     * time and there's no possibility of side effects from previous runs.
     */
    private fun prepareTempDirectory(
        fixture: String,
        projectType: ProjectType,
        androidProjectRoot: String?
    ): File {
        val srcDir = File("fixtures/$fixture")
        if (!srcDir.exists()) {
            error("Fixture '${srcDir.absolutePath}' not found.")
        }
        val tmpDir = Files.createTempDirectory("${uniqueDirName.incrementAndGet()}").toFile()
        if (!tmpDir.exists()) {
            error("Failed to create temporary directory $tmpDir.")
        }
        srcDir.copyRecursively(tmpDir, overwrite = true)
        val androidProjectDir = androidProjectRoot?.let { File(tmpDir, it) } ?: tmpDir
        createProjectBoilerplate(androidProjectDir, projectType)
        return androidProjectDir
    }

    private fun createProjectBoilerplate(projectDir: File, projectType: ProjectType) {
        // create an empty settings.gradle.kts file - required for Gradle TestKit
        copyFakeFile(projectDir, "settings.gradle.kts", "fakesettings.gradle.kts")

        // create a minimal AndroidManifest.xml and embrace-config.json
        if (projectType == ProjectType.ANDROID) {
            copyFakeFile(projectDir, "src/main/AndroidManifest.xml", "FakeAndroidManifest.xml")
            copyFakeFile(projectDir, "src/main/embrace-config.json", "fake-embrace-config.json")
        }
    }

    private fun copyFakeFile(projectDir: File, dst: String, resName: String) {
        File(projectDir, dst).apply {
            if (exists()) {
                // don't overwrite an existing file if a test fixture chooses to supply something
                return
            }
            parentFile.mkdirs()
            outputStream().buffered().use {
                val classLoader = checkNotNull(this@PluginIntegrationTestRule.javaClass.classLoader)
                checkNotNull(classLoader.getResourceAsStream(resName)).use { resourceStream ->
                    resourceStream.buffered().copyTo(it)
                }
            }
        }
    }

    /**
     * Executes a Gradle build using TestKit.
     */
    private fun executeGradleBuild(
        args: List<String>,
        gradleVersion: String
    ): BuildResult = GradleRunner.create()
        .withProjectDir(tmpDir)
        .withArguments(args)
        .withGradleVersion(gradleVersion)
        .forwardStdOutput(System.out.writer())
        .forwardStdError(System.err.writer())
        .build()

    /**
     * Adds some additional arguments to the gradle build for better performance of the test suite.
     */
    private fun prepareCommandArgs(
        task: String,
        projectType: ProjectType,
        baseUrl: String?,
        additionalArgs: List<String>,
        attachDebugger: Boolean,
        testMatrix: TestMatrix,
    ): List<String> {
        val args = mutableListOf(task)
        args.addAll(additionalArgs)
        args.addAll(
            listOf(
                "--stacktrace",
                "-Dorg.gradle.daemon=false",
                "-Dorg.gradle.parallel=true",
                "-Dorg.gradle.caching=true",
                "-Dorg.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC -XX:MaxMetaspaceSize=1g",
                "-Dorg.gradle.java.home=${testMatrix.jdk.path}",
                "-Pembrace.baseUrl=$baseUrl",
                "-Pagp_version=${testMatrix.agp}",
                "-Pkotlin_version=${testMatrix.kotlin}",
                "-Pplugin_snapshot_version=${loadSnapshotVersion()}",
            )
        )
        // Android projects currently require this flag to compile
        if (projectType == ProjectType.ANDROID) {
            args.add("-Pandroid.useAndroidX=true")
        }
        if (attachDebugger) {
            println(
                "Waiting for debugger to attach. You need to run the remote debugging " +
                    "configuration from Android Studio for this test case to continue. Please " +
                    "see this module's README.md for further details."
            )
            args.addAll(
                listOf(
                    "-Dorg.gradle.debug=true",
                )
            )
        }
        return args
    }

    private fun loadSnapshotVersion(): String {
        val rootDir = File(System.getProperty("user.dir")).parentFile
        val file = File(rootDir, "gradle.properties")
        file.inputStream().buffered().use {
            Properties().apply {
                load(it)
                return getProperty("version")
            }
        }
    }

    /**
     * Asserts the task executed with the expected outcome. Generally speaking this will be SUCCESS.
     */
    private fun assertTaskOutcome(
        result: BuildResult,
        task: String,
        expectedOutcome: TaskOutcome
    ) {
        val buildTask = result.tasks.singleOrNull { it.path == ":$task" || it.path == ":app:$task" }
            ?: error("Task '$task' not found in result.")
        val outcome = buildTask.outcome
        assertEquals(expectedOutcome, outcome)
    }
}
