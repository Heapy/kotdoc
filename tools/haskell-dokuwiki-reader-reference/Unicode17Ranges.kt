import com.ibm.icu.lang.UCharacter
fun main(){
 val ranges=mutableListOf<Pair<Int,Int>>();var start=-1
 for(cp in 0..0x110000){ val yes=cp<0x110000 && UCharacter.getAge(cp).major==17
 if(yes&&start<0)start=cp
 if(!yes&&start>=0){ranges.add(start to cp-1);start=-1}}
 print(ranges.joinToString("\n",postfix="\n"){"${it.first}\t${it.second}"})
}
