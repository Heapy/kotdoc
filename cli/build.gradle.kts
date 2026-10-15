plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
application { mainClass = "io.heapy.kotdoc.cli.MainKt" }

distributions {
    main {
        contents {
            from(rootProject.file("LICENSE"))
            from(rootProject.file("licenses")) { into("licenses") }
        }
    }
}
