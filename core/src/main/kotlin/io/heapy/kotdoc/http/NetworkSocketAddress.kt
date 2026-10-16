// Network.Socket.Types/Info/ReadShow 3.2.9.0. University of Glasgow 2002-2010,
// Johan Tibell 2007-2010. Complete licenses/Network-BSD-3-Clause.txt and vendor source.
package io.heapy.kotdoc.http

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.IoErrorType
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.resources.GhcForeignCString
import kotlinx.serialization.json.*
import java.nio.ByteOrder

/** Original named-pattern mappings and C layouts, observed from native headers
 * and the pinned network library; values are not JVM network constants. */
class NetworkSocketAbi(val data: JsonObject) {
    val pointerSize=data.getValue("pointerSize").jsonPrimitive.int
    val littleEndian=data.getValue("littleEndian").jsonPrimitive.boolean
    val linuxAbstractUnix=data.getValue("linuxAbstractUnix").jsonPrimitive.boolean
    val families=table("families")
    val socketTypes=table("socketTypes")
    val aiFlags=table("aiFlags")
    private val info=data.getValue("addrInfo").jsonObject
    private val address=data.getValue("sockAddr").jsonObject
    fun info(key: String)=info.getValue(key).jsonPrimitive.int
    fun address(key: String)=address.getValue(key).jsonPrimitive.int
    private fun table(key: String)=data.getValue(key).jsonArray.map {it.jsonArray.let {v->v[0].jsonPrimitive.content to v[1].jsonPrimitive.int}}
    fun family(name: String)=NetworkSocketFamily(families.first {it.first==name}.second)
    fun socketType(name: String)=NetworkSocketType(socketTypes.first {it.first==name}.second)
    fun packFlags(flags: List<NetworkAddressFlag>): Int = aiFlags.fold(0) {mask,(name,value)->if(flags.any {it.name==name}) mask or value else mask}
    fun unpackFlags(mask: Int): List<NetworkAddressFlag> {
        var remaining=mask
        return buildList {for((name,value) in aiFlags) if(remaining and value!=0) {add(NetworkAddressFlag.valueOf(name));remaining=remaining and value.inv()}}
    }
    fun flagImplemented(flag: NetworkAddressFlag)=packFlags(listOf(flag))!=0
    companion object {
        fun load(name: String): NetworkSocketAbi {
            val stream=checkNotNull(NetworkSocketAbi::class.java.getResourceAsStream("/io/heapy/kotdoc/http/network-abi-$name.json"))
            return stream.bufferedReader().use {NetworkSocketAbi(Json.parseToJsonElement(it.readText()).jsonObject)}
        }
        val currentPosix: NetworkSocketAbi by lazy {
            check(Platform.isMac() || Platform.isLinux()) {"Native POSIX address lookup requires Darwin or Linux/glibc"}
            val abi=load(if(Platform.isMac()) "macos-64" else "linux-64")
            check(abi.pointerSize==Native.POINTER_SIZE && abi.littleEndian==(ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN)) {"Unsupported native socket ABI"}
            abi
        }
    }
}
private fun namedInteger(value: Int,table: List<Pair<String,Int>>,constructor: String,precedence: Int): String {
    table.firstOrNull {it.second==value}?.let {return it.first}
    val body="$constructor ${if(value<0) "($value)" else value}"
    return if(precedence>10) "($body)" else body
}
data class NetworkSocketFamily(val value: Int): Comparable<NetworkSocketFamily> {
    val supported get()=value != -1
    override fun compareTo(other: NetworkSocketFamily)=value.compareTo(other.value)
    fun show(abi: NetworkSocketAbi,precedence: Int=0)=namedInteger(value,abi.families,"GeneralFamily",precedence)
}
data class NetworkSocketType(val value: Int): Comparable<NetworkSocketType> {
    val supported get()=value != -1
    override fun compareTo(other: NetworkSocketType)=value.compareTo(other.value)
    fun show(abi: NetworkSocketAbi,precedence: Int=0)=namedInteger(value,abi.socketTypes,"GeneralSocketType",precedence)
}
enum class NetworkAddressFlag { AI_ADDRCONFIG,AI_ALL,AI_CANONNAME,AI_NUMERICHOST,AI_NUMERICSERV,AI_PASSIVE,AI_V4MAPPED }
data class NetworkHostAddress6(val w3: UInt,val w2: UInt,val w1: UInt,val w0: UInt): Comparable<NetworkHostAddress6> {
    fun words()=listOf(w3,w2,w1,w0)
    fun tuple()=words().flatMap {listOf((it shr 16).toUShort(),it.toUShort())}
    override fun compareTo(other: NetworkHostAddress6): Int {for((a,b) in words().zip(other.words())) {val c=a.compareTo(b);if(c!=0)return c};return 0}
    companion object {fun fromTuple(parts: List<UShort>)=parts.chunked(2).map {(it[0].toUInt() shl 16) or it[1].toUInt()}.let {NetworkHostAddress6(it[0],it[1],it[2],it[3])}}
}
object NetworkHostAddresses {
    fun toTuple(host: UInt,littleEndian: Boolean=ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN)=List(4) {i->(host shr (if(littleEndian) i*8 else (3-i)*8)).toUByte()}
    fun fromTuple(bytes: List<UByte>,littleEndian: Boolean=ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN)=bytes.foldIndexed(0u) {i,host,b->host or (b.toUInt() shl (if(littleEndian) i*8 else (3-i)*8))}
    fun show4(host: UInt,littleEndian: Boolean)=toTuple(host,littleEndian).joinToString(".")
    fun show6(host: NetworkHostAddress6): String {
        if(host.w3==0u && host.w2==0u && host.w1==65535u) return "::ffff:"+show4(host.w0,false)
        if(host.w3==0u && host.w2==0u && host.w1==0u && host.w0>=65536u) return "::"+show4(host.w0,false)
        val fields=host.tuple();var diff=0;var best=0;var end=0
        for(i in fields.indices) {
            diff=if(fields[i]==0.toUShort()) diff-1 else 0
            if(diff<best) {best=diff;end=i+1}
        }
        fun show(parts: List<UShort>)=parts.joinToString(":") {it.toString(16)}
        return if(-best>1) show(fields.take(end+best))+"::"+show(fields.drop(end)) else show(fields)
    }
}
sealed class NetworkSocketAddress: Comparable<NetworkSocketAddress> {
    data class Inet(val port: UShort,val host: UInt): NetworkSocketAddress()
    data class Inet6(val port: UShort,val flow: UInt,val host: NetworkHostAddress6,val scope: UInt): NetworkSocketAddress()
    data class Unix(val path: CharacterString): NetworkSocketAddress()
    private val rank get()=when(this) {is Inet->0;is Inet6->1;is Unix->2}
    override fun compareTo(other: NetworkSocketAddress): Int {
        rank.compareTo(other.rank).let {if(it!=0)return it}
        return when(this) {
            is Inet->(other as Inet).let {port.compareTo(it.port).takeIf {n->n!=0} ?: host.compareTo(it.host)}
            is Inet6->(other as Inet6).let {port.compareTo(it.port).takeIf {n->n!=0} ?: flow.compareTo(it.flow).takeIf {n->n!=0} ?: host.compareTo(it.host).takeIf {n->n!=0} ?: scope.compareTo(it.scope)}
            is Unix->path.compareTo((other as Unix).path)
        }
    }
    fun shownPoints(abi: NetworkSocketAbi): IntArray=when(this) {
        is Unix->path.points()
        is Inet->"${NetworkHostAddresses.show4(host,abi.littleEndian)}:$port".codePoints().toArray()
        is Inet6->"[${NetworkHostAddresses.show6(host)}]:$port".codePoints().toArray()
    }
    fun show(abi: NetworkSocketAbi)=buildString {shownPoints(abi).forEach(::appendCodePoint)}
}
data class NetworkAddressInfo(
    val flags: List<NetworkAddressFlag>,val family: NetworkSocketFamily,val socketType: NetworkSocketType,
    val protocol: Int,val address: NetworkSocketAddress,val canonicalName: CharacterString?,
) {
    fun shownPoints(abi: NetworkSocketAbi,precedence: Int=0): IntArray {
        val start="AddrInfo {addrFlags = ${flags.joinToString(",","[","]")}, addrFamily = ${family.show(abi)}, addrSocketType = ${socketType.show(abi)}, addrProtocol = $protocol, addrAddress = "
        val end=", addrCanonName = ${canonicalName?.let {"Just "+HaskellString.quoteCodePoints(it.points())} ?: "Nothing"}}"
        val body=start.codePoints().toArray()+address.shownPoints(abi)+end.codePoints().toArray()
        return if(precedence>10) intArrayOf(40)+body+intArrayOf(41) else body
    }
    fun show(abi: NetworkSocketAbi,precedence: Int=0)=buildString {shownPoints(abi,precedence).forEach(::appendCodePoint)}
    companion object {
        fun defaultHints(abi: NetworkSocketAbi)=NetworkAddressInfo(emptyList(),abi.family("AF_UNSPEC"),abi.socketType("NoSocketType"),0,NetworkSocketAddress.Inet(0u,0u),null)
    }
}
class NetworkSocketMemory(val abi: NetworkSocketAbi,private val codec: ()->GhcForeignCString=GhcForeignCString::currentPosix) {
    fun size(address: NetworkSocketAddress)=when(address) {
        is NetworkSocketAddress.Inet->abi.address("v4Size")
        is NetworkSocketAddress.Inet6->abi.address("v6Size")
        is NetworkSocketAddress.Unix->if(abi.linuxAbstractUnix && address.path.points().firstOrNull()==0) abi.address("familySize")+address.path.points().size else abi.address("unixSize")
    }
    private fun family(p: Pointer,value: Int) {if(abi.address("familySize")==1) p.setByte(abi.address("familyOffset").toLong(),value.toByte()) else p.setShort(abi.address("familyOffset").toLong(),value.toShort())}
    private fun port(p: Pointer,offset: Int,value: UShort) {p.setByte(offset.toLong(),(value.toInt() ushr 8).toByte());p.setByte(offset+1L,value.toByte())}
    private fun port(p: Pointer,offset: Int)=(((p.getByte(offset.toLong()).toInt() and 255) shl 8) or (p.getByte(offset+1L).toInt() and 255)).toUShort()
    fun poke(p: Pointer,address: NetworkSocketAddress) {
        if(address is NetworkSocketAddress.Unix) {
            val path=address.path.points();val max=abi.address("unixMax")
            if(path.size>=max) error("pokeSockAddr: path is too long in SockAddrUnix ${HaskellString.quoteCodePoints(path)}, length ${path.size}, unixPathMax $max")
        }
        p.setMemory(0,size(address).toLong(),0)
        val (name,length)=when(address) {is NetworkSocketAddress.Inet->"AF_INET" to abi.address("v4Size");is NetworkSocketAddress.Inet6->"AF_INET6" to abi.address("v6Size");is NetworkSocketAddress.Unix->"AF_UNIX" to abi.address("unixSize")}
        if(abi.address("lengthOffset")>=0) p.setByte(abi.address("lengthOffset").toLong(),length.toByte())
        family(p,abi.family(name).value)
        when(address) {
            is NetworkSocketAddress.Inet->{port(p,abi.address("v4Port"),address.port);p.setInt(abi.address("v4Host").toLong(),address.host.toInt())}
            is NetworkSocketAddress.Inet6->{
                port(p,abi.address("v6Port"),address.port);p.setInt(abi.address("v6Flow").toLong(),address.flow.toInt());p.setInt(abi.address("v6Scope").toLong(),address.scope.toInt())
                for((i,word) in address.host.words().withIndex()) for(j in 0..3) p.setByte(abi.address("v6Host")+4L*i+j,(word shr ((3-j)*8)).toByte())
            }
            is NetworkSocketAddress.Unix->address.path.points().forEachIndexed {i,cp->p.setByte(abi.address("unixPath")+i.toLong(),cp.toByte())}
        }
    }
    fun peek(p: Pointer): NetworkSocketAddress {
        val family=if(abi.address("familySize")==1) p.getByte(abi.address("familyOffset").toLong()).toInt() and 255 else p.getShort(abi.address("familyOffset").toLong()).toInt() and 65535
        return when(family) {
            abi.family("AF_INET").value->NetworkSocketAddress.Inet(port(p,abi.address("v4Port")),p.getInt(abi.address("v4Host").toLong()).toUInt())
            abi.family("AF_INET6").value->{
                val words=List(4) {i->(0..3).fold(0u) {w,j->(w shl 8) or (p.getByte(abi.address("v6Host")+4L*i+j).toInt() and 255).toUInt()}}
                NetworkSocketAddress.Inet6(port(p,abi.address("v6Port")),p.getInt(abi.address("v6Flow").toLong()).toUInt(),NetworkHostAddress6(words[0],words[1],words[2],words[3]),p.getInt(abi.address("v6Scope").toLong()).toUInt())
            }
            abi.family("AF_UNIX").value->{val path=p.share(abi.address("unixPath").toLong());NetworkSocketAddress.Unix(CharacterString(path.getByteArray(0,path.indexOf(0,0).toInt()).map {it.toInt() and 255}.toIntArray()))}
            else->throw HttpGhcIOException(GhcIoError.userError("Network.Socket.Types.peekSockAddr: address family '$family' not supported."))
        }
    }
    fun pokeInfo(p: Pointer,info: NetworkAddressInfo) {
        for((name,value) in listOf("flags" to abi.packFlags(info.flags),"family" to info.family.value,"type" to info.socketType.value,"protocol" to info.protocol)) p.setInt(abi.info(name).toLong(),value)
        p.setMemory(abi.info("addrlen").toLong(),abi.info("pokeAddrlenSize").toLong(),0)
        for(name in listOf("addr","canon","next")) p.setPointer(abi.info(name).toLong(),null)
    }
    fun peekInfo(p: Pointer): NetworkAddressInfo {
        val flags=abi.unpackFlags(p.getInt(abi.info("flags").toLong()));val family=NetworkSocketFamily(p.getInt(abi.info("family").toLong()))
        val type=NetworkSocketType(p.getInt(abi.info("type").toLong()));val protocol=p.getInt(abi.info("protocol").toLong())
        val address=peek(checkNotNull(p.getPointer(abi.info("addr").toLong())))
        val canon=p.getPointer(abi.info("canon").toLong())?.let {CharacterString(codec().decode(it))}
        return NetworkAddressInfo(flags,family,type,protocol,address,canon)
    }
    fun followAddrInfo(root: Pointer?): List<NetworkAddressInfo> {
        if(root==null) throw HttpGhcIOException(GhcIoError(IoErrorType.NoSuchThing,Chars.fromString("getaddrinfo must return at least one addrinfo")))
        return buildList {
            var p: Pointer?=root
            while(p!=null) {
                if(Thread.interrupted()) throw InterruptedException()
                val current=p;add(peekInfo(current));p=current.getPointer(abi.info("next").toLong())
            }
        }
    }

}
interface NetworkNameFunctions {
    fun getAddrInfo(host: Pointer?,service: Pointer?,hints: Pointer?,result: PointerByReference): Int
    fun freeAddrInfo(pointer: Pointer?)
    fun gaiStrError(code: Int): Pointer
}
object NetworkPosixNameFunctions: NetworkNameFunctions {
    private val libc by lazy {NativeLibrary.getInstance(Platform.C_LIBRARY_NAME)}
    override fun getAddrInfo(host: Pointer?,service: Pointer?,hints: Pointer?,result: PointerByReference)=libc.getFunction("getaddrinfo").invokeInt(arrayOf(host,service,hints,result))
    override fun freeAddrInfo(pointer: Pointer?) {libc.getFunction("freeaddrinfo").invokeVoid(arrayOf(pointer))}
    override fun gaiStrError(code: Int): Pointer=libc.getFunction("gai_strerror").invokePointer(arrayOf(code))
}
/** Actual POSIX getaddrinfo. OS results are copied before freeaddrinfo, preserving
 * order and duplicate entries. No JVM DNS cache or InetAddress parser is used. */
