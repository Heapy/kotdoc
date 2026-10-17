package io.heapy.kotdoc.ast
import kotlinx.serialization.json.*
private fun text(s:String)=ManyShowRuntime.text(s,GenericList.Companion::empty)
private fun render(s:ManyShowString)=buildString{for(c in s)appendCodePoint(c)}
private fun showElement(name:String?=null)=object:ManyElementShow<Int>{override fun showsPrec(precedence:Long,value:()->Int,suffix:()->ManyShowString):ManyShowString {
 val n=value();val s=if(name==null)"$n"else "$name "+if(n<0)"($n)"else "$n"
 return ManyShowRuntime.text(if(name!=null&&precedence>10)"($s)"else s,suffix)
}}
private val ordinary=object:ManyElementOrd<Int>{override fun equal(left:()->Int,right:()->Int)=left()==right();override fun compare(left:()->Int,right:()->Int)=left().compareTo(right())}
private val odd=object:ManyElementOrd<Int>{override fun equal(left:()->Int,right:()->Int)=left()%2==right()%2;override fun compare(left:()->Int,right:()->Int)=right().compareTo(left())}
private val zero=object:ManyElementOrd<Int>{override fun equal(left:()->Int,right:()->Int)=left()==right();override fun compare(left:()->Int,right:()->Int)=0}
private val neOnly=object:ManyElementOrd<Int>{override fun notEqual(left:()->Int,right:()->Int)=left()%2!=right()%2;override fun compare(left:()->Int,right:()->Int)=left().compareTo(right())}
private val leOnly=object:ManyElementOrd<Int>{override fun equal(left:()->Int,right:()->Int)=left()==right();override fun lessEqual(left:()->Int,right:()->Int)=left()>=right()}
private val eqFirst=object:ManyElementOrd<Int>{override fun equal(left:()->Int,right:()->Int)=true;override fun lessEqual(left:()->Int,right:()->Int):Boolean=error("unused-le")}
private fun operation(op:String,a:Many<Int>,b:Many<Int>,d:ManyElementOrd<Int>,display:ManyElementShow<Int>):String {
 val eq=ManyEqDictionary(d);val ord=ManyOrdDictionary(d)
 fun bool(v:Boolean)=if(v)"True"else"False"
 return when(op){
 "eq"->bool(eq.equal({a},{b}));"ne"->bool(eq.notEqual({a},{b}));"compare"->when(ord.compare({a},{b})){ -1->"LT";0->"EQ";else->"GT"}
 "lt"->bool(ord.less({a},{b}));"le"->bool(ord.lessEqual({a},{b}));"gt"->bool(ord.greater({a},{b}));"ge"->bool(ord.greaterEqual({a},{b}))
 "min"->render(ManyShowDictionary(display).show{ord.minimum({a},{b})});"max"->render(ManyShowDictionary(display).show{ord.maximum({a},{b})})
 else->error("Unknown operation $op")
 }
}
private fun evaluateFinite(name:String):String {
 val p=name.split('/');val values=listOf(emptyList(),listOf(0),listOf(1),listOf(0,1),listOf(1,0),listOf(0,1,2),listOf(2,1,0))
 return when(p[0]){
 "int","odd"->{val a=Many.fromList(values[p[1].toInt()]);val b=Many.fromList(values[p[2].toInt()]);operation(p[3],a,b,if(p[0]=="odd")odd else ordinary,showElement(if(p[0]=="odd")"Odd"else null))}
 "large"->{val xs=(0 until p[1].toInt()).toList();val ys=when(p[2].toInt()){0->xs;1->xs.drop(1);2->emptyList();3->if(xs.isEmpty())listOf(1)else xs.dropLast(1)+(-1);else->xs.reversed()};operation(p[3],Many.fromList(xs),Many.fromList(ys),ordinary,showElement())}
 "zero"->operation(p[1],Many.fromList(listOf(1)),Many.fromList(listOf(2)),zero,showElement("Zero"))
 "ne-only","le-only","eq-first"->{val small=listOf(emptyList(),listOf(1),listOf(2),listOf(1,2));val(d,title)=when(p[0]){"ne-only"->neOnly to "NeOnly";"le-only"->leOnly to "LeOnly";else->eqFirst to "EqFirst"};operation(p[3],Many.fromList(small[p[1].toInt()]),Many.fromList(small[p[2].toInt()]),d,showElement(title))}
 "show"->render(ManyShowDictionary(showElement()).showsPrec(p[2].toLong(),{Many.fromList(values[p[1].toInt()])}){text("TAIL")})
 "show-only"->{val d=object:ManyElementShow<Int>{override fun show(value:()->Int)=ManyShowRuntime.text("ONLY"){text(value().toString())}};render(ManyShowDictionary(d).showsPrec(p[1].toLong(),{Many.fromList(listOf(1,2))}){text("TAIL")})}
 "show-conflict"->{val d=object:ManyElementShow<Int>{override fun showsPrec(precedence:Long,value:()->Int,suffix:()->ManyShowString)=ManyShowRuntime.text("PREC$precedence:${value()}",suffix);override fun show(value:()->Int)=text("SHOW")};render(ManyShowDictionary(d).showsPrec(p[1].toLong(),{Many.fromList(listOf(1,2))}){text("TAIL")})}
 "show-override"->{val d=object:ManyElementShow<Int>{override fun showsPrec(precedence:Long,value:()->Int,suffix:()->ManyShowString)=ManyShowRuntime.text("E$precedence:${value()}",suffix);override fun showList(value:()->GenericList<Int>,suffix:()->ManyShowString)=ManyShowRuntime.text("CUSTOM",suffix)};render(ManyShowDictionary(d).showsPrec(p[1].toLong(),{Many.fromList(listOf(1,2))}){text("TAIL")})}
 "read"->{val inputs=listOf("Many {unMany = fromList []}","Many {unMany = fromList [1,2]} rest","(Many {unMany = fromList [1]})tail","Many (fromList [1])","Many { unMany = (fromList [1]) }","Many {unMany = fromList [1,]}","Many {unMany = fromList [(-1)]}");ManyDerivedReadFixtures.execute("int","readsPrec",p[2].toLong(),ManyReadInput(inputs[p[1].toInt()]))}
 "read-list-override"->ManyDerivedReadFixtures.execute("tag","readsPrec",p[1].toLong(),ManyReadInput("Many {unMany = fromList TAG}tail"))
 "read-char"->{val inputs=listOf("Many {unMany = fromList \"ab\"}T","Many {unMany = fromList ['a']}T","Many {unMany = fromList (['a'])}T","Many {unMany = fromList ((['a']))}T");ManyDerivedReadFixtures.execute("char","readsPrec",0,ManyReadInput(inputs[p[1].toInt()]))}
 "read-duplicate"->ManyDerivedReadFixtures.execute("duplicate","readsPrec",0,ManyReadInput("Many {unMany = fromList }tail"))
 "read-duplicate-whole"->ManyDerivedReadFixtures.execute("duplicate","read",0,ManyReadInput("Many {unMany = fromList }"))
 else->error("Unknown original row $name")
 }
}
fun replayManyDerivedFinite(only:String?=null){var count=0
 for((name,expected) in ManyDerivedProof.named("native.jsonl",only)){
 val actual=try{buildJsonObject{put("result",evaluateFinite(name))}}catch(e:IllegalArgumentException){buildJsonObject{put("exception",e.message!!.lineSequence().first())}}
 check(actual==expected){"$name: $actual != $expected"};count++
 }
 if(only==null)println("Many derived finite/default/shape original replay PASS $count")
}
fun main()=replayManyDerivedFinite()
