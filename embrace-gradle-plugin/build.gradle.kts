plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    id("groovy")
    id("maven-publish")
    id("signing")
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.buildconfig)
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
    androidModule.set(false)
    jvmTarget.set(JavaVersion.VERSION_11)
}

dependencies {
    compileOnly(libs.agp.api)
    compileOnly(gradleApi())

    implementation(libs.okhttp)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.zstd.jni)
    implementation(libs.asm.util)

    testImplementation(libs.agp.api)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(project(":embrace-test-common"))
}

buildConfig {
    val version: String by project
    buildConfigField("String", "VERSION", "\"$version\"")
}

allprojects {
    extra["signing.keyId"] = System.getenv("mavenSigningKeyId")
    extra["signing.secretKeyRingFile"] = System.getenv("mavenSigningKeyRingFile")
    extra["signing.password"] = System.getenv("mavenSigningKeyPassword")
}

java {
    withJavadocJar()
    withSourcesJar()
}

// marker artifact publication
gradlePlugin {
    plugins {
        create("embraceGradle") {
            id = "io.embrace.swazzler"
            group = "io.embrace"
            implementationClass = "io.embrace.android.gradle.plugin.EmbraceGradlePlugin"
            displayName = "Embrace Gradle Plugin"
            description = "The Embrace Gradle plugin uploads mapping information and instruments bytecode"
        }
    }
}

// maven-publish plugin publications settings
publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            pom {
                artifactId = "embrace-swazzler"
                name = "embrace-swazzler"
                group = "io.embrace"
                description = "Embrace Gradle Plugin"
                url = "https://github.com/embrace-io/embrace-android-sdk"
                licenses {
                    license {
                        name = "Embrace License"
                        url = "https://embrace.io/docs/terms-of-service/"
                    }
                }
                developers {
                    developer {
                        id = "dev1"
                        name = "Embrace"
                        email = "support@embrace.io"
                    }
                }
                scm {
                    connection = "scm:git:github.com/embrace-io/embrace-android-sdk.git"
                    developerConnection = "scm:git:ssh://github.com/embrace-io/embrace-android-sdk.git"
                    url = "https://github.com/embrace-io/embrace-android-sdk/tree/main"
                }
            }
        }
    }

    afterEvaluate {
        publications {
            named<MavenPublication>("embraceGradlePluginMarkerMaven") {
                pom {
                    name = "embrace-swazzler"
                    artifactId = "io.embrace.swazzler.gradle.plugin"
                    group = "io.embrace"
                    description = "Embrace Gradle Plugin"
                    url = "https://github.com/embrace-io/embrace-android-sdk"
                    licenses {
                        license {
                            name = "Embrace License"
                            url = "https://embrace.io/docs/terms-of-service/"
                        }
                    }
                    developers {
                        developer {
                            id = "dev1"
                            name = "Embrace"
                            email = "support@embrace.io"
                        }
                    }
                    scm {
                        connection = "scm:git:github.com/embrace-io/embrace-android-sdk.git"
                        developerConnection = "scm:git:ssh://github.com/embrace-io/embrace-android-sdk.git"
                        url = "https://github.com/embrace-io/embrace-android-sdk/tree/main"
                    }
                }
            }
        }
        signing {
            setRequired { gradle.taskGraph.hasTask("publishEmbraceGradlePluginMarkerMavenPublicationToSonatypeRepository") }
            sign(publishing.publications["embraceGradlePluginMarkerMaven"])
        }
    }

    repositories {
        // beta releases
        maven {
            credentials {
                username = System.getenv("MAVEN_QA_USER")
                password = System.getenv("MAVEN_QA_PASSWORD")
            }
            name = "Qa"
            url = uri("https://repo.embrace.io/repository/beta")
        }
        // the android-testing maven repository is used for publishing snapshots
        maven {
            credentials {
                username = System.getenv("MAVEN_QA_USER")
                password = System.getenv("MAVEN_QA_PASSWORD")
            }
            name = "Snapshot"
            url = uri("https://repo.embrace.io/repository/android-testing")
        }
        // sonatype repo that provides path to publishing on mavencentral
        maven {
            credentials {
                username = System.getenv("SONATYPE_USERNAME")
                password = System.getenv("SONATYPE_PASSWORD")
            }
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2")
        }
    }
    signing {
        setRequired { gradle.taskGraph.hasTask("publishPluginMavenPublicationToSonatypeRepository") }
        sign(publishing.publications["pluginMaven"])
    }
}
