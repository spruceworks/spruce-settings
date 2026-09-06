plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "dev.spruceworks"
version = "1.0.0"
description = "SpruceSettings — free per-player settings plugin with a public toggle API"

// Single source of truth for the runtime-downloaded driver. processResources
// substitutes it into plugin.yml's `libraries:` block, so the compile
// classpath and what the server actually fetches can never drift apart.
val sqliteJdbcVersion = "3.49.1.0"

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

    // bStats stays shaded: 53 KB, and metrics must not depend on the server
    // reaching a Maven repo at startup.
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // sqlite-jdbc is NOT shaded. Its native binaries for every platform are
    // ~24 MB of the jar — the exact packaging that got SpruceBounty 1.0.0
    // rejected by SpigotMC. Declared in plugin.yml's `libraries:` block instead;
    // Paper's library loader fetches it from Maven Central on first start.
    compileOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
    testImplementation("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")

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
        val props = mapOf(
            "version" to project.version.toString(),
            "sqliteJdbcVersion" to sqliteJdbcVersion,
        )
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
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        // Only bStats is bundled, relocated so it cannot clash with another
        // plugin's copy. sqlite-jdbc is fetched at runtime by Paper's library
        // loader and is deliberately absent from this jar.
        // NOTE: dev.spruceworks.settings.api is deliberately NOT relocated — it is the
        // public surface other plugins compile against.
        relocate("org.bstats", "dev.spruceworks.settings.libs.bstats")

        doLast {
            val jar = archiveFile.get().asFile
            logger.lifecycle("shadowJar: ${jar.name} = %.2f MB".format(jar.length() / 1024.0 / 1024.0))
            // Guard against the driver silently becoming bundled again and
            // pushing us back over the marketplace upload limit.
            zipTree(jar).matching { include("**/sqlite/**", "**/org/sqlite/**") }
                .files.firstOrNull()?.let {
                    throw GradleException(
                        "sqlite-jdbc was shaded into the jar again (found ${it.name}). " +
                        "It must stay in plugin.yml's libraries: block."
                    )
                }
        }
    }

    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        // Downloads this Paper version and boots a local test server with the plugin installed.
        minecraftVersion("26.2")
    }
}
