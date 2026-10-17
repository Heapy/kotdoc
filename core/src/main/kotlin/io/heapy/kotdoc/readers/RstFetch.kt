// Reader-used closure of Text.Pandoc.Class.PandocMonad.fetchItem/downloadOrRead.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.readers

import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.logging.LogMessage
import io.heapy.kotdoc.resources.ResourceUri
import io.heapy.kotdoc.state.PandocState
import io.heapy.kotdoc.templates.PartialPaths
import io.heapy.kotdoc.uri.NetworkURI
import io.heapy.kotdoc.uri.PandocURI
import io.heapy.kotdoc.uri.URI

/** MIME is not demanded by the reader's CSV consumer. All resource selection and
 * ordered logging remain those of PandocMonad; host IO is supplied explicitly. */
internal object RstFetch {
 fun fetch(value:String,state:PandocState,readFile:(String)->ByteArray,openURL:(String)->ByteArray):ByteArray {
  state.getMediaBag()[value]?.let{return it.contents}
  if(value.take(5).lowercase()=="data:")ResourceUri.base64Data(value)?.let{return it.contents}
  fun escaped(s:String)=Chars.build(NetworkURI.escapeURIString(NetworkURI::isAllowedInURI,Chars.fromString(s.replace('\\','/'))))
  // The original predicate tests the complete uriScheme field (including ':').
  fun reference(s:String)=NetworkURI.parseURIReference(s)?.takeUnless{it.scheme.points().size==1}
  val target=escaped(value)
  val source=state.getSourceURL()?.let{reference(escaped(it))}
  if(source!=null)return openURL(reference(target)?.let{NetworkURI.nonStrictRelativeTo(it,source).toString()}?:target)
  if(target.length>=3&&target.startsWith("//")&&target[2]!='?')return openURL(reference(target)?.let{NetworkURI.nonStrictRelativeTo(it,URI(scheme="http:")).toString()}?:target)
  fun local(path:String):ByteArray {
   val paths=PartialPaths.System
   val found:Pair<String,ByteArray> = if(paths.isRelative(path)) {
    var result:Pair<String,ByteArray>?=null
    for(dir in state.getResourcePath()){
     val candidate=paths.combine(Chars.build(dir),path)
     try{result=candidate to readFile(candidate);break}catch(_:PandocError){ }
    }
    result?:throw PandocError.PandocResourceNotFound(path)
   } else path to readFile(path)
   state.report(LogMessage.LoadedResource(path,relativeToDot(found.first)))
   return found.second
  }
  val uri=NetworkURI.parseURI(target)
  if(uri!=null){
   when(Chars.build(uri.scheme).lowercase()){
    "file:"->return local(PandocURI.uriPathToPath(Chars.build(uri.path)))
    "data:"->return ResourceUri.extractData(Chars.build(uri.path)).contents
   }
   if(uri.scheme.points().size>2)return openURL(uri.toString())
  }
  return local(Chars.build(NetworkURI.unEscapeString(Chars.fromString(value))))
 }
 // System.FilePath.makeRelative "." specialization. It removes one matching
 // directory component, retaining subsequent '.' and '..' components.
 private fun relativeToDot(path:String):String {
  val windows=PartialPaths.System==PartialPaths.Windows
  fun separator(c:Char)=c=='/'||windows&&c=='\\'
  if(!PartialPaths.System.isRelative(path))return path
  val normalized=path.split(if(windows)Regex("[/\\\\]+")else Regex("/+" )).filter{it.isNotEmpty()&&it!="."}
  if(normalized.isEmpty())return "."
  if(path.startsWith('.')&&path.getOrNull(1)?.let(::separator)==true)return path.drop(1).dropWhile(::separator)
  return path
 }
}
