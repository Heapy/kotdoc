// Generated from DocLayout 0.6 shortcut guards, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.layout

internal object WidthShortcuts {

    fun narrow(c: Int): Width? = when {
        c <= 0x001F -> Width.Control
        c <= 0x007E -> Width.Narrow
        c <= 0x009F -> Width.Control
        c == 0x00AD -> Width.Control
        c <= 0x02FF -> Width.Narrow
        c <= 0x036F -> Width.Combining
        c >= 0x3250 && c <= 0xA4CF -> Width.Wide
        c >= 0x0600 && c <= 0x06FF -> when {
            c <= 0x0605 -> Width.Control
            c <= 0x060F -> Width.Narrow
            c <= 0x061A -> Width.Combining
            c == 0x061B -> Width.Narrow
            c <= 0x061C -> Width.Control
            c <= 0x064A -> Width.Narrow
            c <= 0x065F -> Width.Combining
            c == 0x0670 -> Width.Combining
            c <= 0x06D5 -> Width.Narrow
            c <= 0x06DC -> Width.Combining
            c == 0x06DD -> Width.Control
            c == 0x06DE -> Width.Narrow
            c <= 0x06E4 -> Width.Combining
            c <= 0x06E6 -> Width.Narrow
            c == 0x06E9 -> Width.Narrow
            c <= 0x06ED -> Width.Combining
            else -> Width.Narrow
        }
        c >= 0x0900 && c <= 0x097F -> when {
            c <= 0x0902 -> Width.Combining
            c <= 0x0939 -> Width.Narrow
            c == 0x093A -> Width.Combining
            c == 0x093C -> Width.Combining
            c <= 0x0940 -> Width.Narrow
            c <= 0x0948 -> Width.Combining
            c == 0x094D -> Width.Combining
            c <= 0x0950 -> Width.Narrow
            c <= 0x0957 -> Width.Combining
            c == 0x0962 -> Width.Combining
            c == 0x0963 -> Width.Combining
            else -> Width.Narrow
        }
        c >= 0x0980 && c <= 0x0A02 -> when {
            c == 0x0981 -> Width.Combining
            c == 0x09BC -> Width.Combining
            c <= 0x09C0 -> Width.Narrow
            c <= 0x09C4 -> Width.Combining
            c == 0x09CD -> Width.Combining
            c <= 0x09E1 -> Width.Narrow
            c <= 0x09E3 -> Width.Combining
            c <= 0x09FD -> Width.Narrow
            else -> Width.Combining
        }
        c >= 0x0370 && c <= 0x058F -> when {
            c <= 0x0482 -> Width.Narrow
            c <= 0x0489 -> Width.Combining
            else -> Width.Narrow
        }
        c >= 0x2E80 && c <= 0x324F -> when {
            c <= 0x3029 -> Width.Wide
            c <= 0x302D -> Width.Combining
            c == 0x303F -> Width.Narrow
            c <= 0x3096 -> Width.Wide
            c <= 0x309A -> Width.Combining
            c <= 0x3247 -> Width.Wide
            else -> Width.Ambiguous
        }
        c >= 0xAC00 && c <= 0xD7A3 -> Width.Wide
        c >= 0x0C00 && c <= 0x0C80 -> when {
            c == 0x0C00 -> Width.Combining
            c == 0x0C04 -> Width.Combining
            c <= 0x0C39 -> Width.Narrow
            c == 0x0C3D -> Width.Narrow
            c <= 0x0C40 -> Width.Combining
            c <= 0x0C44 -> Width.Narrow
            c <= 0x0C56 -> Width.Combining
            c == 0x0C62 -> Width.Combining
            c == 0x0C63 -> Width.Combining
            else -> Width.Narrow
        }
        c >= 0x0B80 && c <= 0x0BFF -> when {
            c <= 0x0B82 -> Width.Combining
            c == 0x0BC0 -> Width.Combining
            c == 0x0BCD -> Width.Combining
            c <= 0x0BCC -> Width.Narrow
            else -> Width.Narrow
        }
        c >= 0x0E00 && c <= 0x0E7F -> when {
            c == 0x0E31 -> Width.Combining
            c >= 0x0E34 && c <= 0x0E3A -> Width.Combining
            c >= 0x0E47 && c <= 0x0E4E -> Width.Combining
            else -> Width.Narrow
        }
        c == 0x200D -> Width.ZWJ
        c == 0xFE0F -> Width.EmojiPresentationMod
        c >= 0xF900 && c <= 0xFAFF -> Width.Wide
        c >= 0xFF01 && c <= 0xFF60 -> Width.Wide
        c >= 0xFFE0 && c <= 0xFFE6 -> Width.Wide
        c >= 0x20000 && c <= 0x3FFFF -> Width.Wide
        c >= 0x1F3FB && c <= 0x1F3FF -> Width.EmojiSkinToneMod
        else -> null
    }

