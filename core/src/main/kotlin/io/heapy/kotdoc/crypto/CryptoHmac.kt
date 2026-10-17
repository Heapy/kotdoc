// Crypto.MAC.HMAC, crypton 2.0.1, BSD-3-Clause.
// Vincent Hanquez and contributors; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

object CryptoHmac {
    /** Raw HashAlgorithm dictionary. Buffers have the source declared allocation sizes. */
    interface HashAlgorithm {
        val blockSize:Int
        val digestSize:Int
        val contextSize:Int
        fun initialize(context:ByteArray)
        fun update(context:ByteArray,bytes:ByteArray)
        fun finalize(context:ByteArray,output:ByteArray)
        // IO () executes its effects but its returned Unit root can remain unforced.
        fun initializeResult(context:ByteArray):Lazy<Unit>{initialize(context);return lazyOf(Unit)}
        fun updateResult(context:ByteArray,bytes:ByteArray):Lazy<Unit>{update(context,bytes);return lazyOf(Unit)}
        fun finalizeResult(context:ByteArray,output:ByteArray):Lazy<Unit>{finalize(context,output);return lazyOf(Unit)}
    }
    interface ByteArrayAccess {
        val size:Int
        fun <A> withBytes(action:(ByteArray)->Lazy<A>):Lazy<A>
        fun copyBytesTo(destination:ByteArray,offset:Int=0):Lazy<Unit> = withBytes{source->source.copyInto(destination,offset,0,size);lazyOf(Unit)}
    }
    fun byteArrayAccess(bytes:ByteArray):ByteArrayAccess=object:ByteArrayAccess{
        override val size get()=bytes.size
        override fun <A> withBytes(action:(ByteArray)->Lazy<A>)=action(bytes)
    }
    class HashContext internal constructor(internal val raw:ByteArray):ByteArrayAccess {
        override val size get()=raw.size
        override fun <A> withBytes(action:(ByteArray)->Lazy<A>)=action(raw)
        fun bytes()=raw.copyOf()
    }
    class Context(val algorithm:HashAlgorithm,val outer:HashContext,val inner:HashContext)
    class Digest(bytes:ByteArray):Comparable<Digest>,ByteArrayAccess {
        private val value=bytes.copyOf()
        override val size get()=value.size
        override fun <A> withBytes(action:(ByteArray)->Lazy<A>)=action(value)
        fun bytes()=value.copyOf()
        override fun equals(other:Any?)=other is Digest && value.contentEquals(other.value)
        override fun hashCode()=value.contentHashCode()
        override fun compareTo(other:Digest):Int{for(i in 0..<minOf(value.size,other.value.size)){val c=(value[i].toInt() and 255).compareTo(other.value[i].toInt() and 255);if(c!=0)return c};return value.size.compareTo(other.value.size)}
        override fun toString()=value.toHexString()
        fun force()=Unit
        internal fun constantEquals(other:Digest):Boolean{
            if(value.size!=other.value.size)return false
            var different=0
            for(i in value.indices)different=different or ((value[i].toInt() xor other.value[i].toInt()) and 255)
            return different==0
        }
    }
    class Mac(private val digest:Digest):ByteArrayAccess {
        override val size get()=digest.size
        override fun <A> withBytes(action:(ByteArray)->Lazy<A>)=digest.withBytes(action)
        constructor(bytes:ByteArray):this(Digest(bytes))
        fun hmacGetDigest()=digest
        fun bytes()=digest.bytes()
        override fun equals(other:Any?)=other is Mac && digest.constantEquals(other.digest)
        override fun hashCode()=digest.hashCode()
        // Host-only opaque display: original HMAC has no Show instance.
        override fun toString()="<HMAC>"
    }
    fun digestFromByteString(algorithm:HashAlgorithm,bytes:ByteArray):Digest?=if(bytes.size==algorithm.digestSize)Digest(bytes)else null
    fun readsDigest(algorithm:HashAlgorithm,input:String):Pair<Digest,String>?{
        val size=algorithm.digestSize
        if(size<0||size>input.length/2)return null
        val out=ByteArray(size)
        fun digit(c:Char)=when(c){in '0'..'9'->c-'0';in 'a'..'f'->c-'a'+10;in 'A'..'F'->c-'A'+10;else->-1}
        for(i in out.indices){val a=digit(input[2*i]);val b=digit(input[2*i+1]);if(a<0||b<0)return null;out[i]=(a*16+b).toByte()}
        return Digest(out) to input.substring(size*2)
    }
    private fun <A> memo(block:()->A):Lazy<A> = object:Lazy<A> {
        private var result:Result<A>?=null
        override val value:A get()=synchronized(this){val r=result?:try{Result.success(block())}catch(t:Throwable){Result.failure<A>(t)}.also{result=it};r.getOrThrow()}
        override fun isInitialized()=result!=null
    }
    private fun hashInit(a:HashAlgorithm)=HashContext(ByteArray(a.contextSize).also{a.initializeResult(it)})
    private fun prefix(bytes:ByteArray,length:Int)=if(bytes.size==length)bytes else bytes.copyOfRange(0,length)
    private fun hashUpdatesAccess(a:HashAlgorithm,initial:Lazy<HashContext>,chunks:Iterable<ByteArrayAccess>):HashContext {
        val iterator=chunks.iterator();var first:ByteArrayAccess?=null
        while(iterator.hasNext()){val b=iterator.next();if(b.size!=0){first=b;break}}
        val context=initial.value
        if(first==null)return context
        val copy=context.raw.copyOf()
        fun process(b:ByteArrayAccess)=b.withBytes{a.updateResult(copy,prefix(it,b.size))}
        process(first)
        while(iterator.hasNext()){val b=iterator.next();if(b.size!=0)process(b)}
        return HashContext(copy)
    }
    private fun hashUpdates(a:HashAlgorithm,initial:Lazy<HashContext>,chunks:Iterable<ByteArray>)=hashUpdatesAccess(a,initial,chunks.asSequence().map(::byteArrayAccess).asIterable())
    private fun mutableFinalize(a:HashAlgorithm,raw:ByteArray):ByteArray=ByteArray(a.digestSize).also{a.finalizeResult(raw,it)}
    private fun hashFinalize(a:HashAlgorithm,c:HashContext)=mutableFinalize(a,c.raw.copyOf())
    fun context(algorithm:HashAlgorithm,outer:Lazy<HashContext>,inner:Lazy<HashContext>):Context {
        val o=outer.value;val i=inner.value;return Context(algorithm,o,i)
    }
    fun hashContext(bytes:ByteArray)=HashContext(bytes.copyOf())
    fun initialize(algorithm:HashAlgorithm,secret:ByteArray)=initialize(algorithm,byteArrayAccess(secret))
    fun initialize(algorithm:HashAlgorithm,secret:ByteArrayAccess):Context {
        val keyLength=secret.size
        val blockSize=algorithm.blockSize
        val withKey:((ByteArray)->Lazy<Pair<ByteArray,ByteArray>>)->Lazy<Pair<ByteArray,ByteArray>> = when {
            keyLength==blockSize->{action->secret.withBytes(action)}
            keyLength<blockSize->{
                val key=ByteArray(blockSize)
                secret.withBytes{it.copyInto(key,0,0,secret.size);lazyOf(Unit)};
                {action->action(key)}
            }
            else->{
                val mutable=ByteArray(algorithm.contextSize).also{algorithm.initializeResult(it)}
                secret.withBytes{algorithm.updateResult(mutable,prefix(it,secret.size))}
                val digest=mutableFinalize(algorithm,mutable)
                algorithm.initializeResult(mutable)
                val key=if(algorithm.digestSize<blockSize)ByteArray(blockSize).also{digest.copyInto(it)}else digest
                {action->action(key)}
            }
        }
        val (inner,outer)=withKey{key->
            val inner=ByteArray(blockSize){(key[it].toInt() xor 0x36).toByte()}
            val outer=ByteArray(blockSize){(key[it].toInt() xor 0x5c).toByte()}
            lazyOf(inner to outer)
        }.value
        val initial=memo{hashInit(algorithm)}
        val o=hashUpdates(algorithm,initial,listOf(outer))
        val i=hashUpdates(algorithm,initial,listOf(inner))
        return Context(algorithm,o,i)
    }
    fun update(context:Context,message:ByteArray)=update(context,byteArrayAccess(message))
    fun update(context:Context,message:ByteArrayAccess)=updatesAccess(context,listOf(message))
    fun updates(context:Context,messages:Iterable<ByteArray>)=updatesAccess(context,messages.asSequence().map(::byteArrayAccess).asIterable())
    fun updatesAccess(context:Context,messages:Iterable<ByteArrayAccess>)=Context(context.algorithm,context.outer,hashUpdatesAccess(context.algorithm,lazyOf(context.inner),messages))
    fun finalize(context:Context):Mac {
        val a=context.algorithm
        val digest=hashFinalize(a,context.inner)
        val outer=hashUpdates(a,lazyOf(context.outer),listOf(digest))
        return Mac(hashFinalize(a,outer))
    }
    fun hmac(algorithm:HashAlgorithm,key:ByteArray,message:ByteArray)=hmac(algorithm,byteArrayAccess(key),byteArrayAccess(message))
    fun hmac(algorithm:HashAlgorithm,key:ByteArrayAccess,message:ByteArrayAccess)=finalize(update(initialize(algorithm,key),message))
    fun hmacLazy(algorithm:HashAlgorithm,key:ByteArray,chunks:Iterable<ByteArray>)=hmacLazy(algorithm,byteArrayAccess(key),chunks)
    fun hmacLazy(algorithm:HashAlgorithm,key:ByteArrayAccess,chunks:Iterable<ByteArray>)=finalize(updates(initialize(algorithm,key),chunks))
    fun deferInitialize(algorithm:HashAlgorithm,key:Lazy<ByteArray>)=memo{initialize(algorithm,key.value)}
    fun deferUpdate(context:Lazy<Context>,message:Lazy<ByteArray>)=memo{val c=context.value;update(c,message.value)}
    fun deferUpdates(context:Lazy<Context>,messages:Lazy<Iterable<ByteArray>>)=memo{val c=context.value;updates(c,messages.value)}
    fun deferFinalize(context:Lazy<Context>)=memo{finalize(context.value)}
    fun deferHmac(algorithm:HashAlgorithm,key:Lazy<ByteArray>,message:Lazy<ByteArray>)=deferFinalize(deferUpdate(deferInitialize(algorithm,key),message))
    fun deferHmacLazy(algorithm:HashAlgorithm,key:Lazy<ByteArray>,chunks:Lazy<Iterable<ByteArray>>)=deferFinalize(deferUpdates(deferInitialize(algorithm,key),chunks))
    // Selected GHC9.12.2-O2 constEq strict length bindings demand the right operand first.
    fun deferEquals(left:Lazy<Mac>,right:Lazy<Mac>)=memo{val r=right.value;left.value==r}
    fun deferContext(algorithm:HashAlgorithm,outer:Lazy<HashContext>,inner:Lazy<HashContext>)=memo{context(algorithm,outer,inner)}
    fun deferMac(digest:Lazy<ByteArray>)=memo{Mac(digest.value)}
    fun deferGetDigest(mac:Lazy<Mac>)=memo{mac.value.hmacGetDigest()}
    fun deferInitializeAccess(algorithm:HashAlgorithm,key:Lazy<ByteArrayAccess>)=memo{initialize(algorithm,key.value)}
    fun deferUpdateAccess(context:Lazy<Context>,message:Lazy<ByteArrayAccess>)=memo{val c=context.value;update(c,message.value)}
    fun deferUpdatesAccess(context:Lazy<Context>,messages:Lazy<Iterable<ByteArrayAccess>>)=memo{val c=context.value;updatesAccess(c,messages.value)}
    fun deferHmacAccess(algorithm:HashAlgorithm,key:Lazy<ByteArrayAccess>,message:Lazy<ByteArrayAccess>)=deferFinalize(deferUpdateAccess(deferInitializeAccess(algorithm,key),message))
    fun deferHmacLazyAccess(algorithm:HashAlgorithm,key:Lazy<ByteArrayAccess>,chunks:Lazy<Iterable<ByteArray>>)=deferFinalize(deferUpdates(deferInitializeAccess(algorithm,key),chunks))
}
