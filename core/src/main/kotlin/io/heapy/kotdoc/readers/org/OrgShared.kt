// Text.Pandoc.Readers.Org.Shared, ©2014-2024 Albert Krewinkel; GPL-2.0-or-later.
// System.FilePath.Internal1.5.4.0 used path operations: BSD-3-Clause.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.templates.PartialPaths
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.latex.LaTeXReaderText
internal object OrgShared {
 // Same Posix host profile as the pinned original Pandoc runtime/oracle.
 fun isImageFilename(path:String):Boolean=PartialPaths.Posix.takeExtension(LaTeXReaderText.toLower(path))in listOf(".jpeg",".jpg",".png",".gif",".svg",".webp",".jxl",".avif",".pdf")&&((path.isNotEmpty()&&'\u0000' !in path)||listOf("file","http","https").any{path.startsWith(it+"://")})
 fun cleanLinkText(path:String):String?{
  fun file(s:String):String?=when{!PartialPaths.Windows.isRelative(s)->"file:///"+s;!PartialPaths.Posix.isRelative(s)->"file://"+s;else->null}
  file(path)?.let{return it}
  if(path.startsWith("./")||path.startsWith("../"))return path
  if(path.startsWith("file:")){val s=path.drop(5);return if(s.startsWith("//"))path else file(s)?:s}
  val at=path.indexOf(':');return if(at>=0&&path.take(at).codePoints().allMatch{ParserCharacters.isAlphaNum(it)||it==46||it==45})path else null
 }
 fun translateLang(s:String)=when(s){"C"->"c";"C++"->"cpp";"emacs-lisp","lisp"->"commonlisp";"js"->"javascript";"R"->"r";"sh"->"bash";"sqlite"->"sql";else->s}
 fun originalLang(s:String)=if(translateLang(s)!=s)listOf("org-language" to s) else emptyList()
 fun exportsCode(kv:List<Pair<String,String>>)=kv.firstOrNull{it.first=="exports"}?.second?.let{it=="code"||it=="both"}?:true
}
