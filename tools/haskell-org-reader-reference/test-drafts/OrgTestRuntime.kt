// Independent original-reader test runtime; GPL-2.0-or-later.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.templates.PartialPaths
import io.heapy.kotdoc.errors.PandocError
import kotlinx.serialization.json.*
import java.util.zip.GZIPInputStream
import java.io.FileInputStream
import java.math.BigInteger
internal class OrgTestRuntime(q:JsonObject,state:PandocState=PandocStateContext()):PandocDataRuntime,PandocState by state {
 override val filePaths=PartialPaths.Posix
 private val files=q["files"]?.jsonArray?.associate{f->val o=f.jsonObject;filePaths.canonical(o.getValue("path").jsonPrimitive.content) to o.getValue("bytes").jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()}?:emptyMap()
 fun key(p:CharacterString)=filePaths.canonical(CharacterStringChars.build(p))
 override fun readFileStrict(path:CharacterString)=files[key(path)]?:throw PandocError.PandocResourceNotFound(CharacterStringChars.build(path))
 override fun readFileLazy(path:CharacterString)=readFileStrict(path)
 override fun fileExists(path:CharacterString)=key(path) in files
 override fun getDataFileName(path:CharacterString)=CharacterStringChars.fromString("data/"+CharacterStringChars.build(path))
 override fun lookupEnv(name:String)=if(name=="USER")"pandoc-user"else null
 override fun getCurrentTime()=PandocUtcTime(BigInteger.valueOf(40587),BigInteger.ZERO)
}
