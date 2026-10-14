// Text.Blaze/Internal 0.8.3.0; Copyright (c) 2010 Jasper Van der Jeugt.
// BSD-3-Clause; licenses/Blaze-BSD-3-Clause.txt.
package io.heapy.kotdoc.markup

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars

/** Static backends remain independent, including the String suffix function. */
class StaticString(
    val appendString: (Lazy<CharacterString>) -> CharacterString,
    utf8: ByteArray,
    text: String,
    internal val prefix: CharacterString? = null,
) {
    private val bytes = utf8.copyOf()
    val utf8: ByteArray get() = bytes.copyOf()
    internal val rawUtf8: ByteArray get() = bytes
    val text = TextChars.fromString(text)
    companion object {
        fun fromString(value: CharacterString): StaticString {
            val text = TextChars.fromPoints(value.points())
            return StaticString({ CharacterStringChars.append(value,it.value) },text.toByteArray(Charsets.UTF_8),text,value)
        }
        fun fromString(value: String) = fromString(CharacterStringChars.fromString(value))
        fun fromText(value: String) = fromString(CharacterStringChars.fromString(TextChars.fromString(value)))
    }
}
sealed interface ChoiceString {
    class Static(val value: StaticString) : ChoiceString
    class String(val value: CharacterString) : ChoiceString
    class Text(value: kotlin.String) : ChoiceString { val value = TextChars.fromString(value) }
    class Bytes(bytes: ByteArray) : ChoiceString {
        private val data=bytes.copyOf()
        val bytes: ByteArray get()=data.copyOf()
        internal val rawBytes: ByteArray get()=data
    }
    class PreEscaped(val value: ChoiceString) : ChoiceString
    class External(val value: ChoiceString) : ChoiceString
    class Append(val left: ChoiceString,val right: ChoiceString) : ChoiceString
    /** A memoized source thunk producing a ChoiceString constructor. */
    class Deferred(val value: Lazy<ChoiceString>) : ChoiceString
    data object Empty : ChoiceString
}
operator fun ChoiceString.plus(other: ChoiceString): ChoiceString = ChoiceString.Append(this,other)
class Tag(val value: StaticString)
class AttributeValue(val value: ChoiceString) {
    operator fun plus(other: AttributeValue) = AttributeValue(value + other.value)
    companion object { val Empty = AttributeValue(ChoiceString.Empty) }
}

