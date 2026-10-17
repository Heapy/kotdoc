package io.heapy.kotdoc.crypto
object Md5JUnitMain {
 @JvmStatic fun main(args:Array<String>){
  val t=CryptoMd5Test();var count=0
  for(stream in listOf(t.raw(),t.counterWrap(),t.aliases(),t.rounds(),t.boundaries(),t.publicOriginal(),t.literalOriginal(),t.demandOriginal(),t.originalPropertyFamilies()))stream.use{it.forEach{test->try{test.executable.execute();count++}catch(e:Throwable){throw AssertionError(test.displayName,e)}}}
  t.ownershipAndFailureSharing();count++;println("PASS $count isolated assertions (pins checked separately before install)")
 }
}
