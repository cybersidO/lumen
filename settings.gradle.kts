pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // M8's PDF engine needs the PDFium JNI bindings, and those are published only on
        // JitPack — they are not on Maven Central under any coordinate. JitPack builds
        // artifacts on demand from a GitHub tag rather than having them staged and signed
        // by the publisher, so it is a weaker link than Central and is deliberately fenced
        // in: `includeGroup` means Gradle will consult jitpack.io for this one group and
        // for nothing else, so it can never be asked for — or answer with — a substitute
        // for any other dependency in the build. Declared last so Central always wins a
        // tie. See DECISIONS.md.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.marain87") }
        }
    }
}

rootProject.name = "Lumen"
include(":app")
