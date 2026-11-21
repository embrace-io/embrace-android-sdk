package com.facebook.react;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class FakeBundleTask extends DefaultTask {

    @OutputDirectory
    public abstract DirectoryProperty getJsBundleDir();

    @OutputDirectory
    public abstract DirectoryProperty getJsSourceMapsDir();

    @TaskAction
    public void run() throws IOException {
        Files.write(
            getJsBundleDir().file("index.android.bundle").get().getAsFile().toPath(),
            "Fake bundle".getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            getJsSourceMapsDir().file("index.android.bundle.map").get().getAsFile().toPath(),
            "Fake sourcemap".getBytes(StandardCharsets.UTF_8)
        );
    }
}
