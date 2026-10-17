import io.heapy.kotdoc.ast.*
fun main(){
 val expected=mapOf("x" to MetaValue.MetaBool(true),"y" to MetaValue.MetaString("value"))
 val actual=MetadataMap.fromMap(expected)
 println("symmetric equality: ${actual==expected&&expected==actual}")
 println("equal map hashes: ${actual.hashCode()==expected.hashCode()}")
 println("equal entry sets: ${actual.entries==expected.entries&&expected.entries==actual.entries}")
 println("HashSet lookup: ${hashSetOf<Map<String,MetaValue>>(actual).contains(expected)}")
}
