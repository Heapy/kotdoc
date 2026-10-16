package io.heapy.kotdoc.x509

import com.sun.jna.Native
import com.ibm.icu.lang.UCharacter
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object X509SystemNativeChild {
    @JvmStatic fun main(args: Array<String>) {
        val certificates=X509System.getSystemCertificateStore().certificates().toList()
        val hash=MessageDigest.getInstance("SHA-256")
        for(cert in certificates) hash.update(cert.encodeSignedObject())
        println(buildJsonObject {put("count",certificates.size);put("sha256",hash.digest().joinToString("") {(it.toInt() and 255).toString(16).padStart(2,'0')})})
    }
}
class X509SystemNativeTest {
    private fun child(bundle: File?): JsonObject {
        val classes=listOf(X509SystemNativeChild::class.java,X509System::class.java,JsonElement::class.java,
            kotlinx.serialization.KSerializer::class.java,Unit::class.java,Native::class.java,UCharacter::class.java)
        val classpath=classes.map {File(it.protectionDomain.codeSource.location.toURI()).absolutePath}.distinct().toMutableList()
        System.getProperty("kotdoc.root")?.let {classpath.add(File(it,"core/src/main/resources").absolutePath)}
        val java=File(System.getProperty("java.home"),"bin/java").absolutePath
        val builder=ProcessBuilder(java,"-cp",classpath.joinToString(File.pathSeparator),X509SystemNativeChild::class.java.name).redirectErrorStream(true)
        val environment=builder.environment()
        for(k in listOf("SSL_CERT_FILE","SSL_CERT_DIR","SYSTEM_CERTIFICATE_PATH")) environment.remove(k)
        if(bundle!=null) {environment["SSL_CERT_FILE"]=bundle.absolutePath;environment["SSL_CERT_DIR"]=bundle.absolutePath+".missing"}
        val process=builder.start()
        try {
            assertTrue(process.waitFor(45,TimeUnit.SECONDS),"native CA loader child timed out")
            val output=process.inputStream.bufferedReader().readText()
            assertEquals(0,process.exitValue(),output)
            return Json.parseToJsonElement(output.trim().lineSequence().last()).jsonObject
        } finally {if(process.isAlive) process.destroyForcibly()}
    }
    @Test fun actualOperatingSystemStore() {
        val result=child(null)
        assertTrue(result.getValue("count").jsonPrimitive.int>0,result.toString())
        assertEquals(64,result.getValue("sha256").jsonPrimitive.content.length)
        println("Native CA snapshot: $result")
    }
    @Test fun nativeEnvironmentOverrideAndUnicodeFilesystemPath() {
        val raw=X509SystemFixtures.certificate()
        val dir=Files.createTempDirectory("kotdoc-system-env-")
        val bundle=dir.resolve("bundle_Русский_😀.pem")
        try {
            Files.writeString(bundle,"-----BEGIN CERTIFICATE-----\n"+java.util.Base64.getEncoder().encodeToString(raw)+"\n-----END CERTIFICATE-----\n")
            val result=child(bundle.toFile())
            assertEquals(1,result.getValue("count").jsonPrimitive.int)
            val hash=MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {(it.toInt() and 255).toString(16).padStart(2,'0')}
            assertEquals(hash,result.getValue("sha256").jsonPrimitive.content)
        } finally {Files.deleteIfExists(bundle);Files.deleteIfExists(dir)}
    }
}
