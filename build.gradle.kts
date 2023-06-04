import java.util.Properties

val localProperties = Properties()
localProperties.load(project.rootProject.file("gradle-local.properties").inputStream())

plugins {
    kotlin("jvm") version "1.8.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.ontotext.graphdb:graphdb-runtime:10.2.0")
//    implementation("com.ontotext.graphdb.plugins:proof-plugin:1.0-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}


tasks.register<Copy>("copyPlugin") {
    if (properties["graphdb.home"] == null)
        throw Error("Property graphdb.home is not defined ")

    val source = layout.buildDirectory.file("libs/${project.name}-${project.version}.jar")
    val dest = "${properties["graphdb.home"]}/lib/plugins/${project.name}/"

    from(source)
    into(dest)
}



