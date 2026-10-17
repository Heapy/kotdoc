package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
import java.io.File
private fun sourceText(s:String,rest:()->ManyShowString=GenericList.Companion::empty)=ManyShowRuntime.text(s,rest)
private fun sourceRender(value:ManyShowString,limit:Int=Int.MAX_VALUE):String {
 val output=StringBuilder();var rest=value;var n=limit
 while(n>0)when(val node=rest.step()){GenericList.Step.Nil->return output.toString();is GenericList.Step.Cons->{output.appendCodePoint(node.head());n--;if(n>0)rest=node.tail()}}
 return output.toString()
}
private val intElement=object:ManyElementOrd<Int>{override fun equal(left:()->Int,right:()->Int)=left()==right();override fun compare(left:()->Int,right:()->Int)=left().compareTo(right())}
private val intShow=object:ManyElementShow<Int>{override fun showsPrec(precedence:Long,value:()->Int,suffix:()->ManyShowString)=sourceText(value().toString(),suffix)}
fun replayManyDerivedDemand(only:String?=null){
 val equality=ManyEqDictionary(intElement);val ordering=ManyOrdDictionary(intElement);val display=ManyShowDictionary(intShow)
 val badRoot={error("root") as Many<Int>};val badHead={Many.singleton<Int>{error("head")}}
 val badTail={Many.fromTree{ManyTree.Deep({listOf(ManyCell.Leaf{1})},{error("middle")},{listOf(ManyCell.Leaf{2})},3)}}
 val empty={Many.empty<Int>()};val one={Many.fromList(listOf(1))}
 val ignore=object:ManyElementOrd<Int>{override fun equal(left:()->Int,right:()->Int)=true;override fun compare(left:()->Int,right:()->Int)=0}
 val ignoringShow=ManyShowDictionary(object:ManyElementShow<Int>{override fun showsPrec(precedence:Long,value:()->Int,suffix:()->ManyShowString)=sourceText("E",suffix);override fun showList(value:()->GenericList<Int>,suffix:()->ManyShowString)=sourceText("CUSTOM",suffix)})
 fun bool(v:Boolean)=if(v)"True" else "False"
 fun order(v:Int)=if(v<0)"LT"else if(v>0)"GT"else"EQ"
 var count=0
 for((name,expected) in ManyDerivedProof.named("demand-native.jsonl",only)){val parts=name.split('/')
 val actual=try {val result=when(parts[0]){
 "show-root","show-head","show-tail"->sourceRender(display.showsPrec(0,when(parts[0]){"show-root"->badRoot;"show-head"->badHead;else->badTail}){sourceText("TAIL")},parts[1].toInt())
 "show-listroot"->sourceRender(display.showList({error("listroot")}){sourceText("TAIL")},parts[1].toInt())
 "show-listtail"->sourceRender(display.showList({GenericList.cons(one){error("listtail")}}){sourceText("TAIL")},parts[1].toInt())
 "show-ignore-root"->sourceRender(ignoringShow.show(badRoot))
 "show-ignore-head"->sourceRender(ignoringShow.show(badHead))
 "eq-ignore-head"->bool(ManyEqDictionary(ignore).equal({Many.singleton{error("left")}},{Many.singleton{error("right")}}))
 "ne-ignore-head"->bool(ManyEqDictionary(ignore).notEqual({Many.singleton{error("left")}},{Many.singleton{error("right")}}))
 "compare-ignore-head"->order(ManyOrdDictionary(ignore).compare({Many.singleton{error("left")}},{Many.singleton{error("right")}}))
 "eq-size-beforehead"->bool(equality.equal(badHead,empty))
 "eq-leftroot"->bool(equality.equal({error("left")},{error("right")}))
 "eq-lefttree-rightroot"->bool(equality.equal({Many.fromTree{error("lefttree")}},{error("right")}))
 "compare-empty-badhead"->order(ordering.compare(empty,badHead))
 "compare-badhead-empty"->order(ordering.compare(badHead,empty))
 "compare-lefttree-rightroot"->order(ordering.compare({Many.fromTree{error("lefttree")}},{error("right")}))
 "eq-cached-length","compare-ignores-cache"->{val malformed={Many.fromTree{ManyTree.Deep({listOf(ManyCell.Leaf{1})},{ManyTree.Empty()},{listOf(ManyCell.Leaf{2})},7)}};val normal={Many.fromList(listOf(1,2))};if(parts[0]=="eq-cached-length")bool(equality.equal(malformed,normal))else order(ordering.compare(malformed,normal))}
 "eq-strict-prefix","eq-strict-suffix","eq-prefix-before-suffix"->{val malformed={Many.fromTree{ManyTree.Deep({if(parts[0]!="eq-strict-suffix")error("prefix")else listOf(ManyCell.Leaf{1})},{ManyTree.Empty()},{if(parts[0]!="eq-strict-prefix")error("suffix")else listOf(ManyCell.Leaf{2})},7)}};bool(equality.equal(malformed,empty))}
 "nan"->{val d=object:ManyElementOrd<Double>{override fun equal(left:()->Double,right:()->Double)=left()==right();override fun compare(left:()->Double,right:()->Double)=AstComparison.Double.compare(left(),right())};val a={Many.singleton{Double.NaN}};val eq=ManyEqDictionary(d);val ord=ManyOrdDictionary(d);when(parts[1]){"eq"->bool(eq.equal(a,a));"ne"->bool(eq.notEqual(a,a));"compare"->order(ord.compare(a,a));"lt"->bool(ord.less(a,a));"le"->bool(ord.lessEqual(a,a));"gt"->bool(ord.greater(a,a));else->bool(ord.greaterEqual(a,a))}}
 else->error("Unknown $name")
 };buildJsonObject{put("result",result)}}catch(e:IllegalStateException){buildJsonObject{put("exception",e.message!!.lineSequence().first())}}
 check(actual==expected){"$name: $actual != $expected"};count++
 }
 if(only==null)println("Many derived partial/custom demand PASS $count")
}

fun main()=replayManyDerivedDemand()
