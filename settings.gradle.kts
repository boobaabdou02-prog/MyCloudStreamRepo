rootProject.name = "FrenchStreamPlugins"

// Automatically include all subdirectories that have a build.gradle.kts
File(rootDir, ".").eachDir { dir ->
    if (File(dir, "build.gradle.kts").exists() && dir.name != "build") {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
