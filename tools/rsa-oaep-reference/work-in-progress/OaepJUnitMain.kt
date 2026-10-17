package io.heapy.kotdoc.crypto
object OaepJUnitMain {
 @JvmStatic fun main(args:Array<String>){val t=CryptoRsaOaepTest();var n=0;for(s in listOf(t.ordinary(),t.demand(),t.pairedErrors(),t.pureRng(),t.pureRngRetry(),t.callbackRng(),t.callbackRngRetry(),t.virtualBoundaries(),t.genericAccess()))s.use{it.forEach{d->try{d.executable.execute();n++}catch(e:Throwable){throw AssertionError(d.displayName,e)}}};t.ownedInputSnapshotsAndSharedResult();println("PASS ${n+1} isolated assertions; pins separate")}
}