    fun wide(c: Int): Width? = when {
        c <= 0x001F -> Width.Control
        c <= 0x007E -> Width.Narrow
        c >= 0x3250 && c <= 0xA4CF -> Width.Wide
        c >= 0x2E80 && c <= 0x324F -> when {
            c <= 0x3029 -> Width.Wide
            c <= 0x302D -> Width.Combining
            c == 0x303F -> Width.Narrow
            c <= 0x3096 -> Width.Wide
            c <= 0x309A -> Width.Combining
            c <= 0x3247 -> Width.Wide
            else -> Width.Ambiguous
        }
        c >= 0xAC00 && c <= 0xD7A3 -> Width.Wide
        c >= 0x0300 && c <= 0x036F -> Width.Combining
        c >= 0x0600 && c <= 0x06FF -> when {
            c <= 0x0605 -> Width.Control
            c <= 0x060F -> Width.Narrow
            c <= 0x061A -> Width.Combining
            c == 0x061B -> Width.Narrow
            c <= 0x061C -> Width.Control
            c <= 0x064A -> Width.Narrow
            c <= 0x065F -> Width.Combining
            c == 0x0670 -> Width.Combining
            c <= 0x06D5 -> Width.Narrow
            c <= 0x06DC -> Width.Combining
            c == 0x06DD -> Width.Control
            c == 0x06DE -> Width.Narrow
            c <= 0x06E4 -> Width.Combining
            c <= 0x06E6 -> Width.Narrow
            c == 0x06E9 -> Width.Narrow
            c <= 0x06ED -> Width.Combining
            else -> Width.Narrow
        }
        c >= 0x0900 && c <= 0x097F -> when {
            c <= 0x0902 -> Width.Combining
            c <= 0x0939 -> Width.Narrow
            c == 0x093A -> Width.Combining
            c == 0x093C -> Width.Combining
            c <= 0x0940 -> Width.Narrow
            c <= 0x0948 -> Width.Combining
            c == 0x094D -> Width.Combining
            c <= 0x0950 -> Width.Narrow
            c <= 0x0957 -> Width.Combining
            c == 0x0962 -> Width.Combining
            c == 0x0963 -> Width.Combining
            else -> Width.Narrow
        }
        c >= 0x0980 && c <= 0x0A02 -> when {
            c == 0x0981 -> Width.Combining
            c == 0x09BC -> Width.Combining
            c <= 0x09C0 -> Width.Narrow
            c <= 0x09C4 -> Width.Combining
            c == 0x09CD -> Width.Combining
            c <= 0x09E1 -> Width.Narrow
            c <= 0x09E3 -> Width.Combining
            c <= 0x09FD -> Width.Narrow
            else -> Width.Combining
        }
        c >= 0x0C00 && c <= 0x0C80 -> when {
            c == 0x0C00 -> Width.Combining
            c == 0x0C04 -> Width.Combining
            c <= 0x0C39 -> Width.Narrow
            c == 0x0C3D -> Width.Narrow
            c <= 0x0C40 -> Width.Combining
            c <= 0x0C44 -> Width.Narrow
            c <= 0x0C56 -> Width.Combining
            c == 0x0C62 -> Width.Combining
            c == 0x0C63 -> Width.Combining
            else -> Width.Narrow
        }
        c >= 0x0B80 && c <= 0x0BFF -> when {
            c <= 0x0B82 -> Width.Combining
            c == 0x0BC0 -> Width.Combining
            c == 0x0BCD -> Width.Combining
            c <= 0x0BCC -> Width.Narrow
            else -> Width.Narrow
        }
        c >= 0x0E00 && c <= 0x0E7F -> when {
            c == 0x0E31 -> Width.Combining
            c >= 0x0E34 && c <= 0x0E3A -> Width.Combining
            c >= 0x0E47 && c <= 0x0E4E -> Width.Combining
            else -> Width.Narrow
        }
        c == 0x200D -> Width.ZWJ
        c == 0xFE0F -> Width.EmojiPresentationMod
        c >= 0xF900 && c <= 0xFAFF -> Width.Wide
        c >= 0xFF01 && c <= 0xFF60 -> Width.Wide
        c >= 0xFFE0 && c <= 0xFFE6 -> Width.Wide
        c >= 0x20000 && c <= 0x3FFFF -> Width.Wide
        c >= 0x1F3FB && c <= 0x1F3FF -> Width.EmojiSkinToneMod
        else -> null
    }
}