typealias Markup = MarkupM<Unit>
typealias Html = Markup
/** Each return value is lazy; renderers never force leaf values. */
sealed interface MarkupM<out A> {
    class Parent<A>(val tag: StaticString,val open: StaticString,val close: StaticString,val content: MarkupM<A>) : MarkupM<A>
    class CustomParent<A>(val tag: ChoiceString,val content: MarkupM<A>) : MarkupM<A>
    class Leaf<A>(val tag: StaticString,val open: StaticString,val close: StaticString,val result: Lazy<A>) : MarkupM<A>
    class CustomLeaf<A>(val tag: ChoiceString,val close: Boolean,val result: Lazy<A>) : MarkupM<A>
    class Content<A>(val content: ChoiceString,val result: Lazy<A>) : MarkupM<A>
    class Comment<A>(val comment: ChoiceString,val result: Lazy<A>) : MarkupM<A>
    class Append<A>(val left: MarkupM<*>,val right: MarkupM<A>) : MarkupM<A>
    class AddAttribute<A>(val rawKey: StaticString,val key: StaticString,val value: ChoiceString,val content: MarkupM<A>) : MarkupM<A>
    class AddCustomAttribute<A>(val key: ChoiceString,val value: ChoiceString,val content: MarkupM<A>) : MarkupM<A>
    class Empty<A>(val result: Lazy<A>) : MarkupM<A>
    /** Transparent call-by-need boundary for bind/function application. */
    class Deferred<A>(val node: Lazy<MarkupM<A>>) : MarkupM<A>
}
fun <A> MarkupM<A>.markupValue(): A {
    var node: MarkupM<A> = this
    while (true) node = when (val n=node) {
        is MarkupM.Parent -> n.content
        is MarkupM.CustomParent -> n.content
        is MarkupM.AddAttribute -> n.content
        is MarkupM.AddCustomAttribute -> n.content
        is MarkupM.Append -> n.right
        is MarkupM.Deferred -> n.node.value
        is MarkupM.Leaf -> return n.result.value
        is MarkupM.CustomLeaf -> return n.result.value
        is MarkupM.Content -> return n.result.value
        is MarkupM.Comment -> return n.result.value
        is MarkupM.Empty -> return n.result.value
    }
}
fun <A,B> MarkupM<A>.mapLazy(f: (Lazy<A>)->B): MarkupM<B> = MarkupM.Append(this,MarkupM.Empty(lazy { f(lazy { markupValue() }) }))
fun <A,B> MarkupM<A>.map(f: (A)->B): MarkupM<B> = mapLazy { f(it.value) }
fun <A,B> MarkupM<A>.flatMapLazy(f: (Lazy<A>)->MarkupM<B>): MarkupM<B> = MarkupM.Append(this,MarkupM.Deferred(lazy { f(lazy { markupValue() }) }))
fun <A,B> MarkupM<A>.flatMap(f: (A)->MarkupM<B>): MarkupM<B> = flatMapLazy { f(it.value) }
fun <A,B> MarkupM<A>.then(other: MarkupM<B>): MarkupM<B> = MarkupM.Append(this,other)
fun <A,B> MarkupM<(Lazy<A>)->B>.applyLazy(other: MarkupM<A>): MarkupM<B> = MarkupM.Append(MarkupM.Append(this,other),MarkupM.Empty(lazy { markupValue()(lazy { other.markupValue() }) }))
fun <A,B> MarkupM<(A)->B>.apply(other: MarkupM<A>): MarkupM<B> = map { f -> { x: Lazy<A> -> f(x.value) } }.applyLazy(other)
fun <A,B> MarkupM<A>.before(other: MarkupM<B>): MarkupM<A> = mapLazy { a -> { _: Lazy<B> -> a.value } }.applyLazy(other)
operator fun <A> MarkupM<A>.plus(other: MarkupM<A>): MarkupM<A> = MarkupM.Append(this,other)
fun <A> pureMarkup(value: A): MarkupM<A> = MarkupM.Empty(lazyOf(value))
fun <A> pureMarkupLazy(value: ()->A): MarkupM<A> = MarkupM.Empty(lazy(value))
fun <A> concatMarkup(values: List<MarkupM<A>>,empty: A): MarkupM<A> = values.asReversed().fold(pureMarkup(empty)) { tail,value -> MarkupM.Append(value,tail) }

/** Attribute composition preserves application order and the original tree. */
class Attribute private constructor(private val operation: Operation?) {
    private sealed interface Operation {
        class Builtin(val raw: StaticString,val key: StaticString,val value: ChoiceString) : Operation
        class Custom(val key: ChoiceString,val value: ChoiceString) : Operation
        class Compose(val first: Operation,val second: Operation) : Operation
    }
    fun <A> apply(markup: MarkupM<A>): MarkupM<A> {
        var result=markup
        val todo=ArrayDeque<Operation>()
        operation?.let(todo::addLast)
        while(todo.isNotEmpty()) when(val op=todo.removeLast()) {
            is Operation.Builtin -> result=MarkupM.AddAttribute(op.raw,op.key,op.value,result)
            is Operation.Custom -> result=MarkupM.AddCustomAttribute(op.key,op.value,result)
            is Operation.Compose -> { todo.addLast(op.second);todo.addLast(op.first) }
        }
        return result
    }
    operator fun plus(other: Attribute): Attribute = when {
        operation == null -> other
        other.operation == null -> this
        else -> Attribute(Operation.Compose(operation,other.operation))
    }
    companion object {
        val Empty = Attribute(null)
        fun builtin(raw: Tag,key: Tag,value: AttributeValue) = Attribute(Operation.Builtin(raw.value,key.value,value.value))
        fun custom(key: ChoiceString,value: AttributeValue) = Attribute(Operation.Custom(key,value.value))
    }
}
fun <A> MarkupM<A>.with(attribute: Attribute): MarkupM<A> = attribute.apply(this)
fun <A> MarkupM<A>.withIf(condition: Boolean,attribute: ()->Attribute): MarkupM<A> = if(condition) with(attribute()) else this
fun <A,B> ((MarkupM<A>)->MarkupM<B>).with(attribute: Attribute): (MarkupM<A>)->MarkupM<B> = { attribute.apply(this(it)) }
fun <A,B> ((MarkupM<A>)->MarkupM<B>).withIf(condition: Boolean,attribute: ()->Attribute): (MarkupM<A>)->MarkupM<B> = if(condition) with(attribute()) else this

