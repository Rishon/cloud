plugins {
    java
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    id("io.freefair.lombok") version "8.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
}

group = "systems.rishon"
version = "1.0.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://repo.rishon.systems/releases")
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.34")

    // Docker
    implementation("com.github.docker-java:docker-java-core:3.4.0")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.4.0")

    // Utils-API
    implementation("systems.rishon:utils-api:1.0.0")
}

tasks.shadowJar {
    archiveBaseName.set("Cloud")
    archiveClassifier.set("")
    mergeServiceFiles()
}

val targetJavaVersion = 21
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

publishing {
    repositories {
        maven {
            name = "seladevelopment-repo"
            url = uri("https://repo.rishon.systems/releases")
            credentials {
                username = System.getenv("MAVEN_NAME")
                password = System.getenv("MAVEN_SECRET")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "systems.rishon"
            artifactId = "cloud-api"
            version = "${project.version}"
            from(components["java"])
        }
    }
}