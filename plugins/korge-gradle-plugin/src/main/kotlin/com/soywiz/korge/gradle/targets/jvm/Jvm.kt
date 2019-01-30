package com.soywiz.korge.gradle.targets.jvm

import com.soywiz.korge.gradle.*
import com.soywiz.korge.gradle.util.*
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import proguard.gradle.*

fun Project.configureJvm() {
	gkotlin.targets.add((gkotlin.presets.getAt("jvm") as KotlinJvmTargetPreset).createTarget("jvm"))

	project.dependencies.add("jvmMainImplementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	project.dependencies.add("jvmTestImplementation", "org.jetbrains.kotlin:kotlin-test")
	project.dependencies.add("jvmTestImplementation", "org.jetbrains.kotlin:kotlin-test-junit")

	addProguard()
	configureJvmTest()
}

private fun Project.configureJvmTest() {
	val jvmTest = (tasks.findByName("jvmTest") as Test)
	jvmTest.jvmArgs = (jvmTest.jvmArgs ?: listOf()) + listOf("-Djava.awt.headless=true")
}


private fun Project.addProguard() {

	// Provide default mainClassName
	if (!project.ext.has("mainClassName")) {
		project.ext.set("mainClassName", "")
	}

	// packageJvmFatJar
	val packageJvmFatJar = project.addTask<org.gradle.jvm.tasks.Jar>("packageJvmFatJar", group = korgeGroup) { task ->
		task.baseName = "${project.name}-all"
		project.afterEvaluate {
			task.manifest { manifest ->
				manifest.attributes(
					mapOf(
						"Implementation-Title" to project.ext.get("mainClassName"),
						"Implementation-Version" to project.version.toString(),
						"Main-Class" to project.ext.get("mainClassName")
					)
				)
			}
			//it.from()
			//fileTree()
			task.from(GroovyClosure(project) {
				(project["kotlin"]["targets"]["jvm"]["compilations"]["main"]["runtimeDependencyFiles"] as FileCollection).map { if (it.isDirectory) it else project.zipTree(it) as Any }
				//listOf<File>()
			})
			task.with(project.getTasksByName("jvmJar", true).first() as CopySpec)
		}
	}

	val runJvm = tasks.getByName("runJvm") as JavaExec

	project.addTask<ProGuardTask>("packageJvmFatJarProguard", group = korgeGroup, dependsOn = listOf(
		packageJvmFatJar
	)
	) { task ->
		task.libraryjars("${System.getProperty("java.home")}/lib/rt.jar")
		task.injars(packageJvmFatJar.outputs.files.toList())
		task.outjars(buildDir["/libs/${project.name}-all-proguard.jar"])
		task.dontwarn()
		task.ignorewarnings()
		//task.dontobfuscate()
		task.assumenosideeffects("""
                class kotlin.jvm.internal.Intrinsics {
                    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
                }
            """.trimIndent())

		//task.keep("class jogamp.nativetag.**")
		//task.keep("class jogamp.**")

		task.keep("class com.jogamp.** { *; }")
		task.keep("class jogamp.** { *; }")

		afterEvaluate {
			if (runJvm.main?.isNotBlank() == true) {
				task.keep("""public class ${runJvm.main} { public static void main(java.lang.String[]); }""")
			}
		}
	}
}