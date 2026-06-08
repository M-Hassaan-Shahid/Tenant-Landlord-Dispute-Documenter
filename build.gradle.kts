import org.gradle.api.tasks.Copy

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}

tasks.register<Copy>("buildDebugApk") {
    group = "build"
    description = "Assemble the debug APK and copy it into releases/ for sharing or pushing."

    dependsOn(":app:assembleDebug")
    from(project(":app").layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.projectDirectory.dir("releases"))
    rename { "ProofNest-v1.0-debug.apk" }
}
