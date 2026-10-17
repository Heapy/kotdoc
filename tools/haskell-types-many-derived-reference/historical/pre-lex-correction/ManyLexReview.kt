import io.heapy.kotdoc.ast.*
fun main() {
 val dict=ManyReadDictionary(object:ManyElementRead<Int>{override fun readPrec()=ManyReadPrec.fail<Int>()})
 for(s in listOf("Many 1","Many \"a","Many 'a","Many {unMany = fromList 1")) {
  fun chars(i:Int):GenericList<Int> = GenericList.defer {if(i==s.length) error("lex-tail") else GenericList.cons({s[i].code}){chars(i+1)}}
  try {println("$s: ${dict.readsPrec(0,ManyReadInput{chars(0)}).step() is GenericList.Step.Nil}")}catch(e:Throwable){println("$s: ${e.message}")}
 }
}
