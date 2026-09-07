// Deliberately its own Gradle subproject, with none of the root
// project's plugins applied. Gatling bundles its own (newer) Netty;
// the root project's io.spring.dependency-management plugin pins
// Netty to whatever Spring Boot's BOM wants (older, for Kafka), and
// that pin turned out to apply project-wide with no supported way to
// scope it out of just the Gatling configurations -- so the clean fix
// is to just not share a Gradle project (and its dependency
// resolution) with the app at all.
plugins {
    java
    id("io.gatling.gradle") version "3.15.1.3"
}

repositories {
    mavenCentral()
}
