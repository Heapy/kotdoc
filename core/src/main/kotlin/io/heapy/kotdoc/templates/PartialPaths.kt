// System.FilePath.Internal 1.5.4.0; BSD-3-Clause, licenses/Filepath-BSD-3-Clause.txt.
package io.heapy.kotdoc.templates

/** Original FilePath string operations; these do not normalize or resolve paths. */
enum class PartialPaths {
    Posix, Windows;
    private fun separator(c: Char) = c == '/' || this == Windows && c == '\\'
    private fun letter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
    private fun addSlash(prefix: String, rest: String): Pair<String, String> {
        val n = rest.takeWhile(::separator).length
        return (prefix + rest.take(n)) to rest.drop(n)
    }
    private fun driveLetter(s: String): Pair<String, String>? {
        if (s.length < 2 || !letter(s[0]) || s[1] != ':') return null
        return addSlash(s.take(2), s.drop(2))
    }
    private fun shareName(s: String): Pair<String, String> {
        val n = s.indexOfFirst(::separator).takeIf { it >= 0 } ?: s.length
        return addSlash(s.take(n), s.drop(n))
    }
    private fun unc(s: String): Pair<String, String>? {
        if (s.length < 4 || !separator(s[0]) || !separator(s[1]) || s[2] != '?' || !separator(s[3])) return null
        val tail = s.drop(4)
        val result = if (tail.length >= 4 && tail[0] in "uU" && tail[1] in "nN" && tail[2] in "cC" && separator(tail[3])) {
            val (a, b) = shareName(tail.drop(4)); (tail.take(4) + a) to b
        } else driveLetter(tail) ?: return null
        return (s.take(4) + result.first) to result.second
    }
    private fun splitDrive(s: String): Pair<String, String> {
        if (this == Posix) { val n = s.takeWhile { it == '/' }.length; return s.take(n) to s.drop(n) }
        return driveLetter(s) ?: unc(s) ?: if (s.length >= 2 && separator(s[0]) && separator(s[1])) {
            val (a, b) = shareName(s.drop(2)); (s.take(2) + a) to b
        } else "" to s
    }
    private fun extension(s: String): String {
        val at = s.lastIndexOf('.')
        if (at < 0) return ""
        if (this == Windows && splitDrive(s.take(at + 1)).second.isEmpty()) return ""
        return if (s.drop(at + 1).any(::separator)) "" else s.drop(at)
    }
    private fun splitName(s: String): Pair<String, String> {
        val n = s.indexOfLast(::separator) + 1
        val dir = s.take(n); val file = s.drop(n)
        if (this == Posix) return dir to file
        if (dir.isEmpty()) return driveLetter(s) ?: ("" to s)
        if (dir.length >= 2 && separator(dir[0])) {
            val drive = unc(dir)
            val incomplete = drive != null && drive.second.isEmpty() &&
                !(drive.first.lastOrNull()?.let(::separator) == true && drive.first.trimEnd(::separator).endsWith(':'))
            if (dir.length == 2 || incomplete) return s to ""
            if (dir.length == 4 && separator(dir[1]) && dir[2] == '?' && separator(dir[3])) {
                driveLetter(file)?.let { return (dir + it.first) to it.second }
            }
        }
        return dir to file
    }
    private fun combineAlways(a: String, b: String): String = when {
        a.isEmpty() -> b; b.isEmpty() -> a; separator(a.last()) -> a + b
        this == Windows && a.length == 2 && letter(a[0]) && a[1] == ':' -> a + b
        else -> a + (if (this == Windows) "\\" else "/") + b
    }
    fun resolve(templatePath: String, partial: String): String {
        val dir = splitName(templatePath).first
        return if (extension(partial).isEmpty()) {
            val ext = extension(splitName(templatePath).second)
            val (drive, file) = splitDrive(partial)
            val name = if (ext.isEmpty()) partial else combineAlways(drive, file + ext)
            combineAlways(dir, name)
        } else if (partial.firstOrNull()?.let(::separator) == true || splitDrive(partial).first.isNotEmpty()) partial
        else combineAlways(dir, partial)
    }
    companion object {
        val System = if (java.io.File.separatorChar == '\\') Windows else Posix
    }
}
