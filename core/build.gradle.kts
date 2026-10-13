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
    // The growing upstream suite exceeds Gradle's default 512 MiB worker heap.
    maxHeapSize = "1g"
    systemProperty("jna.tmpdir", layout.buildDirectory.dir("jna").get().asFile.absolutePath)
    systemProperty("kotdoc.root", rootProject.projectDir.absolutePath)
}
