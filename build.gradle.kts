plugins {
    java
    id("org.springframework.boot") version "3.3.6"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.charge0315"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val frontendDir = file("src/main/frontend")

val npmCommand = if (System.getProperty("os.name").lowercase().contains("win")) {
    "npm.cmd"
} else {
    "npm"
}

val frontendNpmInstall = tasks.register<Exec>("frontendNpmInstall") {
    workingDir = frontendDir
    commandLine(npmCommand, "install")
}

val frontendBuild = tasks.register<Exec>("frontendBuild") {
    dependsOn(frontendNpmInstall)
    workingDir = frontendDir
    commandLine(npmCommand, "run", "build")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(frontendBuild)
    from(frontendDir.resolve("dist")) {
        into("static")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
