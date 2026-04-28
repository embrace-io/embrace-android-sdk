// Add Kotlin compiler plugins so they exist only in the test classpath
configurations.matching {
    it.name.startsWith("kotlinCompilerPluginClasspath") && it.name.endsWith("UnitTest")
}.configureEach {
    dependencies.add(project.dependencies.create(findLibrary("kotlin.compose.compiler.plugin")))
    dependencies.add(project.dependencies.create(findLibrary("kotlin.serialization.compiler.plugin")))
}
