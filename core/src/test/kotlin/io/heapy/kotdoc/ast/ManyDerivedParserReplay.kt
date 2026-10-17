package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private fun <A> result(p:ManyReadPrec<A>,prec:()->Long={error("prec")},s:ManyReadInput=ManyReadInput{error("input")}):A = when(val x=p.resultsLazy(prec,s).step()){GenericList.Step.Nil->error("noresult");is GenericList.Step.Cons->x.head().value()}
private fun str(prefix:String,tail:()->GenericList<Int>):GenericList<Int> {val points=prefix.codePoints().toArray()
 fun at(i:Int):GenericList<Int> = GenericList.defer{if(i==points.size)tail()else GenericList.cons({points[i]}){at(i+1)}}
 return at(0)
}
private fun repeatChar(c:()->Int):GenericList<Int> = GenericList.cons(c){repeatChar(c)}
private fun prefix(xs:GenericList<Int>,n:Int):String {var rest=xs;val b=StringBuilder();repeat(n){i->when(val x=rest.step()){GenericList.Step.Nil->return b.toString();is GenericList.Step.Cons->{b.appendCodePoint(x.head());if(i+1<n)rest=x.tail()}}};return b.toString()}
private fun quoteString(s:String)=io.heapy.kotdoc.formats.HaskellString.quote(s)
fun replayManyDerivedParser(only:String?=null){
 val emptyRead=ManyReadDictionary(object:ManyElementRead<Int>{override fun readPrec():ManyReadPrec<Int> = error("unused-readPrec");override fun readListPrec()=ManyReadPrec.pure{GenericList.empty<Int>()}})
 val padded=ManyReadDictionary(object:ManyElementRead<Int>{override fun readPrec():ManyReadPrec<Int> = error("unused-readPrec");override fun readListPrec()=ManyReadPrec.get().flatMap{ManyReadPrec.get().map{c->GenericList.cons(c){GenericList.empty()}}}})
 val badList=ManyReadDictionary(object:ManyElementRead<Int>{override fun readPrec():ManyReadPrec<Int> = error("unused-readPrec");override fun readListPrec()=ManyReadPrec.pure{GenericList.defer<Int>{error("listroot")}}})
 val input="Many {unMany = fromList }"
 fun partialInput()=ManyReadInput{str("Many {unMany = fromList "){GenericList.cons({error("char")}){str("}"){GenericList.empty()}}}}
 fun firstEmpty(tail:()->GenericList<Int>)=when(val x=emptyRead.readsPrec(0,ManyReadInput{str(input,tail)}).step()){GenericList.Step.Nil->error("noresult");is GenericList.Step.Cons->x.head()}
 fun finalParser()=ManyReadPrec.fromReadSLazy<Int>{_,_->GenericList.view(listOf(ManyReadAlternative({1}){ManyReadInput("z")},ManyReadAlternative({2}){ManyReadInput("a")}))}
 fun parserRows(p:ManyReadPrec<Int>)=p.results(0,ManyReadInput("xy")).joinToString(",","[","]"){"(${it.value()},${quoteString(String(it.remainder().characters().points(),0,it.remainder().characters().points().size))})"}
 var count=0
 for((name,expected) in ManyDerivedProof.named("parser-demand-native.jsonl",only)){val actual=try {
 val v=when(name){
 "final-ignore-remainder"->result(ManyReadPrec.fromReadSLazy<Int>{_,_->GenericList.cons({ManyReadAlternative({1}){error("remainder")}}){GenericList.empty()}}.map{7},{0},ManyReadInput("")).toString()
 "final-demand-remainder"->{val p=ManyReadPrec.fromReadSLazy<Int>{_,_->GenericList.cons({ManyReadAlternative({1}){error("remainder")}}){GenericList.empty()}}.map{7};p.results(0,ManyReadInput(""))[0].remainder();"False"}
 "final-ignore-value"->result(ManyReadPrec.fromReadSLazy<Int>{_,s->GenericList.cons({ManyReadAlternative<Int>({error("value")}){s}}){GenericList.empty()}}.map{7},{0},ManyReadInput("")).toString()
 "pure-return-list-root"->{result(ManyReadPrec.pure{GenericList.defer<Int>{error("resultroot")}},{0},ManyReadInput(""));"7"}
 "pure-return-list-head"->result(ManyReadPrec.pure{GenericList.cons({1}){error("tail")}},{0},ManyReadInput(""))[0].toString()
 "pure-return-list-spine"->{result(ManyReadPrec.pure{GenericList.cons<Int>({error("head")}){error("tail")}},{0},ManyReadInput(""));"7"}
 "pure-ignore-input"->result(ManyReadPrec.pure{7}).toString()
 "pure-pair-ignore-value"->{val xs=ManyReadPrec.pure<Int>{error("value")}.resultsLazy({error("prec")},ManyReadInput{error("input")});if(xs.step() is GenericList.Step.Cons)"7" else "0"}
 "pure-value"->result(ManyReadPrec.pure<Int>{error("value")}).toString()
 "look-ignore-input"->result(ManyReadPrec.look().map{7}).toString()
 "look-force-input"->result(ManyReadPrec.look().map{s->s().sourceCharacters().size}).toString()
 "get-ignore-headtail"->result(ManyReadPrec.get().map{7},s=ManyReadInput{GenericList.cons({error("char")}){error("tail")}}).toString()
 "get-force-head"->result(ManyReadPrec.get(),s=ManyReadInput{GenericList.cons({error("char")}){error("tail")}}).toString()
 "get-force-tail"->{val r=ManyReadPrec.get().map{7}.results(0,ManyReadInput{GenericList.cons({97}){error("tail")}})[0];r.remainder().forceGenericRepRoot();"False"}
 "get-pure-infinite"->result(ManyReadPrec.get().map{7},s=ManyReadInput{repeatChar{error("char")}}).toString()
 "look-infinite-prefix"->quoteString(result(ManyReadPrec.look().map{s->prefix(s().sourceCharacters(),4)},s=ManyReadInput{repeatChar{97}}))
 "choice-result-before-bad"->result(ManyReadPrec.pure{7}.or(ManyReadPrec.defer{error("parser")})).toString()
 "choice-result-tail-bad"->{val p=ManyReadPrec.pure{7}.or(ManyReadPrec.defer{error("parser")});val x=p.results(0,ManyReadInput(""));val first=x.step() as GenericList.Step.Cons;first.tail().step();"2"}
 "choice-result-before-get"->result(ManyReadPrec.get().map{1}.or(ManyReadPrec.pure{2})).toString()
 "choice-final-before-result"->result(ManyReadPrec.fromReadSLazy<Int>{_,_->error("reads")}.or(ManyReadPrec.pure{2})).toString()
 "final-get-order"->parserRows(finalParser().or(ManyReadPrec.get().map{3}))
 "get-final-order"->parserRows(ManyReadPrec.get().map{3}.or(finalParser()))
 "step-ignore-prec"->result(ManyReadPrec.pure{7}.step()).toString()
 "reset-prec"->result(ManyReadPrec.fromReadSLazy<Long>{p,s->GenericList.cons({ManyReadAlternative(p){s}}){GenericList.empty()}}.reset(),s=ManyReadInput("")).toString()
 "step-wrap"->result(ManyReadPrec.fromReadSLazy<Long>{p,s->GenericList.cons({ManyReadAlternative(p){s}}){GenericList.empty()}}.step(),{Long.MAX_VALUE},ManyReadInput("")).toString()
 "guard-prec"->result(ManyReadPrec.pure{7}.atPrecedence(10)).toString()
 "guard-ignore-body"->ManyReadPrec.defer<Int>{error("parser")}.atPrecedence(10).results(11,ManyReadInput{error("input")}).size.toString()
 "many-get-partial-head-length"->padded.read(partialInput()).length.toString()
 "many-get-partial-head-show"->padded.read(partialInput()).iterator().next().toString()
 "many-bad-list-remainder"->badList.readsPrec(0,ManyReadInput(input))[0].remainder().characters().points().size.toString()
 "many-bad-list-root"->badList.read(ManyReadInput(input)).length.toString()
 "many-empty-infinite-remainder"->{val r=firstEmpty{repeatChar{97}};"(${r.value().length},${quoteString(prefix(r.remainder().sourceCharacters(),4))})"}
 "many-empty-partial-remainder"->firstEmpty{error("tail")}.value().length.toString()
 "many-whole-partial-remainder"->emptyRead.read(ManyReadInput{str(input){error("tail")}}).length.toString()
 "many-whole-infinite-nonspace"->emptyRead.read(ManyReadInput{str(input){repeatChar{97}}}).length.toString()
 "many-prec-input-order"->emptyRead.readsPrecLazy({error("prec")},{error("input")}).size.toString()
 "many-prec-after-spaces"->emptyRead.readsPrecLazy({error("prec")},{ManyReadInput("Many {unMany = fromList []}")}).size.toString()
 "many-prec-empty"->emptyRead.readsPrecLazy({error("prec")},{ManyReadInput("")}).size.toString()
 "show-ignore-bad-prec","show-demand-bad-prec"->{val sh=ManyShowDictionary(object:ManyElementShow<Int>{override fun showsPrec(precedence:Long,value:()->Int,suffix:()->ManyShowString):ManyShowString=error("element")});prefix(sh.showsPrecLazy({error("prec")},{error("value")}){error("suffix")},if(name=="show-ignore-bad-prec")0 else 1)}
 "sharing-pure-failure","sharing-parser-failure"->{var calls=0;val p=if(name=="sharing-pure-failure")ManyReadPrec.pure<Int>{calls++;error("value")}else ManyReadPrec.defer<Int>{calls++;error("parser")};val a=calls;try{result(p,{0},ManyReadInput(""))}catch(_:IllegalStateException){};val b=calls;try{result(p,{0},ManyReadInput(""))}catch(_:IllegalStateException){};"($a,$b,$calls)"}
 "sharing-pure","sharing-parser"->{var calls=0;val p=if(name=="sharing-pure")ManyReadPrec.pure{calls++;7}else ManyReadPrec.defer{calls++;ManyReadPrec.pure{7}};val a=calls;result(p,{0},ManyReadInput(""));val b=calls;result(p,{0},ManyReadInput(""));"($a,$b,$calls)"}
 else->error("Unknown $name")
 };buildJsonObject{put("result",v)}}catch(e:IllegalStateException){buildJsonObject{put("exception",e.message!!.lineSequence().first())}}catch(e:IllegalArgumentException){buildJsonObject{put("exception",e.message!!.lineSequence().first())}}
 check(actual==expected){"$name: $actual != $expected"};count++
 }
 if(only==null)println("Many parser partial/infinite/custom original replay PASS $count")
}

fun main()=replayManyDerivedParser()
