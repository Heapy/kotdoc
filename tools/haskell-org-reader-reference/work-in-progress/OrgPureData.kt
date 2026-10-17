// PandocPure default effect profile, pinned Pandoc 3.12.1 (GPL-2.0-or-later).
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.resources.*
import io.heapy.kotdoc.state.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.templates.PartialPaths
import java.math.BigInteger
internal class OrgPureData(state:PandocState):PandocDataRuntime,PandocState by state {
 override val filePaths get()=PartialPaths.Posix
 override fun readFileStrict(path:CharacterString):ByteArray = throw PandocError.PandocResourceNotFound(TextChars.fromPoints(path.points()))
 override fun readFileLazy(path:CharacterString)=readFileStrict(path)
 override fun fileExists(path:CharacterString)=false
 override fun getDataFileName(path:CharacterString)=CharacterStringChars.concat(listOf(CharacterStringChars.fromString("data/"),path))
 override fun lookupEnv(name:String)=if(name=="USER")"pandoc-user" else null
 override fun getCurrentTime()=PandocUtcTime(BigInteger.valueOf(40587),BigInteger.ZERO)
}
