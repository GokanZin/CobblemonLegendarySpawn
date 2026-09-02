pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.mcmoddev.com/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }

    val loom_version: String by settings
    val architectury_plugin_version: String by settings
    val kotlin_version: String by settings

    plugins {
        id("dev.architectury.loom") version loom_version
        id("architectury-plugin") version architectury_plugin_version
        kotlin("jvm") version kotlin_version
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.mcmoddev.com/")
        maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        maven("https://maven.impactdev.net/repository/development/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://api.modrinth.com/maven")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

rootProject.name = "LegendarySpawn"
