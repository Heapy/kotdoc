package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private fun charsText(points:IntArray)=String(points,0,points.size)
private fun quote(value:String)=io.heapy.kotdoc.formats.HaskellString.quote(value)
private fun showChars(value:ManyShowString):String=buildString{for(c in value)appendCodePoint(c)}
private fun charsString(s:String)=ManyShowRuntime.text(s,GenericList.Companion::empty)
private fun <A> typedRead(d:ReadDictionary<A>)=object:ManyElementRead<A>{
 override fun readPrec()=ManyReadPrec.fromReadS<A>{p,s->GenericList.view(d.readPrec(p,s.characters()).map{(v,r)->ManyReadAlternative({v}){ManyReadInput(r)}})}
 override fun readListPrec()=ManyReadPrec.fromReadS<GenericList<A>>{p,s->GenericList.view(d.readListPrec(p,s.characters()).map{(v,r)->ManyReadAlternative({GenericList.view(v)}){ManyReadInput(r)}})}
}
private fun <A> stringShow(render:(A)->String)=object:ManyElementShow<A>{override fun showsPrec(precedence:Long,value:()->A,suffix:()->ManyShowString)=ManyShowRuntime.text(render(value()),suffix)}
private fun <A> readRun(reader:ManyElementRead<A>,show:ManyElementShow<A>,op:String,p:Long,input:ManyReadInput):String {
 val d=ManyReadDictionary(reader);val sh=ManyShowDictionary(show)
 fun many(x:Many<A>)=showChars(sh.show{x})
 fun list(xs:GenericList<Many<A>>)=xs.joinToString(",","[","]",transform=::many)
 return when(op){
 "read"->many(d.read(input))
 "readList","readListPrec"->{val rs=if(op=="readList")d.readList(input)else d.readListPrec(p,input);rs.joinToString(",","[","]"){"("+list(it.value())+","+quote(charsText(it.remainder().characters().points()))+")"}}
 else->{val rs=if(op=="readsPrec")d.readsPrec(p,input)else d.readPrec(p,input);rs.joinToString(",","[","]"){"("+many(it.value())+","+quote(charsText(it.remainder().characters().points()))+")"}}
 }
}
internal object ManyDerivedReadFixtures {
 val intReader=typedRead(PandocRead.Int);val intShow=stringShow<Long>{it.toString()}
 val charReader=typedRead(PandocRead.Char)
 val charShow=object:ManyElementShow<Int>{override fun showsPrec(precedence:Long,value:()->Int,suffix:()->ManyShowString)=ManyShowRuntime.text("'"+value().toChar()+"'",suffix);override fun showList(value:()->GenericList<Int>,suffix:()->ManyShowString)=ManyShowRuntime.text(quote(charsText(value().toIntArray())),suffix)}
 val textReader=object:ManyElementRead<GenericList<Int>>{override fun readPrec()=charReader.readListPrec();override fun readListPrec()=ManyReadPrec.list(readPrec())}
 val textShow=stringShow<GenericList<Int>>{quote(charsText(it.toIntArray()))}
 val tag=object:ManyElementRead<Long>{override fun readPrec()=ManyReadPrec.fromReadS<Long>{p,s->GenericList.view(listOf(ManyReadAlternative({p}){s}))};override fun readListPrec()=ManyReadPrec.fromReadS<GenericList<Long>>{p,s->ManyReadPrec.token("TAG").results(p,s).let{rs->GenericList.defer{when(val x=rs.step()){GenericList.Step.Nil->GenericList.empty();is GenericList.Step.Cons->GenericList.cons({ManyReadAlternative({GenericList.view(listOf(p))}){x.head().remainder()}}){GenericList.empty()}}}}}}
 val parallel=object:ManyElementRead<Long>{override fun readPrec()=ManyReadPrec.pure{1L}.or(ManyReadPrec.get().map{2L});override fun readListPrec()=ManyReadPrec.pure{GenericList.view(listOf(1L))}.or(ManyReadPrec.get().map{GenericList.view(listOf(2L))})}
 val duplicate=object:ManyElementRead<Long>{override fun readPrec()=ManyReadPrec.fromReadS<Long>{p,s->GenericList.view(listOf(ManyReadAlternative({p}){s},ManyReadAlternative({p+1}){s}))};override fun readListPrec()=ManyReadPrec.fromReadS<GenericList<Long>>{p,s->var n=0;while(s.point(n)==32)n++;GenericList.view(listOf(ManyReadAlternative({GenericList.view(listOf(p))}){s.drop(n)},ManyReadAlternative({GenericList.view(listOf(p+1))}){s.drop(n)}))}}

