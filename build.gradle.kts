import java.net.URI
import java.security.MessageDigest

plugins { java }

group = "com.mira"
version = "0.1.28-1.18.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

val miraCoreVersion = "0.6.1-1.18.2"
val miraCoreSha256 = "91a1aacad3f1249877afdd5381d83998c33251bf08a30711f2a15c580feaee39"
val miraCoreJar = layout.projectDirectory.file("libs/MiraCore_1.18.2.jar").asFile

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(file.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
}

val downloadMiraDependencies by tasks.registering {
    doLast {
        if (miraCoreJar.exists() && sha256(miraCoreJar) == miraCoreSha256) return@doLast
        miraCoreJar.parentFile.mkdirs()
        URI("https://github.com/FiveSOCE/Mira-core/releases/download/dev-1.18.2/MiraCore_1.18.2.jar").toURL().openStream().use { input -> miraCoreJar.outputStream().use { output -> input.copyTo(output) } }
        check(sha256(miraCoreJar) == miraCoreSha256) { "Downloaded MiraCore JAR failed SHA-256 verification" }
    }
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("1.18.2-R0.1-SNAPSHOT")
val compileJavaVersion = providers.gradleProperty("compileJavaVersion").map(String::toInt).orElse(17)
val bytecodeJavaVersion = providers.gradleProperty("bytecodeJavaVersion").map(String::toInt).orElse(17)

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("net.luckperms:api:5.4")
    compileOnly(files(miraCoreJar))
    compileOnly("me.clip:placeholderapi:2.11.6")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(compileJavaVersion.get())) }

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadMiraDependencies)
    options.encoding = "UTF-8"
    options.release.set(bytecodeJavaVersion.get())
}

tasks.test { useJUnitPlatform() }

tasks.jar { archiveFileName.set("MiraItems_1.18.2.jar") }
