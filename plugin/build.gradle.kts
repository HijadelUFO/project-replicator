/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    `maven-publish`

    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    google()
    jcenter()
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.getByName("functionalTestImplementation").extendsFrom(configurations.getByName("testImplementation"))

// Add a task to run the functional tests
val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
}

val check by tasks.getting(Task::class) {
    // Run the functional tests as part of `check`
    dependsOn(functionalTest)
}

dependencies {
    implementation(project(":model"))

    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    compileOnly("com.android.tools.build:gradle:4.1.0-beta02")
    testImplementation("com.android.tools.build:gradle:4.1.0-beta02")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("com.google.truth:truth:1.0.1")
}

abstract class AgpClasspathTask: DefaultTask() {
    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(PathSensitivity.RELATIVE)
    abstract val classpath: ConfigurableFileCollection

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputFile: RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun action() {
        outputFile.get().asFile.writeText(
            classpath.files.joinToString(separator = "\n")
        )
    }
}

val agpClasspath = configurations.maybeCreate("agpClasspath")

val outputFileLocation = layout.buildDirectory.file("agpclasspath.txt")

val agpTask by tasks.registering(AgpClasspathTask::class) {
    classpath.from(agpClasspath.incoming.artifacts.artifactFiles)
    outputFile.set(outputFileLocation)
}

functionalTest.configure {
    systemProperty("agp.classpath", outputFileLocation.forUseAtConfigurationTime().get().asFile.absolutePath)
    dependsOn(agpTask)
}

dependencies.add(agpClasspath.name, "com.android.tools.build:gradle:4.1.0-beta02")
dependencies.add(agpClasspath.name, "org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72")

gradlePlugin {
    val extractor by plugins.creating {
        id = "com.android.gradle.project.replicator"
        implementationClass = "com.android.gradle.replicator.ProjectReplicatorPlugin"
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=enable")
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 10 //Runtime.runtime.availableProcessors()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.android.gradle.replicator"
            artifactId = "project-replicator"
            version = "0.1"

            from(components["java"])
            pom {
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}

// easier to run task for local publishing
val publishLocal by tasks.registering {
    dependsOn("publishMavenPublicationToMavenLocal")
}