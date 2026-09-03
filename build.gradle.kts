import java.net.URI
import java.security.MessageDigest

plugins { java }

group = "com.mira"
version = "0.1.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val miraCoreVersion = "0.2.0"
val miraCoreSha256 = "66433a266a76088d2a2de90ac1beb1a5a183c26891ee8f394827b47830195b03"
val miraCoreJar = layout.projectDirectory.file("libs/MiraCore-$miraCoreVersion.jar").asFile

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(file.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
}

val downloadMiraDependencies by tasks.registering {
    doLast {
        if (miraCoreJar.exists() && sha256(miraCoreJar) == miraCoreSha256) return@doLast
        miraCoreJar.parentFile.mkdirs()
        URI("https://github.com/FiveSOCE/MIra-core/releases/download/v$miraCoreVersion/MiraCore-$miraCoreVersion.jar").toURL().openStream().use { input -> miraCoreJar.outputStream().use { output -> input.copyTo(output) } }
        check(sha256(miraCoreJar) == miraCoreSha256) { "Downloaded MiraCore JAR failed SHA-256 verification" }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files(miraCoreJar))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadMiraDependencies)
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test { useJUnitPlatform() }

tasks.jar { archiveFileName.set("MiraItems-${project.version}.jar") }
