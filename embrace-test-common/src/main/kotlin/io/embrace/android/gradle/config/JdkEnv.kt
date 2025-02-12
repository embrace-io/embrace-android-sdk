package io.embrace.android.gradle.config

enum class JdkEnv(val path: String) {
    JAVA_11(resolveJdkPath(11)),
    JAVA_17(resolveJdkPath(17))
}

private fun resolveJdkPath(version: Int): String {
    return System.getenv("JAVA_HOME_${version}_X64")
        ?: System.getenv("LOCAL_JAVA_${version}_PATH")
        ?: error(
            "No JDK path supplied for Java $version. Please check the " +
                "LOCAL_JAVA_${version}_PATH or JAVA_HOME_${version}_X64 envars are set."
        )
}
