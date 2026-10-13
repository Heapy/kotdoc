// Port of Text.Pandoc.Format, Copyright (C) 2022-2024 Albert Krewinkel.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.options

import com.ibm.icu.lang.UCharacter
import io.heapy.kotdoc.resources.ResourceUri
import io.heapy.kotdoc.ast.UnicodeText

data class ExtensionsDiff(
    val enable: Extensions = Extensions.Empty,
    val disable: Extensions = Extensions.Empty,
) {
    /** Composition is ordered: changes in other override the corresponding changes here. */
    operator fun plus(other: ExtensionsDiff): ExtensionsDiff = ExtensionsDiff(
        (enable - other.disable) + other.enable,
        (disable - other.enable) + other.disable,
    )

    companion object {
        fun between(defaults: Extensions, actual: Extensions): ExtensionsDiff =
            ExtensionsDiff(actual - defaults, defaults - actual)
    }
}

data class ExtensionsConfig(
    val defaults: Extensions = Extensions.Empty,
    val supported: Extensions = Extensions.Empty,
) {
    operator fun plus(other: ExtensionsConfig): ExtensionsConfig =
        ExtensionsConfig(defaults + other.defaults, supported + other.supported)

    fun apply(format: FlavoredFormat): Extensions {
        val unsupported = (format.extensions.enable + format.extensions.disable) - supported
        unsupported.firstOrNull()?.let { throw UnsupportedExtensionException(it.wireName, format.name) }
        return (defaults - format.extensions.disable) + format.extensions.enable
    }
}

class UnsupportedExtensionException(val extension: String, val format: String) : IllegalArgumentException(
    "The extension '$extension' is not supported for $format.\nUse --list-extensions=$format to list supported extensions."
)

class FormatSpecificationException(val specification: String) : IllegalArgumentException(
    "Error parsing format '$specification': (line 1, column 1):\nunexpected " +
        if (specification.isEmpty()) "end of input" else "'${specification.first()}'"
)

data class FlavoredFormat(val name: String, val extensions: ExtensionsDiff = ExtensionsDiff()) {
    companion object {
        fun parse(specification: String): FlavoredFormat {
            val dot = extensionIndex(specification)
            val prefix = if (dot < 0) "" else specification.substring(0, dot)
            val body = if (dot < 0) UnicodeText.toLower(specification) else specification.substring(dot)
            val firstModifier = body.indexOfFirst { it == '+' || it == '-' }.let { if (it < 0) body.length else it }
            if (firstModifier == 0) throw FormatSpecificationException(specification)
            var index = firstModifier
            var enabled = Extensions.Empty
            var disabled = Extensions.Empty
            while (index < body.length) {
                val polarity = body[index++]
                val start = index
                while (index < body.length && body[index] != '+' && body[index] != '-') index++
                val extension = Extension.read(body.substring(start, index))
                if (polarity == '+') enabled = enabled.enable(extension) else disabled = disabled.enable(extension)
            }
            return FlavoredFormat(prefix + body.substring(0, firstModifier), ExtensionsDiff(enabled, disabled))
        }

        fun fromFilePaths(paths: Iterable<String>): FlavoredFormat? = paths.firstNotNullOfOrNull(::fromFilePath)

        private fun fromFilePath(path: String): FlavoredFormat? {
            val uri = ResourceUri.parse(path)?.takeIf { it.isAbsolute && path.all { c -> c.code < 128 } }
            val uriPath = uri?.let { it.rawPath ?: it.rawSchemeSpecificPart.substringBefore('?') }
            val file = if (uriPath == "" || uriPath == "/") "index.html" else uriPath ?: path
            val lower = buildString { file.codePoints().forEach { appendCodePoint(UCharacter.toLowerCase(it)) } }
            val index = extensionIndex(lower)
            if (index < 0) return null
            val extension = lower.substring(index)
            val format = ExtensionRegistry.fileTypes[extension]
                ?: if (extension.length == 2 && extension[1] in '1'..'9') "man" else return null
            return FlavoredFormat(format, if (extension == ".lhs")
                ExtensionsDiff(enable = Extensions.of(ExtensionName.LiterateHaskell)) else ExtensionsDiff())
        }

        /** System.FilePath.Posix treats a leading dot as an extension too. */
        private fun extensionIndex(path: String): Int = path.lastIndexOf('.').takeIf { it > path.lastIndexOf('/') } ?: -1
    }
}
