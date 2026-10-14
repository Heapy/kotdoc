// GHC 9.12.2 GHC.Internal.IO.Exception and IO.Handle.Types.
// Full original BSD notices and unchanged source are retained in licenses/ and vendor/.
package io.heapy.kotdoc.errors

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars

enum class IoErrorType(val shown: String) {
    AlreadyExists("already exists"), NoSuchThing("does not exist"), ResourceBusy("resource busy"),
    ResourceExhausted("resource exhausted"), EOF("end of file"), IllegalOperation("illegal operation"),
    PermissionDenied("permission denied"), UserError("user error"), UnsatisfiedConstraints("unsatisfied constraints"),
    SystemError("system error"), ProtocolError("protocol error"), OtherError("failed"), InvalidArgument("invalid argument"),
    InappropriateType("inappropriate type"), HardwareFault("hardware fault"), UnsupportedOperation("unsupported operation"),
    TimeExpired("timeout"), ResourceVanished("resource vanished"), Interrupted("interrupted");
    override fun toString() = shown
}

/** Source Handle equality uses constructor and read-side cell identity, not name. */
sealed class ErrorHandle(val name: CharacterString, internal val readIdentity: Any) {
    class File(name: CharacterString, identity: Any = Any()) : ErrorHandle(name, identity)
    class Duplex(name: CharacterString, readIdentity: Any = Any(), val writeIdentity: Any = Any()) : ErrorHandle(name, readIdentity)
    override fun equals(other: Any?) = other is ErrorHandle && javaClass == other.javaClass && readIdentity === other.readIdentity
    override fun hashCode() = 31 * javaClass.hashCode() + System.identityHashCode(readIdentity)
    fun show(): CharacterString = CharacterStringChars.concat(listOf(CharacterStringChars.fromString("{handle: "), name, CharacterStringChars.fromString("}")))
}

class GhcIoError(
    val type: IoErrorType,
    val location: CharacterString = CharacterStringChars.empty,
    val description: CharacterString = CharacterStringChars.empty,
    val filename: CharacterString? = null,
    val errno: Int? = null,
    val handle: ErrorHandle? = null,
) {
    fun show(): CharacterString = CharacterStringChars.concat(buildList {
        fun ascii(text: String) { add(CharacterStringChars.fromString(text)) }
        if (filename != null) { add(filename); ascii(": ") }
        else if (handle != null) { add(handle.show()); ascii(": ") }
        if (location.points().isNotEmpty()) { add(location); ascii(": ") }
        ascii(type.shown)
        if (description.points().isNotEmpty()) { ascii(" ("); add(description); ascii(")") }
    })
    override fun toString() = buildString { show().points().forEach(::appendCodePoint) }
    fun displayedText() = TextChars.fromPoints(show().points())
    override fun equals(other: Any?) = other is GhcIoError && type == other.type && description == other.description &&
        handle == other.handle && location == other.location && errno == other.errno && filename == other.filename
    override fun hashCode() = listOf(type, description, handle, location, errno, filename).hashCode()
    companion object {
        fun userError(description: CharacterString) = GhcIoError(IoErrorType.UserError, description = description)
        fun userError(description: String) = userError(CharacterStringChars.fromString(description))
    }
}
