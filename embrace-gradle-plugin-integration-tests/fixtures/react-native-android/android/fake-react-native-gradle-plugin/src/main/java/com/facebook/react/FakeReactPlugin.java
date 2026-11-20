package com.facebook.react;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Stands in for the React Native Gradle plugin, which needs node_modules and Node to bundle JS.
 * Registers the bundle task the Embrace plugin reads the bundle and sourcemap from, at the
 * output paths the real plugin uses.
 */
public class FakeReactPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("createBundleReleaseJsAndAssets", FakeBundleTask.class, task -> {
            task.getJsBundleDir().set(project.getLayout().getBuildDirectory().dir("generated/assets/react/release"));
            task.getJsSourceMapsDir().set(project.getLayout().getBuildDirectory().dir("generated/sourcemaps/react/release"));
        });
    }
}