class NetworkNameResolver(
    val abi: NetworkSocketAbi,private val functions: NetworkNameFunctions,
    private val encoding: ()->GhcForeignCString=GhcForeignCString::currentPosix,
    private val initializeSockets: ()->Unit,
    private val darwin: Boolean,
) {
    private val memory=NetworkSocketMemory(abi,encoding)
    fun getAddrInfo(hints: NetworkAddressInfo?,host: CharacterString?,service: CharacterString?): List<NetworkAddressInfo> {
        initializeSockets()
        val filtered=if(darwin && service==null && hints!=null) hints.copy(flags=hints.flags.toMutableList().also {it.remove(NetworkAddressFlag.AI_NUMERICSERV)}) else hints
        fun <A> string(text: CharacterString?,action: (Pointer?)->A): A=if(text==null) action(null) else encoding().withCString(text,action)
        fun <A> withHints(action: (Pointer?)->A): A=if(filtered==null) action(null) else Memory(abi.info("size").toLong()).use {p->memory.pokeInfo(p,filtered);action(p)}
        return string(host) {node->string(service) {svc->withHints {ptr->
            val out=PointerByReference();val error=functions.getAddrInfo(node,svc,ptr,out)
            if(error!=0) {
                val rawDescription=functions.gaiStrError(error)
                val description=CharacterString(encoding().decode(rawDescription))
                val location="Network.Socket.getAddrInfo (called with preferred socket type/protocol: ${hints?.show(abi) ?: "Nothing"}, host name: ${host?.let {HaskellString.quoteCodePoints(it.points())} ?: "Nothing"}, service name: ${service?.let {HaskellString.quoteCodePoints(it.points())} ?: "Nothing"})"
                throw HttpGhcIOException(GhcIoError(IoErrorType.NoSuchThing,Chars.fromString(location),description))
            }
            val root=out.value
            try { memory.followAddrInfo(root)
            } finally {functions.freeAddrInfo(root)}
        }}}
    }
    companion object {
        fun posix()=NetworkNameResolver(NetworkSocketAbi.currentPosix,NetworkPosixNameFunctions,GhcForeignCString::currentPosix,{},Platform.isMac())
    }
}
