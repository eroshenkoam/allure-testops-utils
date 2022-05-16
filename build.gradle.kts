import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.gradle.kotlin.dsl.support.unzipTo

plugins {
    java
    application
    id("com.bmuschko.docker-remote-api") version "6.6.1"
}

group = "io.github.eroshenkoam.allure"
version = version

application {
    mainClassName = "io.github.eroshenkoam.allure.AllureTestOpsUtils"
}

val prepareDockerOutput by tasks.creating {
    group = "Build"
    dependsOn(tasks.distZip)

    doLast {
        unzipTo(file("build/docker"), tasks.distZip.get().archiveFile.get().asFile)
    }
}

val prepareDockerfile by tasks.creating(Dockerfile::class) {
    group = "Build"
    dependsOn(prepareDockerOutput)
    destFile.set(project.file("build/docker/Dockerfile"))
    from("adoptopenjdk/openjdk11:alpine-jre")

    addFile("${project.name}-${project.version}/bin", "/var/lib/${project.name}/bin")
    addFile("${project.name}-${project.version}/lib", "/var/lib/${project.name}/lib")

    runCommand("chmod +x /var/lib/${project.name}/bin/${project.name}")
    runCommand("ln -s /var/lib/${project.name}/bin/${project.name} /usr/bin/${project.name}")

    entryPoint("/usr/bin/${project.name}")
    defaultCommand("sync")
}

val buildDockerImage by tasks.creating(DockerBuildImage::class) {
    group = "Build"
    dependsOn(prepareDockerfile)
    inputDir.set(project.file("build/docker"))
    images.set(setOf(
            "allure/${project.name}:${project.version}"
    ))
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.atlassian.com/content/groups/public/")
    maven("https://dl.qameta.io/artifactory/maven/")
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.12")
    compileOnly("org.projectlombok:lombok:1.18.12")

    annotationProcessor("info.picocli:picocli-codegen:4.1.4")
    implementation("info.picocli:picocli:4.1.4")

    implementation("org.springframework.ldap:spring-ldap-core:2.3.7.RELEASE")

    implementation("com.squareup.retrofit2:converter-jackson:2.7.2")
    implementation("com.squareup.retrofit2:retrofit:2.7.2")

    implementation("joda-time:joda-time:2.10.14")

    implementation("io.qameta.allure:allure-ee-client:3.40.3")
}
