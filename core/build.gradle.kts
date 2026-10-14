plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    implementation("com.ibm.icu:icu4j:78.1")
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
    // JUnit retains descriptors for the full independent upstream corpus.
    // Allow headroom for more than 548,000 upstream observations and their full
    // expected outputs; the build daemon has its own budget in gradle.properties.
    maxHeapSize = "2g"
    systemProperty("jna.tmpdir", layout.buildDirectory.dir("jna").get().asFile.absolutePath)
    systemProperty("kotdoc.root", rootProject.projectDir.absolutePath)
}

// Preserve upstream notices when distributing the standalone core JAR.
tasks.jar {
    from(rootProject.file("licenses")) { into("META-INF/LICENSES") }
    from(rootProject.file("LICENSE")) { into("META-INF/LICENSES"); rename { "kotdoc-GPL-3.0.txt" } }
}
