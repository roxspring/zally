import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        jcenter()
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.spring.io/libs-release")
    }
}

plugins {
    val kotlinVersion = "1.3.21"

    kotlin("jvm").version(kotlinVersion)
    kotlin("plugin.jpa").version(kotlinVersion)
    kotlin("plugin.noarg").version(kotlinVersion)
    kotlin("plugin.spring").version(kotlinVersion)
    kotlin("plugin.allopen").version(kotlinVersion)

    id("jacoco")
    id("org.springframework.boot") version "2.0.4.RELEASE"
    id("io.spring.dependency-management") version "1.0.7.RELEASE"
    id("com.github.ben-manes.versions") version "0.20.0"
    id("de.undercouch.download") version "3.4.3"
    id("org.jlleitschuh.gradle.ktlint") version "7.2.1"
    id("org.jetbrains.dokka") version "0.10.0"
    id("maven-publish")
}

allprojects {

    val group = "de.zalando"
    val projVersion = "1.0.0-dev"

    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")

    repositories {
        jcenter()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        compile("org.jetbrains.kotlin:kotlin-stdlib")

        testCompile("junit:junit:4.12")
    }

    tasks.withType(KotlinCompile::class.java).all {
        kotlinOptions.jvmTarget = "1.8"
    }

    tasks.withType(DokkaTask::class.java).all {
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/dokka"
        configuration {
            reportUndocumented = false
        }
    }

    tasks.register("javadocJar", Jar::class) {
        dependsOn(tasks["dokka"])
        archiveClassifier.set("javadoc")
        from(tasks["dokka"])
    }

    tasks.register("sourcesJar", Jar::class) {
        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    artifacts {

        add("archives", tasks["javadocJar"])
        add("archives", tasks["sourcesJar"])
    }

    publishing {
        publications {
            create<MavenPublication>(project.name) {
                groupId = group
                artifactId = project.name
                version = if (projVersion.endsWith("-dev")) projVersion.replace("-dev", "-SNAPSHOT") else projVersion

                tasks.findByPath("bootJar")?.let { artifact(it) }
                artifact(tasks["sourcesJar"])
                artifact(tasks["javadocJar"])
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/roxspring/zally")
                credentials {
                    username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
                    password = project.findProperty("gpr.key") ?: System.getenv("PASSWORD")
                }
            }
        }
    }
}

dependencies {
    val springBootVersion = "2.0.4.RELEASE"
    val jadlerVersion = "1.3.0"

    compile(project("zally-rule-api"))
    compile("com.github.zeitlinger.swagger-parser:swagger-parser:v2.0.14-z4")
    compile("com.github.java-json-tools:json-schema-validator:2.2.10")
    compile("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    compile("org.springframework.boot:spring-boot-starter-undertow:$springBootVersion")
    compile("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
    compile("org.springframework.boot:spring-boot-starter-data-jpa:$springBootVersion") {
        exclude("org.hibernate", "hibernate-entitymanager")
    }
    compile("org.flywaydb:flyway-core:5.1.4")
    compile("org.hsqldb:hsqldb:2.4.1")
    compile("org.postgresql:postgresql:42.2.4")
    compile("org.hibernate:hibernate-core:5.3.5.Final")
    compile("org.jadira.usertype:usertype.core:7.0.0.CR1") {
        exclude("org.hibernate", "hibernate-entitymanager")
    }
    compile("com.fasterxml.jackson.module:jackson-module-parameter-names")
    compile("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compile("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    // 2.9+ is invalid for maven publish
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.8")
    compile("org.zalando.stups:stups-spring-oauth2-server:1.0.22")
    compile("org.zalando:problem-spring-web:0.23.0")
    compile("org.zalando:twintip-spring-web:1.1.0")
    compile("io.github.config4k:config4k:0.4.1")

    compile("de.mpg.mpi-inf:javatools:1.1")

    testCompile(project("zally-rule-api"))

    testCompile("net.jadler:jadler-core:$jadlerVersion")
    testCompile("net.jadler:jadler-jdk:$jadlerVersion")
    testCompile("net.jadler:jadler-junit:$jadlerVersion")
    testCompile("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testCompile("org.assertj:assertj-core:3.11.0")
    testCompile("com.jayway.jsonpath:json-path-assert:2.4.0")
    testCompile("org.mockito:mockito-core:2.23.4")
}

jacoco {
    toolVersion = "0.8.2"
}

tasks.register("downloadJsonSchema", Download::class) {
    src("http://json-schema.org/draft-04/schema")
    dest("$rootDir/src/main/resources/schemas/json-schema.json")
    onlyIfModified(true)
}

tasks.register("downloadSwaggerSchema", Download::class) {
    src("http://swagger.io/v2/schema.json")
    dest("$rootDir/src/main/resources/schemas/swagger-schema.json")
    onlyIfModified(true)
}

tasks.bootRun {
    jvmArgs = listOf("-Dspring.profiles.active=dev")
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
    }
}

tasks.jar {
    archiveBaseName.set(project.name)
    archiveVersion.set(version)
}

tasks.processResources {
    dependsOn("downloadJsonSchema")
    dependsOn("downloadSwaggerSchema")
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
    gradleVersion = "5.3.1"
}
