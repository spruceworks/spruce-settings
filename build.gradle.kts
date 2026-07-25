plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "dev.spruceworks"
version = "1.0.0"
description = "SpruceSettings — free per-player settings plugin with a public toggle API"

java {
    // Paper 26.x requires Java 25 (https://docs.papermc.io/paper/dev/project-setup/).
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    // Published so other plugins can compile against the public API package.
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    maven("https://repo.extendedclip.com/releases/") { name = "placeholderapi" }
}

dependencies {
    // Version format per https://docs.papermc.io/paper/dev/project-setup/ — resolves the latest 26.2 build.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("me.clip:placeholderapi:2.12.3")

    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Paper supplies slf4j at runtime, but paper-api is compileOnly so it is not
    // on the test classpath — the storage tests need a real Logger to construct.
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.17")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        // The unshaded jar has no bundled runtime deps and is not a usable plugin — label it clearly.
        archiveClassifier.set("unshaded")
    }

    shadowJar {
        archiveClassifier.set("")
        // Rewrites META-INF/services/* content too, not just class file locations —
        // required for sqlite-jdbc's JDBC 4 auto-registration to survive relocation.
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        // Relocate bundled runtime deps so they cannot clash with other plugins that bundle them.
        // NOTE: dev.spruceworks.settings.api is deliberately NOT relocated — it is the
        // public surface other plugins compile against.
        relocate("org.bstats", "dev.spruceworks.settings.libs.bstats")
        relocate("org.sqlite", "dev.spruceworks.settings.libs.sqlite")
    }

    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        // Downloads this Paper version and boots a local test server with the plugin installed.
        minecraftVersion("26.2")
    }
}
