pluginManagement {
    repositories {
        // Repositories used for plugin resolution
        maven("https://maven.fabricmc.net")
        gradlePluginPortal()
        maven("https://jitpack.io")        // <– add this
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.replaymod.preprocess") {
                // map the plugin ID to the JitPack module
                useModule("com.github.ReplayMod:preprocessor:${requested.version}")
            }
        }
    }
}



rootProject.name = "altoclef"

rootProject.buildFileName = "root.gradle.kts"

listOf(
    "1.21.4",
    //"1.21.1",
    //"1.21",
    //"1.20.6",
    //"1.20.5",
    //"1.20.4",
    //"1.20.2",
    //"1.20.1",
    //"1.19.4",
    //"1.18.2",
    //"1.18",
    //"1.17.1",
//    "1.16.5"// fixme 1.16.5 is not working due to java version drop to java 8
).forEach { version ->
    include(":$version")
    project(":$version").apply {
        projectDir = file("versions/$version")
        buildFileName = "../../build.gradle"
        name = version
    }
}