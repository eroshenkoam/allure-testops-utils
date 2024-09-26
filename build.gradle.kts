import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.gradle.kotlin.dsl.support.unzipTo
import org.gradle.internal.os.OperatingSystem;

plugins {
    java
    application
    id("com.bmuschko.docker-remote-api") version "7.3.0"
    id("org.graalvm.buildtools.native") version "0.9.28"
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
    from("amazoncorretto:17")

    addFile("${project.name}-${project.version}/bin", "/var/lib/${project.name}/bin")
    addFile("${project.name}-${project.version}/lib", "/var/lib/${project.name}/lib")

    runCommand("chmod +x /var/lib/${project.name}/bin/${project.name}")
    runCommand("ln -s /var/lib/${project.name}/bin/${project.name} /usr/bin/${project.name}")

    entryPoint("/usr/bin/${project.name}")
    workingDir("/opt/allure-testops")
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
    annotationProcessor("org.projectlombok:lombok:1.18.28")
    compileOnly("org.projectlombok:lombok:1.18.28")

    annotationProcessor("info.picocli:picocli-codegen:4.1.4")
    implementation("info.picocli:picocli:4.1.4")

    implementation("org.springframework.ldap:spring-ldap-core:2.3.8.RELEASE")

    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("org.freemarker:freemarker:2.3.32")

    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    implementation("joda-time:joda-time:2.12.5")

    implementation("io.qameta.allure:allure-model:2.24.0")
    implementation("io.qameta.allure:allure-ee-client:3.53.0")
}

graalvmNative {
    toolchainDetection.set(true)
    binaries {
        named("main") {
            useFatJar.set(true)
            verbose.set(true)

            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(17))
            })
            requiredVersion.set("23.0.2")

            metadataRepository {
                enabled.set(true)
                version.set("0.3.5")
            }

            configurationFileDirectories.from(
                    file("src/main/resources/META-INF/native-image")
            )

            var osArch = System.getProperty("os.arch")
            println(osArch)
            var gcFlag = ""
            if (OperatingSystem.current().isMacOsX() || OperatingSystem.current().isLinux()) {
                gcFlag = "--gc=G1"
            }
            buildArgs.addAll(
                    "-march=native",
                    "-R:MinHeapSize=400m",
                    "-R:MaxHeapSize=400m",
                    "-H:+AddAllCharsets",
                    gcFlag,
            )
            jvmArgs.addAll(
                    "-Dfile.encoding=UTF-8",
                    "-Dsun.jnu.encoding=UTF-8",
                    "-Djava.awt.headless=true",
                    "-Djava.net.preferIPv4Stack=true",
                    "-Djava.net.preferIPv4Addresses=true"
            )
        }
    }
}

