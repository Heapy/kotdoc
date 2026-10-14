// Text.XML/xml-types 1.10.1.0/0.3.8, Copyright (c) 2010-2024 Michael Snoyman,
// John Millikin and contributors. MIT; licenses/xml-conduit-MIT.txt.
package io.heapy.kotdoc.xml

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.formats.HaskellString
import java.util.Collections

class XmlDocName(val local: String, val namespace: String? = null, val prefix: String? = null) : Comparable<XmlDocName> {
    override fun equals(other: Any?) = other is XmlDocName && namespace == other.namespace && local == other.local
    override fun hashCode() = 31 * (namespace?.hashCode() ?: 0) + local.hashCode()
    override fun compareTo(other: XmlDocName): Int {
        val ns = when { namespace == null && other.namespace == null -> 0; namespace == null -> -1; other.namespace == null -> 1
            else -> UnicodeTextOrder.compare(namespace, other.namespace) }
        return if (ns != 0) ns else UnicodeTextOrder.compare(local, other.local)
    }
    fun haskellShow(precedence: Int = 0): String {
        fun maybe(value: String?) = value?.let { "Just ${HaskellString.quote(it)}" } ?: "Nothing"
        val text = "Name {nameLocalName = ${HaskellString.quote(local)}, nameNamespace = ${maybe(namespace)}, namePrefix = ${maybe(prefix)}}"
        return if (precedence > 10) "($text)" else text
    }
    override fun toString() = haskellShow()
}
data class XmlInstruction(val target: String, val data: String)
sealed interface XmlMisc {
    data class Comment(val text: String) : XmlMisc
    data class Instruction(val instruction: XmlInstruction) : XmlMisc
}
sealed interface XmlExternalId {
    data class SystemId(val system: String) : XmlExternalId
    data class PublicId(val public: String, val system: String) : XmlExternalId
}
data class XmlDoctype(val name: String, val externalId: XmlExternalId?)
sealed interface XmlDocNode {
    data class Element(val element: XmlDocElement) : XmlDocNode
    data class Text(val text: String) : XmlDocNode
    data class Comment(val text: String) : XmlDocNode
    data class Instruction(val instruction: XmlInstruction) : XmlDocNode
}
class XmlDocElement(val name: XmlDocName, attributes: Map<XmlDocName, String>, nodes: List<XmlDocNode>) {
    val attributes: Map<XmlDocName, String> = Collections.unmodifiableMap(attributes.toSortedMap())
    val nodes: List<XmlDocNode> = Collections.unmodifiableList(nodes.toList())
    val children: List<XmlDocElement> get() = nodes.mapNotNull { (it as? XmlDocNode.Element)?.element }
    val strContent: String get() = nodes.filterIsInstance<XmlDocNode.Text>().joinToString("") { it.text }
}
class XmlDocument(before: List<XmlMisc>, val doctype: XmlDoctype?, after: List<XmlMisc>, val root: XmlDocElement, epilogue: List<XmlMisc>) {
    val before: List<XmlMisc> = Collections.unmodifiableList(before.toList())
    val after: List<XmlMisc> = Collections.unmodifiableList(after.toList())
    val epilogue: List<XmlMisc> = Collections.unmodifiableList(epilogue.toList())
}
sealed interface XmlDocumentResult {
    class Success(val document: XmlDocument) : XmlDocumentResult
    class Failure(val error: String) : XmlDocumentResult
}
