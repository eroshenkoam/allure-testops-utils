import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.gradle.kotlin.dsl.support.unzipTo
import org.gradle.internal.os.OperatingSystem;

plugins {
    java
    application
    id("com.bmuschko.docker-remote-api") version "9.4.0"
    id("org.graalvm.buildtools.native") version "0.9.28"
}

group = "io.github.eroshenkoam.allure"
version = version

application {
    mainClass.set("io.github.eroshenkoam.allure.AllureTestOpsUtils")
}

val prepareDockerOutput by tasks.registering {
    group = "Build"
    dependsOn(tasks.distZip)

    doLast {
        unzipTo(file("build/docker"), tasks.distZip.get().archiveFile.get().asFile)
    }
}

val prepareDockerfile by tasks.registering(Dockerfile::class) {
    group = "Build"
    dependsOn(prepareDockerOutput)
    destFile.set(project.file("build/docker/Dockerfile"))
    from("amazoncorretto:17")

    addFile("${project.name}-${project.version}/bin", "/var/lib/${project.name}/bin")
    addFile("${project.name}-${project.version}/lib", "/var/lib/${project.name}/lib")

    runCommand("chmod +x /var/lib/${project.name}/bin/${project.name}")
    runCommand("ln -s /var/lib/${project.name}/bin/${project.name} /usr/bin/${project.name}")

    entryPoint("/usr/bin/${project.name}")
    workingDir("/opt/allure-testops")
}

val buildDockerImage by tasks.registering(DockerBuildImage::class) {
    group = "Build"
    dependsOn(prepareDockerfile)
    inputDir.set(project.file("build/docker"))
    images.add("allure/${project.name}:${project.version}")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.atlassian.com/content/groups/public/")
    maven("https://dl.qameta.io/artifactory/maven/")
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    compileOnly("org.projectlombok:lombok:1.18.30")

    annotationProcessor("info.picocli:picocli-codegen:4.7.5")
    implementation("info.picocli:picocli:4.7.5")

    implementation("org.springframework.ldap:spring-ldap-core:3.2.0")

    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("org.freemarker:freemarker:2.3.32")

    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("joda-time:joda-time:2.12.5")

    implementation("com.google.code.findbugs:jsr305:3.0.2")

    implementation("io.qameta.allure:allure-model:2.24.0")
}
