import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("dev.architectury.loom")
    id("architectury-plugin")
    kotlin("jvm")
}

// Mod
val mod_group: String by project
val mod_version: String by project
val mod_id: String by project
val mod_name: String by project
val mod_description: String by project

// Versions
val java_version: String by project
val minecraft_version: String by project
val fabric_loader_version: String by project
val fabric_api_version: String by project
val fabric_kotlin_version: String by project
val cobblemon_version: String by project

// Libraries
val snakeyaml_engine_version: String by project
val sqlite_jdbc_version: String by project
val hikaricp_version: String by project
val lombok_version: String by project
val junit_version: String by project

// External mods
val luckperms_modrinth_version: String by project
val fabric_permissions_api_version: String by project

// Local libs
val mgkcore_version: String by project

group = mod_group
version = mod_version

base {
    archivesName.set(mod_id)
}

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()

    mixin {
        defaultRefmapName.set("mixins.${mod_id}.refmap.json")
    }

    runs {
        named("client") {
            programArgs("--username", "GokanDev_")
        }
    }
}

repositories {
    mavenCentral()

    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://api.modrinth.com/maven")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(java_version.toInt()))
    }

    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(java_version.toInt())
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.fromTarget(java_version))
    }
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabric_kotlin_version")
    modImplementation("com.cobblemon:fabric:$cobblemon_version")

    // Lombok
    compileOnly("org.projectlombok:lombok:$lombok_version")
    annotationProcessor("org.projectlombok:lombok:$lombok_version")

    // Libraries embutidas no seu mod
    implementation("org.snakeyaml:snakeyaml-engine:$snakeyaml_engine_version")
    include("org.snakeyaml:snakeyaml-engine:$snakeyaml_engine_version")

    implementation("org.xerial:sqlite-jdbc:$sqlite_jdbc_version")
    include("org.xerial:sqlite-jdbc:$sqlite_jdbc_version")

    implementation("com.zaxxer:HikariCP:$hikaricp_version")
    include("com.zaxxer:HikariCP:$hikaricp_version")

    // Local libs
    modImplementation(files("libs/mGKCore-$mgkcore_version.jar"))

    // LuckPerms mod via Modrinth
    modImplementation("maven.modrinth:luckperms:$luckperms_modrinth_version")

    // Fabric Permissions API
    // Importante: não deixa compileOnly e modImplementation ao mesmo tempo.
    modImplementation("maven.modrinth:fabric-permissions-api:$fabric_permissions_api_version")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit_version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit_version")
}

tasks.processResources {
    filteringCharset = "UTF-8"

    val properties = mapOf(
        "modid" to mod_id,
        "name" to mod_name,
        "version" to project.version,
        "description" to mod_description,
        "minecraft_version" to minecraft_version,
        "fabric_loader_version" to fabric_loader_version,
        "fabric_api_version" to fabric_api_version,
        "cobblemon_version" to cobblemon_version
    )

    inputs.properties(properties)

    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}