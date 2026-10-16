package io.heapy.kotdoc.errors

/** JVM carrier for the original GHC IOError record; rendering stays deferred. */
open class GhcIOException(val error: GhcIoError): java.io.IOException() {
    override val message: String get()=error.toString()
}
