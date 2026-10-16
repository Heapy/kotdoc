package io.heapy.kotdoc.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlinx.serialization.json.*

/** All20 original property families and11 literal units use independent original-module inputs/results. */
class CryptoP256OriginalSpecTest {
    @TestFactory fun originalSpecFamilies()=P256PublicFixtures.observations(true)
    @Test fun completeOriginalSuiteEvidence(){
        val m=P256PublicFixtures.manifest;val native=m.getValue("originalSuite").jsonObject
        assertEquals(31,native.getValue("examples").jsonPrimitive.int);assertEquals(0,native.getValue("failures").jsonPrimitive.int)
        assertEquals(20,native.getValue("properties").jsonPrimitive.int);assertEquals(4000,native.getValue("propertyChecks").jsonPrimitive.int)
        assertEquals(400,m.getValue("generators").jsonArray.size)
        assertEquals(m.getValue("cases").jsonPrimitive.int,P256PublicFixtures.lines.size-1)
        val groups=m.getValue("groups").jsonObject
        assertEquals(5,groups.getValue("original-valid-point-literal-units").jsonPrimitive.int)
        assertEquals(5,groups.getValue("original-valid-point-edge-units").jsonPrimitive.int)
        assertEquals(1,groups.getValue("original-point-add-literal-unit").jsonPrimitive.int)
    }
}
