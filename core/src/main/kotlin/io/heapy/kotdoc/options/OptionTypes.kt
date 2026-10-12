// Port of Text.Pandoc.Options, Copyright (C) 2012-2024 John MacFarlane.
// GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.options

import kotlinx.serialization.json.*

interface JsonOption {
    val wireName: String
    fun toJson(): JsonPrimitive = JsonPrimitive(wireName)
}

private fun <T : JsonOption> choice(value: JsonElement, choices: Iterable<T>, description: String,
                                    aliases: Map<String, T> = emptyMap()): T {
    if (value is JsonPrimitive && value.isString) {
        choices.firstOrNull { it.wireName == value.content }?.let { return it }
        aliases[value.content]?.let { return it }
    }
    throw IllegalArgumentException("$description $value")
}

enum class CiteMethod(override val wireName: String) : JsonOption {
    Citeproc("citeproc"), Natbib("natbib"), Biblatex("biblatex");
    companion object {
        fun fromJson(value: JsonElement): CiteMethod = choice(value, entries, "Unknown citation method:")
    }
}

enum class ObfuscationMethod(override val wireName: String) : JsonOption {
    NoObfuscation("none"), ReferenceObfuscation("references"), JavascriptObfuscation("javascript");
    companion object {
        fun fromJson(value: JsonElement): ObfuscationMethod = choice(value, entries, "Unknown obfuscation method")
    }
}

enum class TrackChanges(override val wireName: String) : JsonOption {
    AcceptChanges("accept-changes"), RejectChanges("reject-changes"), AllChanges("all-changes");
    companion object {
        fun fromJson(value: JsonElement): TrackChanges = choice(value, entries, "Unknown track changes method",
            mapOf("accept" to AcceptChanges, "reject" to RejectChanges, "all" to AllChanges))
    }
}

enum class WrapOption(override val wireName: String) : JsonOption {
    WrapAuto("wrap-auto"), WrapNone("wrap-none"), WrapPreserve("wrap-preserve");
    companion object {
        fun fromJson(value: JsonElement): WrapOption = choice(value, entries, "Unknown wrap method",
            mapOf("auto" to WrapAuto, "none" to WrapNone, "preserve" to WrapPreserve))
    }
}

enum class TopLevelDivision(override val wireName: String) : JsonOption {
    TopLevelPart("top-level-part"), TopLevelChapter("top-level-chapter"),
    TopLevelSection("top-level-section"), TopLevelDefault("top-level-default");
    companion object {
        fun fromJson(value: JsonElement): TopLevelDivision = choice(value, entries, "Unknown top level division",
            mapOf("part" to TopLevelPart, "chapter" to TopLevelChapter, "section" to TopLevelSection, "default" to TopLevelDefault))
    }
}

enum class ReferenceLocation(override val wireName: String) : JsonOption {
    EndOfBlock("end-of-block"), EndOfSection("end-of-section"), EndOfDocument("end-of-document");
    companion object {
        fun fromJson(value: JsonElement): ReferenceLocation = choice(value, entries, "Unknown reference location",
            mapOf("block" to EndOfBlock, "section" to EndOfSection, "document" to EndOfDocument))
    }
}

enum class CaptionPosition(override val wireName: String) : JsonOption {
    CaptionAbove("above"), CaptionBelow("below");
    companion object {
        fun fromJson(value: JsonElement): CaptionPosition = choice(value, entries, "Unknown caption position")
    }
}

enum class HTMLSlideVariant(override val wireName: String) : JsonOption {
    S5Slides("s5slides"), SlidySlides("slidyslides"), SlideousSlides("slideousslides"),
    DZSlides("dzslides"), RevealJsSlides("revealjsslides"), NoSlides("noslides");
    companion object {
        fun fromJson(value: JsonElement): HTMLSlideVariant = choice(value, entries, "Unknown HTML slide variant")
    }
}

enum class EPUBVersion { EPUB2, EPUB3 }

interface HasSyntaxExtensions {
    val extensions: Extensions
    fun isEnabled(extension: Extension): Boolean = extension in extensions
    fun isEnabled(extension: ExtensionName): Boolean = extension in extensions
}
