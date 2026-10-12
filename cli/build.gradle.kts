plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }
dependencies { implementation(project(":core")) }
application { mainClass = "io.heapy.kotdoc.cli.MainKt" }

distributions {
    main {
        contents {
            from(rootProject.file("LICENSE"))
            from(rootProject.file("licenses")) { into("licenses") }
        }
    }
}