object Blaze {
    val empty: Markup get()=pureMarkup(Unit)
    fun customParent(tag: Tag,content: Markup): Markup = MarkupM.CustomParent(ChoiceString.Static(tag.value),content)
    fun customLeaf(tag: Tag,close: Boolean): Markup = MarkupM.CustomLeaf(ChoiceString.Static(tag.value),close,lazyOf(Unit))
    fun attribute(rawKey: Tag,key: Tag,value: AttributeValue)=Attribute.builtin(rawKey,key,value)
    fun customAttribute(tag: Tag,value: AttributeValue)=Attribute.custom(ChoiceString.Static(tag.value),value)
    fun dataAttribute(tag: Tag,value: AttributeValue)=Attribute.custom(ChoiceString.Static(StaticString.fromString("data-")) + ChoiceString.Static(tag.value),value)
    fun textTag(value: String)=Tag(StaticString.fromText(value))
    fun stringTag(value: CharacterString)=Tag(StaticString.fromString(value))
    fun stringTag(value: String)=stringTag(CharacterStringChars.fromString(value))
    fun content(value: ChoiceString): Markup=MarkupM.Content(value,lazyOf(Unit))
    fun comment(value: ChoiceString): Markup=MarkupM.Comment(value,lazyOf(Unit))
    fun text(value: String)=content(ChoiceString.Text(value))
    fun preEscapedText(value: String)=content(ChoiceString.PreEscaped(ChoiceString.Text(value)))
    fun string(value: CharacterString)=content(ChoiceString.String(value))
    fun string(value: String)=string(CharacterStringChars.fromString(value))
    fun preEscapedString(value: CharacterString)=content(ChoiceString.PreEscaped(ChoiceString.String(value)))
    fun preEscapedString(value: String)=preEscapedString(CharacterStringChars.fromString(value))
    fun unsafeByteString(value: ByteArray)=content(ChoiceString.Bytes(value))
    fun lazyText(chunks: Sequence<String>)=lazyMarkupChunks(chunks.filter { it.isNotEmpty() },::text)
    fun lazyText(chunks: List<String>)=lazyText(chunks.asSequence())
    fun preEscapedLazyText(chunks: Sequence<String>)=lazyMarkupChunks(chunks.filter { it.isNotEmpty() },::preEscapedText)
    fun preEscapedLazyText(chunks: List<String>)=preEscapedLazyText(chunks.asSequence())
    fun unsafeLazyByteString(chunks: Sequence<ByteArray>)=lazyMarkupChunks(chunks.filter { it.isNotEmpty() },::unsafeByteString)
    fun unsafeLazyByteString(chunks: List<ByteArray>)=unsafeLazyByteString(chunks.asSequence())
    fun textComment(value: String)=comment(ChoiceString.PreEscaped(ChoiceString.Text(value)))
    fun stringComment(value: CharacterString)=comment(ChoiceString.PreEscaped(ChoiceString.String(value)))
    fun stringComment(value: String)=stringComment(CharacterStringChars.fromString(value))
    fun lazyTextComment(chunks: Sequence<String>)=comment(lazyChoiceChunks(chunks.filter { it.isNotEmpty() }) { ChoiceString.PreEscaped(ChoiceString.Text(it)) })
    fun lazyTextComment(chunks: List<String>)=lazyTextComment(chunks.asSequence())
    fun unsafeByteStringComment(value: ByteArray)=comment(ChoiceString.PreEscaped(ChoiceString.Bytes(value)))
    fun unsafeLazyByteStringComment(chunks: Sequence<ByteArray>)=comment(lazyChoiceChunks(chunks.filter { it.isNotEmpty() }) { ChoiceString.PreEscaped(ChoiceString.Bytes(it)) })
    fun unsafeLazyByteStringComment(chunks: List<ByteArray>)=unsafeLazyByteStringComment(chunks.asSequence())
    fun textValue(value: String)=AttributeValue(ChoiceString.Text(value))
    fun preEscapedTextValue(value: String)=AttributeValue(ChoiceString.PreEscaped(ChoiceString.Text(value)))
    fun stringValue(value: CharacterString)=AttributeValue(ChoiceString.String(value))
    fun stringValue(value: String)=stringValue(CharacterStringChars.fromString(value))
    fun preEscapedStringValue(value: CharacterString)=AttributeValue(ChoiceString.PreEscaped(ChoiceString.String(value)))
    fun preEscapedStringValue(value: String)=preEscapedStringValue(CharacterStringChars.fromString(value))
    fun unsafeByteStringValue(value: ByteArray)=AttributeValue(ChoiceString.Bytes(value))
    fun lazyTextValue(chunks: Sequence<String>)=AttributeValue(lazyChoiceChunks(chunks.filter { it.isNotEmpty() },ChoiceString::Text))
    fun lazyTextValue(chunks: List<String>)=lazyTextValue(chunks.asSequence())
    fun preEscapedLazyTextValue(chunks: Sequence<String>)=AttributeValue(lazyChoiceChunks(chunks.filter { it.isNotEmpty() }) { ChoiceString.PreEscaped(ChoiceString.Text(it)) })
    fun preEscapedLazyTextValue(chunks: List<String>)=preEscapedLazyTextValue(chunks.asSequence())
    fun unsafeLazyByteStringValue(chunks: Sequence<ByteArray>)=AttributeValue(lazyChoiceChunks(chunks.filter { it.isNotEmpty() },ChoiceString::Bytes))
    fun unsafeLazyByteStringValue(chunks: List<ByteArray>)=unsafeLazyByteStringValue(chunks.asSequence())
    fun concatChoice(values: List<ChoiceString>): ChoiceString=values.asReversed().fold(ChoiceString.Empty as ChoiceString) { tail,value -> value + tail }
}

