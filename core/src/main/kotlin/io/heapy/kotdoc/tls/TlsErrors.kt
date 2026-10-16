// Network.TLS.Error, tls 2.4.6, (c) Vincent Hanquez and contributors.
// BSD-3-Clause; licenses/tls-BSD-3-Clause.txt, complete pinned source in vendor/.
package io.heapy.kotdoc.tls

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.http.HttpExceptionShow
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

@JvmInline value class TlsAlertLevel(val value: UByte) {
    fun show() = when(value.toInt()) {1->"AlertLevel_Warning";2->"AlertLevel_Fatal";else->"AlertLevel $value"}
}
@JvmInline value class TlsAlertDescription(val value: UByte) {
    fun show() = names[value.toInt()] ?: "AlertDescription $value"
    companion object {
        private val names=mapOf(0 to "CloseNotify",10 to "UnexpectedMessage",20 to "BadRecordMac",21 to "DecryptionFailed",
            22 to "RecordOverflow",30 to "DecompressionFailure",40 to "HandshakeFailure",42 to "BadCertificate",
            43 to "UnsupportedCertificate",44 to "CertificateRevoked",45 to "CertificateExpired",46 to "CertificateUnknown",
            47 to "IllegalParameter",48 to "UnknownCa",49 to "AccessDenied",50 to "DecodeError",51 to "DecryptError",
            60 to "ExportRestriction",70 to "ProtocolVersion",71 to "InsufficientSecurity",80 to "InternalError",
            86 to "InappropriateFallback",90 to "UserCanceled",100 to "NoRenegotiation",109 to "MissingExtension",
            110 to "UnsupportedExtension",111 to "CertificateUnobtainable",112 to "UnrecognizedName",
            113 to "BadCertificateStatusResponse",114 to "BadCertificateHashValue",115 to "UnknownPskIdentity",
            116 to "CertificateRequired",117 to "GeneralError",120 to "NoApplicationProtocol",121 to "EchRequired")
    }
}
sealed interface TlsError {
    data class Misc(val description: CharacterString): TlsError {constructor(s: String):this(Chars.fromString(s))}
    data class Protocol(val description: CharacterString,val alert: TlsAlertDescription): TlsError
    data class ProtocolWarning(val description: CharacterString,val alert: TlsAlertDescription): TlsError
    data class Certificate(val description: CharacterString): TlsError
    data class HandshakePolicy(val description: CharacterString): TlsError
    data object EOF: TlsError
    data class Packet(val description: CharacterString): TlsError
    data class UnexpectedPacket(val expected: CharacterString,val actual: CharacterString): TlsError
    data class PacketParsing(val description: CharacterString): TlsError
    data object TcpTerminate: TlsError
    fun show(precedence: Int=0): String {
        fun quote(s: CharacterString)=HaskellString.quoteCodePoints(s.points())
        val result=when(this) {
            is Misc->"Error_Misc ${quote(description)}"
            is Protocol->"Error_Protocol ${quote(description)} ${alert.show()}"
            is ProtocolWarning->"Error_Protocol_Warning ${quote(description)} ${alert.show()}"
            is Certificate->"Error_Certificate ${quote(description)}"
            is HandshakePolicy->"Error_HandshakePolicy ${quote(description)}"
            EOF->"Error_EOF"
            is Packet->"Error_Packet ${quote(description)}"
            is UnexpectedPacket->"Error_Packet_unexpected ${quote(expected)} ${quote(actual)}"
            is PacketParsing->"Error_Packet_Parsing ${quote(description)}"
            TcpTerminate->"Error_TCP_Terminate"
        }
        return if(this!=EOF && this!=TcpTerminate && precedence>10) "($result)" else result
    }
}
sealed class TlsException: RuntimeException(),HttpExceptionShow {
    data class Terminated(val fatal: Boolean,val description: CharacterString,val error: TlsError): TlsException()
    data class HandshakeFailed(val error: TlsError): TlsException()
    data class PostHandshake(val error: TlsError): TlsException()
    data class Uncontextualized(val error: TlsError): TlsException()
    data object ConnectionNotEstablished: TlsException()
    data object MissingHandshake: TlsException()
    override val message get()=showsPrec(0)
    override fun showsPrec(precedence: Int): String {
        val result=when(this) {
            is Terminated->"Terminated ${if(fatal) "True" else "False"} ${HaskellString.quoteCodePoints(description.points())} ${error.show(11)}"
            is HandshakeFailed->"HandshakeFailed ${error.show(11)}"
            is PostHandshake->"PostHandshake ${error.show(11)}"
            is Uncontextualized->"Uncontextualized ${error.show(11)}"
            ConnectionNotEstablished->"ConnectionNotEstablished"
            MissingHandshake->"MissingHandshake"
        }
        return if(this!=ConnectionNotEstablished && this!=MissingHandshake && precedence>10) "($result)" else result
    }
}
