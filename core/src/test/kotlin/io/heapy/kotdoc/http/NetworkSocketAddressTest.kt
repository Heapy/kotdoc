package io.heapy.kotdoc.http

import com.sun.jna.Memory
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import io.heapy.kotdoc.errors.*
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.resources.GhcForeignCString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

class NetworkSocketAddressTest {
    private val actual=if(Platform.isMac()) "macos-64" else "linux-64"
    private fun text(v: JsonObject,k: String,default: String="")=v[k]?.takeUnless {it is JsonNull}?.jsonPrimitive?.content ?: default
    private fun number(v: JsonObject,k: String,default: Long=0)=v[k]?.jsonPrimitive?.longOrNull ?: default
    private fun present(v: JsonObject,k: String)=v[k]?.takeUnless {it is JsonNull}
    private fun points(v: JsonElement)=v.jsonArray.map {it.jsonPrimitive.int}.toIntArray()
    private fun json(p: IntArray)=JsonArray(p.map(::JsonPrimitive))
    private fun bytes(b: ByteArray)=JsonArray(b.map {JsonPrimitive(it.toInt() and 255)})
    private fun address(v: JsonElement): NetworkSocketAddress=v.jsonObject.let {o->when(text(o,"kind")) {
        "v4"->NetworkSocketAddress.Inet(number(o,"port").toUShort(),number(o,"host").toUInt())
        "v6"->o.getValue("host").jsonArray.map {it.jsonPrimitive.long.toUInt()}.let {NetworkSocketAddress.Inet6(number(o,"port").toUShort(),number(o,"flow").toUInt(),NetworkHostAddress6(it[0],it[1],it[2],it[3]),number(o,"scope").toUInt())}
        "unix"->NetworkSocketAddress.Unix(CharacterString(points(o.getValue("path"))))
        else->error(o)
    }}
    private fun info(v: JsonElement): NetworkAddressInfo=v.jsonObject.let {o->NetworkAddressInfo(
        o["flags"]?.jsonArray?.map {NetworkAddressFlag.valueOf(it.jsonPrimitive.content)} ?: emptyList(),NetworkSocketFamily(number(o,"family").toInt()),NetworkSocketType(number(o,"type").toInt()),number(o,"protocol").toInt(),o["address"]?.let(::address) ?: NetworkSocketAddress.Inet(0u,0u),present(o,"canon")?.let {CharacterString(points(it))},
    )}
    private fun view(a: NetworkSocketAddress,abi: NetworkSocketAbi)=buildJsonObject {
        put("show",json(a.shownPoints(abi)));put("prec11",json(a.shownPoints(abi)+"TAIL".codePoints().toArray()))
        when(a) {
            is NetworkSocketAddress.Inet->{put("kind","v4");put("port",a.port.toInt());put("host",a.host.toLong())}
            is NetworkSocketAddress.Inet6->{put("kind","v6");put("port",a.port.toInt());put("flow",a.flow.toLong());put("host",JsonArray(a.host.words().map {JsonPrimitive(it.toLong())}));put("scope",a.scope.toLong())}
            is NetworkSocketAddress.Unix->{put("kind","unix");put("path",json(a.path.points()))}
        }
    }
    private fun view(i: NetworkAddressInfo,abi: NetworkSocketAbi)=buildJsonObject {
        put("show",json(i.shownPoints(abi)));put("prec11",json(i.shownPoints(abi,11)+"TAIL".codePoints().toArray()))
        put("flags",JsonArray(i.flags.map {JsonPrimitive(it.name)}));put("family",i.family.value);put("type",i.socketType.value);put("protocol",i.protocol)
        put("address",view(i.address,abi));put("canon",i.canonicalName?.points()?.let(::json) ?: JsonNull)
    }
    private fun failure(e: Throwable)=buildJsonObject {
        when(e) {
            is GhcIOException->{val err=e.error;put("kind","io");put("show",json(err.show().points()));put("type",err.type.shown);put("location",json(err.location.points()));put("description",json(err.description.points()));put("errno",err.errno?.let(::JsonPrimitive) ?: JsonNull);put("filename",err.filename?.points()?.let(::json) ?: JsonNull)}
            is IllegalStateException->{put("kind","fatal");put("show",json(e.message!!.codePoints().toArray()))}
            else->throw e
        }
    }
    private fun observe(v: JsonObject,abi: NetworkSocketAbi): JsonElement=try {
        val memory=NetworkSocketMemory(abi) {GhcForeignCString("UTF-8")}
        when(text(v,"api")) {
            "address"->{val a=address(v.getValue("address"));val size=memory.size(a);Memory(size.toLong()).use {p->memory.poke(p,a);buildJsonObject {put("size",size);put("bytes",bytes(p.getByteArray(0,size)));put("value",view(a,abi));put("peek",view(memory.peek(p),abi))}}}
            "peek"->Memory(256).use {p->p.clear();val raw=points(v.getValue("bytes")).map {it.toByte()}.toByteArray();p.write(0,raw,0,raw.size);view(memory.peek(p),abi)}
            "compare"->{val a=address(v.getValue("left"));val b=address(v.getValue("right"));buildJsonObject {put("eq",a==b);put("order",a.compareTo(b).let {if(it<0) "LT" else if(it>0) "GT" else "EQ"})}}
            "integer-types"->{val n=number(v,"value").toInt();val f=NetworkSocketFamily(n);val t=NetworkSocketType(n);val prec=number(v,"precedence").toInt();buildJsonObject {put("family",f.show(abi,prec)+"TAIL");put("type",t.show(abi,prec)+"TAIL");put("familySupported",f.supported);put("typeSupported",t.supported)}}
            "hints"->{val i=info(v.getValue("hints"));Memory(abi.info("size").toLong()).use {p->p.setMemory(0,p.size(),170.toByte());memory.pokeInfo(p,i);buildJsonObject {put("value",view(i,abi));put("bytes",bytes(p.getByteArray(0,p.size().toInt())))}}}
            "flags"->{val flags=v.getValue("flags").jsonArray.map {NetworkAddressFlag.valueOf(it.jsonPrimitive.content)};buildJsonObject {put("pack",abi.packFlags(flags));put("unpack",JsonArray(abi.unpackFlags(number(v,"mask").toInt()).map {JsonPrimitive(it.name)}))}}
            "errno"->throw GhcIOException(GhcPosixErrno.toError(Chars.fromString(text(v,"location","test-location")),number(v,"errno").toInt(),present(v,"filename")?.let {CharacterString(points(it))}))
            "codec-pairs"->{val codec=GhcForeignCString(text(v,"name"));JsonArray(List(number(v,"count").toInt()) {i->val n=number(v,"start").toInt()+i;json(codec.decode(byteArrayOf((n/256).toByte(),n.toByte())))})}
            "follow"->{
                val allocations=mutableListOf<Memory>()
                fun allocate(n: Long)=Memory(n).also {allocations.add(it);it.clear()}
                try {
                    var next: Pointer?=null
                    for(item in v.getValue("nodes").jsonArray.reversed()) {
                        val n=item.jsonObject;val i=info(n);val sa=allocate(memory.size(i.address).toLong());memory.poke(sa,i.address)
                        val node=allocate(abi.info("size").toLong());memory.pokeInfo(node,i);node.setPointer(abi.info("addr").toLong(),sa);node.setPointer(abi.info("next").toLong(),next)
                        present(n,"canonBytes")?.let {raw->val data=points(raw).map {it.toByte()}.toByteArray();val p=allocate(data.size+1L);if(data.isNotEmpty())p.write(0,data,0,data.size);node.setPointer(abi.info("canon").toLong(),p)}
                        next=node
                    }
                    JsonArray(memory.followAddrInfo(next).map {view(it,abi)})
                } finally {allocations.asReversed().forEach {it.close()}}
            }
            "codec"->{
                val codec=GhcForeignCString(text(v,"name"))
                if(text(v,"mode")=="encode") {
                    val pts=v["points"]?.let(::points) ?: IntArray(number(v,"rangeCount").toInt()) {number(v,"rangeStart").toInt()+it}
                    val output=codec.encode(pts,v["nullTerminate"]?.jsonPrimitive?.boolean ?: true)
                    if(v["hashOutput"]?.jsonPrimitive?.boolean==true) buildJsonObject {put("count",output.size);put("sha256",MessageDigest.getInstance("SHA-256").digest(output).joinToString("") {"%02x".format(it.toInt() and 255)})} else bytes(output)
                } else {
                    val input=points(v.getValue("bytes")).map {it.toByte()}.toByteArray()
                    val data=if(text(v,"mode")=="cstring") input.copyOf(input.indexOf(0).let {if(it<0) input.size else it}) else input
                    json(codec.decode(data))
                }
            }
            "resolve"->{val codec=GhcForeignCString(text(v,"encoding","UTF-8"));val resolver=NetworkNameResolver(abi,NetworkPosixNameFunctions,{codec},{},actual=="macos-64")
                val result=resolver.getAddrInfo(present(v,"hints")?.let(::info),present(v,"host")?.let {CharacterString(points(it))},present(v,"service")?.let {CharacterString(points(it))})
                if(v["void"]?.jsonPrimitive?.boolean==true) JsonPrimitive(true) else JsonArray(result.map {view(it,abi)})}
            else->error(v)
        }
    } catch(e: Throwable) {buildJsonObject {put("error",failure(e))}}
    private fun observations(platform: String): List<DynamicTest> {
        val abi=NetworkSocketAbi.load(platform)
        val stream=checkNotNull(javaClass.getResourceAsStream("/network-address/$platform.jsonl.gz"))
        return GZIPInputStream(stream).bufferedReader().use {reader->reader.readLine();reader.lineSequence().mapIndexedNotNull {i,line->
            val row=Json.parseToJsonElement(line).jsonObject;val request=row.getValue("request").jsonObject
            if(text(request,"api") in listOf("codec","codec-pairs","resolve","errno") && (platform!=actual || Platform.isWindows())) null else DynamicTest.dynamicTest("$platform $i ${text(row,"group")}") {assertEquals(row.getValue("expected"),observe(request,abi))}
        }.toList()}
    }
    @TestFactory fun originalMacOs()=observations("macos-64")
    @TestFactory fun originalLinux()=observations("linux-64")
    @Test
    fun linkedResultIsFreedOnceOnSuccessAndOnDecodeFailure() {
        val abi=NetworkSocketAbi.load(actual);val memory=NetworkSocketMemory(abi) {GhcForeignCString("UTF-8")}
        Memory(abi.info("size").toLong()).use {node->Memory(abi.address("v4Size").toLong()).use {addr->Memory(4).use {canon->
            val hints=NetworkAddressInfo.defaultHints(abi);memory.poke(addr,NetworkSocketAddress.Inet(80u,0x0100007fu));memory.pokeInfo(node,hints)
            node.setPointer(abi.info("addr").toLong(),addr);node.setPointer(abi.info("canon").toLong(),canon);canon.write(0,byteArrayOf(65,-1,66,0),0,4)
            var freed=0
            val functions=object: NetworkNameFunctions {
                override fun getAddrInfo(host: Pointer?,service: Pointer?,hints: Pointer?,result: PointerByReference): Int {result.value=node;return 0}
                override fun freeAddrInfo(pointer: Pointer?) {assertEquals(node,pointer);freed++}
                override fun gaiStrError(code: Int): Pointer=error("not called")
            }
            val resolver=NetworkNameResolver(abi,functions,{GhcForeignCString("UTF-8")},{},Platform.isMac())
            val result=resolver.getAddrInfo(null,null,null);assertEquals(1,freed);assertEquals(Chars.fromString("AB"),result.single().canonicalName)
            val original=java.io.IOException("decode failed")
            val failing=NetworkNameResolver(abi,functions,{throw original},{},Platform.isMac())
            assertSame(original,assertThrows(java.io.IOException::class.java) {failing.getAddrInfo(null,null,null)})
            assertEquals(2,freed)
        }}}
    }
    @Test
    fun initializationAndCStringEffectsPrecedeLookupAndGaiErrorDecodingFollowsIt() {
        val abi=NetworkSocketAbi.load(actual);val events=mutableListOf<String>();val codec=GhcForeignCString("UTF-8")
        Memory(5).use {description->description.write(0,byteArrayOf(66,-1,65,68,0),0,5)
            val functions=object: NetworkNameFunctions {
                override fun getAddrInfo(host: Pointer?,service: Pointer?,hints: Pointer?,result: PointerByReference): Int {events.add("lookup");assertEquals(listOf<Byte>(97,0,98),host!!.getByteArray(0,3).toList());return 9}
                override fun freeAddrInfo(pointer: Pointer?) {error("failed lookup must not free")}
                override fun gaiStrError(code: Int): Pointer {events.add("gai");return description}
            }
            val resolver=NetworkNameResolver(abi,functions,{events.add("encoding");codec},{events.add("init")},Platform.isMac())
            val err=assertThrows(HttpGhcIOException::class.java) {resolver.getAddrInfo(null,CharacterString(intArrayOf(97,0xd800,0,98)),Chars.fromString("80"))}
            assertEquals(listOf("init","encoding","encoding","lookup","gai","encoding"),events)
            assertEquals(Chars.fromString("BAD"),err.error.description);assertEquals(IoErrorType.NoSuchThing,err.error.type)
        }
    }
}