/** Source external/contents transformations retain shape and do not force values. */
fun <A> MarkupM<A>.external(): MarkupM<A> = transformMarkup(this,false)
fun <A> MarkupM<A>.contents(): MarkupM<A> = transformMarkup(this,true)
private fun <A> transformMarkup(root: MarkupM<A>,contents: Boolean): MarkupM<A> {
    class Work(val node: MarkupM<*>,val finish: Boolean=false)
    val todo=ArrayDeque<Work>();val done=ArrayDeque<MarkupM<*>>();todo.addLast(Work(root))
    while(todo.isNotEmpty()) {
        val (n,finish)=todo.removeLast().let { it.node to it.finish }
        if (!finish) when(n) {
            is MarkupM.Deferred -> { done.addLast(MarkupM.Deferred(lazy { transformMarkup(n.node.value,contents) }));continue }
            is MarkupM.Append -> { todo.addLast(Work(n,true));todo.addLast(Work(n.right));todo.addLast(Work(n.left));continue }
            is MarkupM.Parent -> { todo.addLast(Work(n,true));todo.addLast(Work(n.content));continue }
            is MarkupM.CustomParent -> { todo.addLast(Work(n,true));todo.addLast(Work(n.content));continue }
            is MarkupM.AddAttribute -> { todo.addLast(Work(n,true));todo.addLast(Work(n.content));continue }
            is MarkupM.AddCustomAttribute -> { todo.addLast(Work(n,true));todo.addLast(Work(n.content));continue }
            else -> Unit
        }
        done.addLast(when(n) {
            is MarkupM.Append -> { val right=done.removeLast();MarkupM.Append(done.removeLast(),right) }
            is MarkupM.Parent -> done.removeLast().let { if(contents) it else MarkupM.Parent(n.tag,n.open,n.close,it) }
            is MarkupM.CustomParent -> done.removeLast().let { if(contents) it else MarkupM.CustomParent(n.tag,it) }
            is MarkupM.AddAttribute -> done.removeLast().let { if(contents) it else MarkupM.AddAttribute(n.rawKey,n.key,n.value,it) }
            is MarkupM.AddCustomAttribute -> done.removeLast().let { if(contents) it else MarkupM.AddCustomAttribute(n.key,n.value,it) }
            is MarkupM.Content -> if(contents) n else MarkupM.Content(ChoiceString.External(n.content),n.result)
            else -> if(contents) MarkupM.Empty(lazy { n.markupValue() }) else n
        })
    }
    @Suppress("UNCHECKED_CAST") return done.removeLast() as MarkupM<A>
}
fun ChoiceString.isEmpty(): Boolean {
    val todo=ArrayDeque<ChoiceString>();todo.addLast(this)
    while(todo.isNotEmpty()) when(val s=todo.removeLast()) {
        is ChoiceString.Deferred -> todo.addLast(s.value.value)
        is ChoiceString.Static -> if(s.value.rawUtf8.isNotEmpty()) return false
        is ChoiceString.String -> if(s.value.points().isNotEmpty()) return false
        is ChoiceString.Text -> if(s.value.isNotEmpty()) return false
        is ChoiceString.Bytes -> if(s.rawBytes.isNotEmpty()) return false
        is ChoiceString.PreEscaped -> todo.addLast(s.value)
        is ChoiceString.External -> todo.addLast(s.value)
        is ChoiceString.Append -> { todo.addLast(s.right);todo.addLast(s.left) }
        ChoiceString.Empty -> Unit
    }
    return true
}
fun MarkupM<*>.isEmpty(): Boolean {
    val todo=ArrayDeque<MarkupM<*>>();todo.addLast(this)
    while(todo.isNotEmpty()) when(val m=todo.removeLast()) {
        is MarkupM.Append -> { todo.addLast(m.right);todo.addLast(m.left) }
        is MarkupM.Deferred -> todo.addLast(m.node.value)
        is MarkupM.AddAttribute -> todo.addLast(m.content)
        is MarkupM.AddCustomAttribute -> todo.addLast(m.content)
        is MarkupM.Content -> if(!m.content.isEmpty()) return false
        is MarkupM.Comment -> if(!m.comment.isEmpty()) return false
        is MarkupM.Empty -> Unit
        else -> return false
    }
    return true
}

