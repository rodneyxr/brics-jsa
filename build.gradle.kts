plugins {
    java
    `maven-publish`
}

group = "dk.brics.string"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // https://mvnrepository.com/artifact/log4j/log4j
    implementation("log4j:log4j:1.2.14")
    // https://mvnrepository.com/artifact/ca.mcgill.sable/soot
    implementation("ca.mcgill.sable:soot:4.1.0")
    // https://github.com/rodneyxr/brics-automaton
    implementation("com.github.rodneyxr:brics-automaton:gradle-SNAPSHOT")
}

publishing {
    publications {
        create<MavenPublication>("brics-jsa") {
            artifact(tasks.jar)
        }
    }
}