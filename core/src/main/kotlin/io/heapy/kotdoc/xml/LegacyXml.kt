// xml 1.3.14 Text.XML.Light.Types/constructors, (c) 2007 Galois Inc.; BSD-3-Clause.
package io.heapy.kotdoc.xml

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import java.math.BigInteger
import java.util.Collections

internal fun legacyChars(s: String)=CharacterStringChars.fromString(s)
internal fun <A> legacyOwned(xs: List<A>): List<A> = Collections.unmodifiableList(xs.toList())

/** XML.Light uses raw Haskell Strings, including separate surrogate Chars. */
class LegacyXmlName(val local: CharacterString,val uri: CharacterString?=null,val prefix: CharacterString?=null): Comparable<LegacyXmlName> {
    constructor(local: String):this(legacyChars(local))
    override fun compareTo(other: LegacyXmlName): Int {
        val local=local.compareTo(other.local);if(local!=0) return local
        fun optional(a: CharacterString?,b: CharacterString?)=if(a==null) if(b==null) 0 else -1 else if(b==null) 1 else a.compareTo(b)
        return if(uri==null && other.uri==null) optional(prefix,other.prefix) else optional(uri,other.uri)
    }
    override fun equals(other: Any?)=other is LegacyXmlName && compareTo(other)==0
    override fun hashCode()=31*(31*local.hashCode()+(uri?.hashCode() ?: 0))+(if(uri==null) prefix?.hashCode() ?: 0 else 0)
}
class LegacyXmlAttribute(val name: LegacyXmlName,val value: CharacterString): Comparable<LegacyXmlAttribute> {
    constructor(name: String,value: String):this(LegacyXmlName(name),legacyChars(value))
    override fun compareTo(other: LegacyXmlAttribute)=name.compareTo(other.name).takeIf {it!=0} ?: value.compareTo(other.value)
    override fun equals(other: Any?)=other is LegacyXmlAttribute && compareTo(other)==0
    override fun hashCode()=31*name.hashCode()+value.hashCode()
}
enum class LegacyXmlTextKind { CDataText, CDataVerbatim, CDataRaw }
sealed interface LegacyXmlContent
class LegacyXmlText(val text: CharacterString,val kind: LegacyXmlTextKind=LegacyXmlTextKind.CDataText,val line: BigInteger?=null): LegacyXmlContent {
    constructor(text: String):this(legacyChars(text))
}
class LegacyXmlCRef(val reference: CharacterString): LegacyXmlContent
class LegacyXmlElement(val name: LegacyXmlName,attributes: List<LegacyXmlAttribute>,content: List<LegacyXmlContent>,val line: BigInteger?=null): LegacyXmlContent {
    val attributes=legacyOwned(attributes)
    val content=legacyOwned(content)
    fun withAttribute(name: String,value: String)=LegacyXmlElement(this.name,listOf(LegacyXmlAttribute(name,value))+attributes,content,line)
    fun setAttribute(name: String,value: String): LegacyXmlElement {
        val at=attributes.indexOfFirst {it.name.local==legacyChars(name)}
        val attrs=attributes.toMutableList();val attr=LegacyXmlAttribute(name,value)
        if(at<0) attrs.add(attr) else attrs[at]=attr
        return LegacyXmlElement(this.name,attrs,content,line)
    }
    companion object {
        fun node(name: String,children: List<LegacyXmlContent> = emptyList())=LegacyXmlElement(LegacyXmlName(name),emptyList(),children)
        fun textNode(name: String,text: String)=node(name,listOf(LegacyXmlText(text)))
    }
}