 val readsOnly=object:ManyElementRead<Long>{override fun readsPrec(precedence:()->Long,input:ManyReadInput):GenericList<ManyReadAlternative<Long>> = GenericList.defer {
  when(val token=ManyReadPrec.token("R").results(0,input).step()) {
   GenericList.Step.Nil->GenericList.empty()
   is GenericList.Step.Cons->GenericList.view(listOf(ManyReadAlternative(precedence){token.head().remainder()},ManyReadAlternative({precedence()+1}){token.head().remainder()}))
  }
 }}
 val readListOnly=object:ManyElementRead<Long>{override fun readPrec():ManyReadPrec<Long> = error("unused-readPrec");override fun readList(input:ManyReadInput):GenericList<ManyReadAlternative<GenericList<Long>>> {var n=0;while(input.point(n)==32)n++;return GenericList.cons({ManyReadAlternative({GenericList.view(listOf(1L))}){input.drop(n)}}){GenericList.empty()}}}
 val readConflict=object:ManyElementRead<Long>{override fun readPrec():ManyReadPrec<Long> = error("unused-readPrec");override fun readList(input:ManyReadInput):GenericList<ManyReadAlternative<GenericList<Long>>> = error("unused-readList");override fun readListPrec()=ManyReadPrec.pure{GenericList.view(listOf(2L))}}
 val readPrecOnly=object:ManyElementRead<Long>{override fun readsPrec(precedence:()->Long,input:ManyReadInput):GenericList<ManyReadAlternative<Long>> = error("unused-readsPrec");override fun readPrec()=ManyReadPrec.token("R").map{3L}}
 fun execute(kind:String,op:String,p:Long,input:ManyReadInput):String = when(kind){
  "int"->readRun(intReader,intShow,op,p,input)
  "char"->readRun(charReader,charShow,op,p,input)
  "text"->readRun(textReader,textShow,op,p,input)
  "tag"->readRun(tag,stringShow<Long>{"ReadTag $it"},op,p,input)
  "parallel"->readRun(parallel,stringShow<Long>{"Parallel $it"},op,p,input)
  "duplicate"->readRun(duplicate,stringShow<Long>{"Duplicate $it"},op,p,input)
  "reads-only"->readRun(readsOnly,stringShow<Long>{"ReadsOnly $it"},op,p,input)
  "read-list-only"->readRun(readListOnly,stringShow<Long>{"ReadListOnly $it"},op,p,input)
  "read-conflict"->readRun(readConflict,stringShow<Long>{"ReadConflict $it"},op,p,input)
  "read-prec-only"->readRun(readPrecOnly,stringShow<Long>{"ReadPrecOnly $it"},op,p,input)
  else->error("Unknown element dictionary $kind")
 }
}

fun replayManyDerivedRead(only:Int?=null){ val inputs=ManyDerivedProof.lines("read-inputs.jsonl");val outputs=ManyDerivedProof.lines("read-native.jsonl");var count=0
 for(i in (only?.let{listOf(it)}?:inputs.indices.toList())){val r=Json.parseToJsonElement(inputs[i]).jsonObject;val expected=Json.parseToJsonElement(outputs[i]);val p=r.getValue("precedence").jsonPrimitive.long;val op=r.getValue("operation").jsonPrimitive.content;val kind=r.getValue("kind").jsonPrimitive.content;val input=ManyReadInput(io.heapy.kotdoc.layout.CharacterString(r.getValue("input").jsonArray.map{it.jsonPrimitive.int}.toIntArray()))
 val actual=try{val v=ManyDerivedReadFixtures.execute(kind,op,p,input);buildJsonObject{put("result",v)}}catch(e:IllegalArgumentException){buildJsonObject{put("exception",e.message!!.lineSequence().first())}}
 check(actual==expected){"Read row$i $kind/$op/$p: $actual != $expected"};count++
 }
 if(only==null)println("Many derived fullRead native replay PASS $count")
}

fun main()=replayManyDerivedRead()