internal fun ChoiceString.force(): ChoiceString {
    var result=this
    while(result is ChoiceString.Deferred) result=result.value.value
    return result
}
private fun <T> lazyMarkupChunks(chunks: Sequence<T>,convert: (T)->Markup): Markup {
    val input=sourceThunk { chunks.iterator() }
    fun next(): Markup=MarkupM.Deferred(sourceThunk {
        val iterator=input.value
        if(iterator.hasNext()) MarkupM.Append(convert(iterator.next()),next()) else Blaze.empty
    })
    return next()
}
private fun <T> lazyChoiceChunks(chunks: Sequence<T>,convert: (T)->ChoiceString): ChoiceString {
    val input=sourceThunk { chunks.iterator() }
    fun next(): ChoiceString=ChoiceString.Deferred(sourceThunk {
        val iterator=input.value
        if(iterator.hasNext()) ChoiceString.Append(convert(iterator.next()),next()) else ChoiceString.Empty
    })
    return next()
}

/** Haskell thunks retain failures too; Kotlin lazy retries a failed initializer.
 * Retaining the failure is essential when a thunk advances a chunk iterator. */
private fun <T> sourceThunk(initialize: ()->T): Lazy<T> = object : Lazy<T> {
    private var initializer: (() -> T)?=initialize
    private var result: T?=null
    private var failure: Exception?=null
    override val value: T
        @Synchronized get() {
            val f=initializer
            if(f != null) {
                try { result=f() } catch(e: Exception) { failure=e }
                initializer=null
            }
            failure?.let { throw it }
            @Suppress("UNCHECKED_CAST") return result as T
        }
    @Synchronized override fun isInitialized()=initializer == null
}
