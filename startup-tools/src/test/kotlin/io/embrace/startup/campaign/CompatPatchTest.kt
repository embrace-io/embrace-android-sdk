package io.embrace.startup.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CompatPatchTest {

    private fun repo(): Path {
        val repo = Files.createTempDirectory("repo")
        val app = repo.resolve("examples/ExampleApp/app")
        Files.createDirectories(app)
        Files.createDirectories(repo.resolve("examples/ExampleApp/gradle"))
        Files.writeString(repo.resolve("gradle.properties"), "group=io.embrace\nversion=9.3.0-SNAPSHOT\n")
        Files.writeString(
            repo.resolve("examples/ExampleApp/gradle/libs.versions.toml"),
            "[versions]\nagp = \"8.7.0\"\nembrace = \"9.2.0\"\n\n[libraries]\n",
        )
        Files.writeString(
            app.resolve("build.gradle.kts"),
            "plugins {\n    alias(libs.plugins.android.application)\n    alias(libs.plugins.embrace)\n}\n\n" +
                "dependencies {\n    implementation(\"io.embrace:embrace-android-sdk\")\n}\n",
        )
        return repo
    }

    /**
     * Apply, build and revert are three separate commands, so a run that dies between them leaves the
     * checkout pinned to the wrong SDK and every later build resolves it. Nothing used to say so.
     */
    @Test
    fun `a checkout left patched by a dead run says so, and still holds the original files`() {
        val repo = repo()
        val patch = CompatPatch(repo)
        patch.apply("7.5.0")
        orphanJournal(patch)

        val status = patch.status()
        assertTrue(status[0], status[0].startsWith("PATCHED for 7.5.0: 2 file(s) rewritten"))
        assertTrue(status[0], status[0].contains("no longer running"))
        assertTrue(status[1], status[1].contains("--revert-all"))
        assertTrue(status.any { it.contains("patched: examples/ExampleApp/gradle/libs.versions.toml") })

        val lines = patch.apply("7.4.0")
        assertTrue(lines[0], lines[0].startsWith("WARNING: this checkout was already patched for 7.5.0"))
        assertTrue(lines[0], lines[0].contains("a previous run died"))
        assertTrue(lines[1], lines[1].contains("restores them"))

        patch.revertAll()
        assertEquals(listOf("clean: no compat patch applied"), patch.status())
        assertTrue(Files.readString(patch.catalog).contains("embrace = \"9.2.0\""))
    }

    @Test
    fun `a second patcher that is still alive is reported as a conflict, not a dead run`() {
        val repo = repo()
        val patch = CompatPatch(repo)
        patch.apply("7.5.0")

        val lines = patch.apply("7.4.0")

        assertTrue(lines[0], lines[0].contains("is patching this checkout right now"))
    }

    /** Rewrites the journal's owner to a pid that cannot be alive, standing in for a run that was killed. */
    private fun orphanJournal(patch: CompatPatch) {
        val journal = patch.readJournal()
        journal.ownerPid = DEAD_PID
        Files.writeString(
            patch.journalFile,
            io.embrace.startup.core.json.StartupJson.encodeToString(CompatPatch.Journal.serializer(), journal),
        )
    }

    @Test
    fun `recipe selection follows the major version`() {
        assertEquals("modern", CompatPatch.recipeFor("local"))
        assertEquals("modern", CompatPatch.recipeFor("9.2.0"))
        assertEquals("modern", CompatPatch.recipeFor("8.0.0"))
        assertEquals("7x", CompatPatch.recipeFor("7.5.0"))
        assertEquals("6x", CompatPatch.recipeFor("6.14.0"))
    }

    @Test
    fun `a 7x apply swaps the plugin, adds the fcm dependency and journals both files, and revert restores them`() {
        val repo = repo()
        val patch = CompatPatch(repo)
        val lines = patch.apply("7.5.0")
        assertEquals(
            "recipe 7x for 7.5.0: swazzler plugin DOES apply on modern AGP; embrace-android-fcm must be declared " +
                "explicitly because later versions bundle it transitively",
            lines[0],
        )
        assertEquals("patched examples/ExampleApp/app/build.gradle.kts", lines[1])
        assertEquals("pin set to 7.5.0; journal has 2 snapshot(s)", lines[2])
        val catalog = Files.readString(patch.catalog)
        assertTrue(catalog.contains("embrace = \"7.5.0\""))
        assertTrue(catalog.contains("agp = \"8.7.0\""))
        val build = Files.readString(repo.resolve("examples/ExampleApp/app/build.gradle.kts"))
        assertTrue(build.contains("    id(\"io.embrace.swazzler\")"))
        assertFalse(build.contains("alias(libs.plugins.embrace)"))
        assertTrue(build.contains("dependencies {\n    implementation(\"io.embrace:embrace-android-fcm:7.5.0\")"))
        assertEquals("7.5.0", patch.readJournal().version)

        // A second apply on top keeps the ORIGINAL snapshots (first-write wins).
        patch.apply("7.4.0")
        assertEquals(2, patch.readJournal().files.size)
        assertTrue(patch.readJournal().files.getValue("examples/ExampleApp/gradle/libs.versions.toml").contains("embrace = \"9.2.0\""))

        val reverted = patch.revertAll()
        assertEquals(
            listOf(
                "reverted examples/ExampleApp/gradle/libs.versions.toml",
                "reverted examples/ExampleApp/app/build.gradle.kts",
                "journal cleared - now confirm `git status --short` shows only intended changes",
            ),
            reverted,
        )
        assertTrue(Files.readString(patch.catalog).contains("embrace = \"9.2.0\""))
        assertTrue(Files.readString(repo.resolve("examples/ExampleApp/app/build.gradle.kts")).contains("alias(libs.plugins.embrace)"))
        assertFalse(Files.exists(patch.journalFile))
        assertEquals(listOf("nothing to revert"), patch.revertAll())
    }

    @Test
    fun `local resolves the working-tree version, 6x removes the plugin, and verify reports the gradle outcome`() {
        val repo = repo()
        var called: List<String>? = null
        val patch = CompatPatch(repo) { cmd, _ ->
            called = cmd
            io.embrace.startup.core.proc.Processes.Output(1, "…stdout tail…", "e: compile error")
        }
        val local = patch.apply("local")
        assertEquals("pin set to 9.3.0-SNAPSHOT; journal has 1 snapshot(s)", local.last())
        assertTrue(Files.readString(patch.catalog).contains("embrace = \"9.3.0-SNAPSHOT\""))
        patch.revertAll()

        val six = patch.apply("6.14.0")
        assertTrue(six.last().startsWith("REMINDER: 6.x needs hand-injected config resources"))
        val build = Files.readString(repo.resolve("examples/ExampleApp/app/build.gradle.kts"))
        assertTrue(build.contains("// [vfm] plugin intentionally omitted for 6.x plugin-less build"))
        assertFalse(build.contains("alias(libs.plugins.embrace)"))

        val (ok, lines) = patch.verifyBuild()
        assertFalse(ok)
        assertEquals("VERIFY FAILED: patched tree does not build - fix the recipe before running cells", lines.last())
        assertTrue(called!!.contains(":app:assembleBenchmark"))
    }

    private companion object {
        /** Above the pid ceiling of every platform this runs on, so `ProcessHandle.of` finds nothing. */
        const val DEAD_PID = 999_999_999L
    }
}
