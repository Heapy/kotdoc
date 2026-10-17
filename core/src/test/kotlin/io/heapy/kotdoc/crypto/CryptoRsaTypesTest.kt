package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.*
class CryptoRsaTypesTest {
 private fun source(name:String)=Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/crypto-rsa-types/$name.json.gz")!!).bufferedReader().use{it.readText()})
 private fun cases(name:String,observe:(JsonObject)->JsonElement)=source(name).jsonArray.mapIndexed{i,v->DynamicTest.dynamicTest("$name/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun methods()=cases("Observations",RsaTypesReplay::observe)
 @TestFactory fun dataMethods()=cases("DataObservations",RsaTypesDataReplay::observe)
 @TestFactory fun typeIdentity()=cases("TypeInfoObservations",RsaTypesReplay::observe)
 @TestFactory fun equality()=cases("EqObservations",RsaTypesReplay::observe)
 @TestFactory fun streams()=cases("StreamObservations",RsaTypesReplay::observe)
 @TestFactory fun projections()=cases("ProjectedObservations",RsaTypesReplay::observe)
 @TestFactory fun members()=cases("MemberObservations",RsaTypesReplay::observe)
 @TestFactory fun generic():java.util.stream.Stream<DynamicTest>{val rows=source("GenericObservations").jsonArray;return rows.mapIndexed{i,row->DynamicTest.dynamicTest("Generic/$i"){RsaTypesGenericProof.checkRow(rows,row.jsonObject)}}.stream()}
 @TestFactory fun originalSecrecySix():java.util.stream.Stream<DynamicTest>{val s=source("SecrecyCases").jsonObject;return s.getValue("rows").jsonArray.mapIndexed{i,r->DynamicTest.dynamicTest("original RSA Secrecy/$i"){RsaTypesSecrecyProof.checkCase(s,r.jsonObject)}}.stream()}
 @Test fun genericImpreciseBoundaryIsExactNamedPair(){val rows=source("GenericObservations").jsonArray;Assertions.assertEquals(2,rows.count{!RsaTypesGenericProof.checkRow(rows,it.jsonObject)})}
 @Test fun explicitDefinedRecordBridges(){val s=source("SecrecyCases").jsonObject;val p=s.getValue("public").jsonObject;val fields=s.getValue("secrets").jsonArray.map{it.jsonPrimitive.content.toBigInteger()};val public=CryptoRsaTypes.PublicKey(p.getValue("size").jsonPrimitive.long,p.getValue("n").jsonPrimitive.content.toBigInteger(),p.getValue("e").jsonPrimitive.content.toBigInteger());val private=CryptoRsaTypes.PrivateKey(public,fields[0],fields[1],fields[2],fields[3],fields[4],fields[5]);val pair=CryptoRsaTypes.KeyPair(private)
  Assertions.assertEquals(public,CryptoRsaTypes.toPublicKey(pair));Assertions.assertSame(private,CryptoRsaTypes.toPrivateKey(pair));Assertions.assertEquals(public.size,CryptoRsaTypes.privateSize(private));Assertions.assertEquals(public.n,CryptoRsaTypes.privateN(private));Assertions.assertEquals(public.e,CryptoRsaTypes.privateE(private));Assertions.assertEquals(private,CryptoRsaTypes.PrivateKey.fromX509(private.asX509()));Assertions.assertEquals(public,CryptoRsaTypes.PublicKey.fromX509(public.asX509()))
 }
 @TestFactory fun currentPins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/rsa-types-reference/CurrentPins.json").readText()).jsonObject.map{(path,value)->DynamicTest.dynamicTest(path){Assertions.assertEquals(value.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
