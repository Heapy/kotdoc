// Text.Pandoc.Readers.RST pure helpers; Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; original pinned source in upstream/pandoc.
package io.heapy.kotdoc.readers
import io.heapy.kotdoc.parsing.ParserCharacters
internal object RstReaderPure {
 fun trim(s:String)=s.trim{ParserCharacters.isSpace(it.code)}
 fun textLines(s:String):List<String> = if(s.isEmpty())emptyList()else s.removeSuffix("\n").split('\n')
  fun unicodeTransform(raw:String):String{
   val out=StringBuilder();var at=0
   while(at<raw.length){if(raw.startsWith("..",at)){at=raw.indexOf('\n',at+2).let{if(it<0)raw.length else it};continue}
    val prefix=listOf("0x","x","\\x","U+","u","\\u","&#x").firstOrNull{raw.startsWith(it,at)}
    if(prefix!=null){var end=at+prefix.length;while(end<raw.length&&raw[end] in "0123456789abcdefABCDEF")end++;val value=raw.substring(at+prefix.length,end).toLongOrNull(16)
     if(value!=null&&value<=0x10ffff){out.appendCodePoint(if(value in 0xd800..0xdfff)0xfffd else value.toInt());at=end+(if(prefix=="&#x"&&end<raw.length)1 else 0);continue}
     out.append(prefix);at+=prefix.length;continue
    };val cp=raw.codePointAt(at);out.appendCodePoint(cp);at+=Character.charCount(cp)
   };return out.toString()
  }
  fun chunks(raw:String):List<String>{
   val lines=textLines(raw);val groups=mutableListOf<String>();var at=0
   while(at<lines.size){val start=at;while(at<lines.size&&!lines[at].all{it==' '||it=='\t'})at++;val part=trim(lines.subList(start,at).joinToString("\n",postfix=if(at==start)""else"\n"));groups+=if("\\\\" in part)"\\begin{aligned}\n$part\n\\end{aligned}"else part;while(at<lines.size&&lines[at].all{it==' '||it=='\t'})at++}
   return groups.dropWhile{it.isEmpty()}
  }
  fun colSpans(indices:List<Int>,spans:List<Int>):List<Long>?{
   val result=mutableListOf<Long>();var i=0;var j=0
   while(i<indices.size&&j<spans.size){
    if(indices[i]!=spans[j]){i++;continue}
    i++;j++;var count=1L
    while(true){
     if(i==indices.size)break
     if(j==spans.size){i=indices.size;break}
     if(indices[i]==spans[j]){if(j==spans.lastIndex){i++;j++};break}
     i++;count++
    }
    result+=count
   }
   return if(i==indices.size&&j==spans.size)result else null
  }
}
