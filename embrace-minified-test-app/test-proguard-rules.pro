# Applied to the androidTest APK only. The test APK is shrunk against the app's mapping, so
# references to classes that R8 removed from the app would otherwise fail the build.
-dontwarn **
-dontobfuscate
