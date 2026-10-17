package io.heapy.kotdoc.crypto
object HmacJUnitMain {
 @JvmStatic fun main(args:Array<String>){val t=CryptoHmacTest();var count=0;for(s in listOf(t.originalObservations(),t.originalDictionary(),t.originalDictionaryExtra(),t.originalAccess(),t.originalUnit(),t.originalBorrow(),t.originalLiterals(),t.originalPropertyFamilies()))s.use{it.forEach{test->try{test.executable.execute();count++}catch(e:Throwable){throw AssertionError(test.displayName,e)}}};t.immutableOwnershipAndConstructor();t.sharedSynchronousFailureAndSuccess();count+=2;println("PASS $count isolated HMAC assertions; pins separate")}
}
