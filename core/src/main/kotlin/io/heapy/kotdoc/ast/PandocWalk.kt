// Port of Text.Pandoc.Walk, Copyright (c) 2013–2023 John MacFarlane.
// BSD-3-Clause; see licenses/pandoc-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.ast

/** Higher-kinded carrier for a caller-supplied traversal effect. */
interface WalkEffect<F, out A>
sealed interface WalkLoop<out S, out A> {
    data class Continue<S>(val state: S) : WalkLoop<S, Nothing>
    data class Done<A>(val value: A) : WalkLoop<Nothing, A>
}
interface WalkMonad<F> {
    fun <A> pure(value: A): WalkEffect<F, A>
    fun <A, B> flatMap(value: WalkEffect<F, A>, next: (A) -> WalkEffect<F, B>): WalkEffect<F, B>
    /** Ordinary Monad bind recursion by default. Override for stack-safe deep
     * effects; branching implementations must retain independent states. */
    fun <S, A> tailRecM(initial: S, step: (S) -> WalkEffect<F, WalkLoop<S, A>>): WalkEffect<F, A> =
        flatMap(step(initial)) { when (it) {
            is WalkLoop.Continue -> tailRecM(it.state, step)
            is WalkLoop.Done -> pure(it.value)
        } }
}

/** A caller-defined or built-in source Walkable dictionary. */
interface Walkable<A, B> {
    fun walk(transform: (A) -> A, value: B): B =
        (walkM(WalkIdentity, { WalkIdentity.Value(transform(it)) }, value) as WalkIdentity.Value<B>).value
    /** Thunk adapter for source callbacks that may ignore their argument.
     * Custom dictionaries override this alongside walkM when their own pure
     * implementation can avoid demanding the input. The default preserves a
     * custom strict walk override. */
    fun walkLazy(transform: (() -> A) -> A, value: () -> B): B = walk({ transform { it } }, value())
    fun <F> walkM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: B): WalkEffect<F, B>
    fun <C> query(transform: (A) -> C, value: B, monoid: WalkMonoid<C>): C
    fun <C> queryLazy(transform: (() -> A) -> C, value: () -> B, monoid: WalkMonoid<C>): C = query({ transform { it } }, value(), monoid)
    fun queryValues(value: B): Sequence<A> = query({ listOf(it) }, value, WalkMonoids.list()).asSequence()
    fun queryValuesReversed(value: B): Sequence<A> = queryValues(value).toList().asReversed().asSequence()
}

internal class AstWalkable<A, B>(
    internal val plan: (B) -> WalkNode,
    internal val target: WalkKind,
    internal val lazyPlan: ((() -> B) -> WalkNode) = { plan(it()) },
    private val pureWalk: (((A) -> A, B) -> B)? = null,
    private val lazyPureWalk: (((() -> A) -> A, () -> B) -> B)? = null,
) : Walkable<A, B> {
    @Suppress("UNCHECKED_CAST")
    override fun walk(transform: (A) -> A, value: B): B =
        if (pureWalk != null) pureWalk.invoke(transform, value) else walkLazy({ transform(it()) }, { value })

    /** Pure Identity bind is lazy in its reconstructed argument. */
    @Suppress("UNCHECKED_CAST")
    override fun walkLazy(transform: (() -> A) -> A, value: () -> B): B {
        if (lazyPureWalk != null) return lazyPureWalk.invoke(transform, value)
        fun descend(node: WalkNode): Any {
            val rebuilt = lazy {
                val external = node.external
                if (external != null) external.walkLazy { transform { it() as A } } ?: WalkNull
                else node.rebuild(WalkDeferredList({ node.size }) { descend(node.child(it)) })
            }
            return if (node.kind == target && node.visit) transform { rebuilt.value as A } as Any? ?: WalkNull
            else rebuilt.value
        }
        val result = descend(lazyPlan(value))
        return (if (result === WalkNull) null else result) as B
    }

    @Suppress("UNCHECKED_CAST")
    override fun <F> walkM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: B): WalkEffect<F, B> {
        val initial = WalkMachine(WalkFrame(lazyPlan { value }), null)
        return monad.tailRecM(initial) { machine ->
            val frame = machine.frame
            val external=frame.node.external
            if (external != null) {
                monad.flatMap(external.walkM(monad) { transform(it as A) as WalkEffect<F, Any?> }) { result ->
                    val parent = machine.parents
                    val next: WalkLoop<WalkMachine, B> = if (parent == null) WalkLoop.Done(result as B)
                    else WalkLoop.Continue(WalkMachine(parent.frame.copy(results = WalkResults(result ?: WalkNull, parent.frame.results)), parent.rest))
                    monad.pure(next)
                }
            } else if (frame.next < frame.node.size) {
                val child = frame.node.child(frame.next)
                monad.pure(WalkLoop.Continue(WalkMachine(WalkFrame(child), WalkStack(frame.copy(next = frame.next + 1), machine.parents))))
            } else {
                val rebuilt = frame.node.rebuild(frame.results?.forward() ?: emptyList())
                fun finish(result: Any): WalkLoop<WalkMachine, B> {
                    val parent = machine.parents
                    return if (parent == null) WalkLoop.Done((if (result === WalkNull) null else result) as B)
                    else WalkLoop.Continue(WalkMachine(parent.frame.copy(results = WalkResults(result, parent.frame.results)), parent.rest))
                }
                if (frame.node.kind == target && frame.node.visit) {
                    monad.flatMap(transform(rebuilt as A)) { monad.pure(finish(it as Any)) }
                } else monad.pure(finish(rebuilt))
            }
        }
    }

    /** Lazy preorder fold inputs. Taking a prefix does not inspect later descendants. */
    override fun queryValues(value: B): Sequence<A> = queryThunks({ value }, false).map { it() }
    override fun queryValuesReversed(value: B): Sequence<A> = queryThunks({ value }, true).map { it() }

    private fun queryThunks(value: () -> B, reverse: Boolean): Sequence<() -> A> {
        val monoid = WalkMonoid<Sequence<() -> A>> { values -> (if (reverse) values.reversed else values).flatMap { it } }
        return queryLazy({ sequenceOf(it) }, value, monoid)
    }

    override fun <C> query(transform: (A) -> C, value: B, monoid: WalkMonoid<C>): C = queryLazy({ transform(it()) }, { value }, monoid)
    /** Explicit thunks represent source query callbacks which do not demand their argument. */
    override fun <C> queryLazy(transform: (() -> A) -> C, value: () -> B, monoid: WalkMonoid<C>): C =
        monoid.fold(WalkQuery(queryResults(transform, value, monoid, false), queryResults(transform, value, monoid, true)))

    @Suppress("UNCHECKED_CAST")
    private fun <C> queryResults(transform: (() -> A) -> C, value: () -> B, monoid: WalkMonoid<C>, reverse: Boolean): Sequence<C> = sequence {
        data class Work(val node: WalkNode, val index: Int? = null, val selfOnly: Boolean = false)
        val stack = ArrayDeque<Work>(); stack.addLast(Work(lazyPlan(value)))
        while (stack.isNotEmpty()) {
            val work=stack.removeLast(); val node=work.node
            if (work.selfOnly) { yield(transform { node.original as A }); continue }
            val index=work.index
            if(index!=null) {
                val next=index+if(reverse)-1 else 1
                if(next in 0 until node.size)stack.addLast(Work(node,next))
                stack.addLast(Work(node.child(index)));continue
            }
            if(!reverse && node.kind==target && node.visit)yield(transform { node.original as A })
            val external=node.external
            if(external!=null) { yield(external.query({ thunk -> transform { thunk() as A } }, monoid));continue }
            if(reverse) {
                if(node.kind==target && node.visit)stack.addLast(Work(node,selfOnly=true))
                if(node.size>0)stack.addLast(Work(node,node.size-1))
            } else {
                if(node.size>0)stack.addLast(Work(node,0))
            }
        }
    }

    fun <C> query(transform: (A) -> C, value: B, empty: C, append: (C, C) -> C): C =
        queryValues(value).fold(empty) { result, item -> append(result, transform(item)) }
}

object WalkIdentity : WalkMonad<WalkIdentity.Tag> {
    class Tag private constructor()
    data class Value<A>(val value: A) : WalkEffect<Tag, A>
    override fun <A> pure(value: A): WalkEffect<Tag, A> = Value(value)
    override fun <A, B> flatMap(value: WalkEffect<Tag, A>, next: (A) -> WalkEffect<Tag, B>): WalkEffect<Tag, B> = next((value as Value<A>).value)
    override fun <S, A> tailRecM(initial: S, step: (S) -> WalkEffect<Tag, WalkLoop<S, A>>): WalkEffect<Tag, A> {
        var state = initial
        while (true) when (val result = (step(state) as Value<WalkLoop<S, A>>).value) {
            is WalkLoop.Continue -> state = result.state
            is WalkLoop.Done -> return Value(result.value)
        }
    }
}

internal object WalkNull

internal enum class WalkKind { Inline, Inlines, Block, Blocks, Meta, MetaValue, Pandoc, Citation, Caption, Cell, Row, Head, Body, Foot, Other }
internal data class WalkFrame(val node: WalkNode, val next: Int = 0, val results: WalkResults? = null)
internal data class WalkStack(val frame: WalkFrame, val rest: WalkStack?)
internal data class WalkMachine(val frame: WalkFrame, val parents: WalkStack?)
internal class WalkResults(val value: Any, val previous: WalkResults?) {
    fun forward(): List<Any> {
        val values = ArrayList<Any>()
        var node: WalkResults? = this
        while (node != null) { values.add(node.value); node = node.previous }
        values.reverse()
        return values
    }
}
internal open class WalkNode(
    open val kind: WalkKind,
    open val original: Any,
    open val size: Int,
    open val child: (Int) -> WalkNode,
    open val rebuild: (List<Any>) -> Any,
    open val visit: Boolean = true,
    open val external: WalkExternal? = null,
) {
    companion object {
        fun <B> defer(kind: WalkKind, value: () -> B, visit: Boolean = true, build: (B) -> WalkNode): WalkNode = object : WalkNode(kind, Unit, 0, { error("deferred") }, { error("deferred") }, visit) {
            private val input by lazy(value)
            private val resolved by lazy { build(input) }
            override val original: Any get() = input as Any? ?: WalkNull
            override val external: WalkExternal? get() = resolved.external
            override val size: Int get() = resolved.size
            override val child: (Int) -> WalkNode get() = resolved.child
            override val rebuild: (List<Any>) -> Any get() = resolved.rebuild
        }
    }
}

/** A source list result can expose its outer value before its spine is demanded. */
internal class WalkListNode(
    kind: WalkKind, original: Any, private val extent: () -> Int,
    child: (Int) -> WalkNode, rebuild: (List<Any>) -> Any, visit: Boolean = false,
) : WalkNode(kind, original, 0, child, rebuild, visit) {
    override val size: Int get() = extent()
}

class WalkQuery<C> internal constructor(private val forward: Sequence<C>, val reversed: Sequence<C>) : Sequence<C> {
    override fun iterator() = forward.iterator()
}

fun interface WalkMonoid<C> {
    /** Fold in source order; the implementation controls demand of the lazy inputs. */
    fun fold(values: WalkQuery<C>): C
}

internal class WalkShape(private val target: WalkKind, private val resolve: ((WalkKind, () -> Any) -> WalkNode)? = null) {
    private fun childLazy(kind: WalkKind, value: () -> Any): WalkNode = resolve?.invoke(kind, value) ?: WalkNode.defer(kind, value) { node(kind, it) }
    private fun child(kind: WalkKind, value: Any): WalkNode = childLazy(kind) { value }
    private data class Slot<T>(val index: Int) { @Suppress("UNCHECKED_CAST") fun get(values: List<Any>): T = values[index] as T }
    private class Record(val kind: WalkKind, val value: Any) {
        val children = ArrayList<() -> WalkNode>()
        lateinit var result: (List<Any>) -> Any
        fun <T : Any> part(make: () -> WalkNode): Slot<T> = Slot<T>(children.size).also { children.add(make) }
        fun node(visit: Boolean) = WalkNode(kind, value, children.size, { children[it]() }, { result(it) }, visit)
    }
    fun node(kind: WalkKind, value: Any, visit: Boolean = true): WalkNode {
        val r = Record(kind, value)
        fun <T : Any> part(k: WalkKind, v: T): Slot<T> = r.part { child(k, v) }
        fun <T : Any> many(k: WalkKind, vs: List<T>): Slot<List<T>> = r.part { list(k, vs) }
        @Suppress("UNCHECKED_CAST")
        when (kind) {
            WalkKind.Inlines -> return list(WalkKind.Inline, value as List<Inline>, kind, visit)
            WalkKind.Blocks -> return list(WalkKind.Block, value as List<Block>, kind, visit)
            WalkKind.Pandoc -> {
                val x = value as Pandoc
                if (visit && target == WalkKind.Pandoc) r.result = { x }
                else if (visit && (target == WalkKind.Meta || target == WalkKind.MetaValue)) {
                    val m = part(WalkKind.Meta, x.meta); r.result = { x.copy(meta = m.get(it)) }
                } else {
                    val m = part(WalkKind.Meta, x.meta); val b = part(WalkKind.Blocks, x.blocks)
                    r.result = { x.copy(meta = m.get(it), blocks = b.get(it)) }
                }
            }
            WalkKind.Meta -> {
                val x = value as Map<String, MetaValue>
                if (target == WalkKind.Meta) r.result = { x }
                else {
                    val keys = x.keys.sortedWith(UnicodeTextOrder)
                    val m = r.part<Map<String, MetaValue>> {
                        WalkNode(WalkKind.Other, x, keys.size, { child(WalkKind.MetaValue, x.getValue(keys[it])) }, { values -> WalkDeferredMap(keys, WalkDeferredList(keys.size) { values[it] as MetaValue }) }, false)
                    }
                    r.result = { m.get(it) }
                }
            }
            WalkKind.MetaValue -> when (val x = value as MetaValue) {
                is MetaValue.MetaMap -> { val m = part(WalkKind.Meta, x.values); r.result = { x.copy(values = m.get(it)) } }
                is MetaValue.MetaList -> { val m = many(WalkKind.MetaValue, x.values); r.result = { x.copy(values = m.get(it)) } }
                is MetaValue.MetaInlines -> if (visit && target == WalkKind.MetaValue) r.result = { x } else { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is MetaValue.MetaBlocks -> if (visit && target == WalkKind.MetaValue) r.result = { x } else { val c = part(WalkKind.Blocks, x.content); r.result = { x.copy(content = c.get(it)) } }
                is MetaValue.MetaBool, is MetaValue.MetaString -> r.result = { x }
            }
            WalkKind.Inline -> when (val x = value as Inline) {
                is Inline.Emph -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Underline -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Strong -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Strikeout -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Superscript -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Subscript -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.SmallCaps -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Quoted -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Link -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Image -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Span -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Inline.Cite -> { val c = many(WalkKind.Citation, x.citations); val i = part(WalkKind.Inlines, x.content); r.result = { x.copy(citations = c.get(it), content = i.get(it)) } }
                is Inline.Note -> { val b = part(WalkKind.Blocks, x.blocks); r.result = { x.copy(blocks = b.get(it)) } }
                else -> r.result = { x }
            }
            WalkKind.Block -> when (val x = value as Block) {
                is Block.Plain -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Block.Para -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Block.Header -> { val c = part(WalkKind.Inlines, x.content); r.result = { x.copy(content = c.get(it)) } }
                is Block.LineBlock -> { val c = many(WalkKind.Inlines, x.lines); r.result = { x.copy(lines = c.get(it)) } }
                is Block.BlockQuote -> { val c = part(WalkKind.Blocks, x.blocks); r.result = { x.copy(blocks = c.get(it)) } }
                is Block.Div -> { val c = part(WalkKind.Blocks, x.blocks); r.result = { x.copy(blocks = c.get(it)) } }
                is Block.OrderedList -> { val c = many(WalkKind.Blocks, x.items); r.result = { x.copy(items = c.get(it)) } }
                is Block.BulletList -> { val c = many(WalkKind.Blocks, x.items); r.result = { x.copy(items = c.get(it)) } }
                is Block.DefinitionList -> {
                    val c = r.part<List<Pair<List<Inline>, List<List<Block>>>>> {
                        WalkListNode(WalkKind.Other, x.items, { x.items.size }, { index ->
                            val pair = x.items[index]
                            WalkNode(WalkKind.Other, pair, 2, { n -> if (n == 0) child(WalkKind.Inlines, pair.first) else list(WalkKind.Blocks, pair.second) }, { v ->
                                // Kotlin Pair fields are strict; list views retain the
                                // source pair's independent field demands without
                                // changing the public DefinitionList representation.
                                WalkDeferredList({ (v[0] as List<Inline>).size }) { index -> (v[0] as List<Inline>)[index] } to
                                    WalkDeferredList({ (v[1] as List<List<Block>>).size }) { index -> (v[1] as List<List<Block>>)[index] }
                            }, false)
                        }, { values -> WalkDeferredList(values.size) { values[it] as Pair<List<Inline>, List<List<Block>>> } }, false)
                    }
                    r.result = { x.copy(items = c.get(it)) }
                }
                is Block.Table -> {
                    val c = part(WalkKind.Caption, x.caption); val h = part(WalkKind.Head, x.head)
                    val b = many(WalkKind.Body, x.bodies); val f = part(WalkKind.Foot, x.foot)
                    r.result = { x.copy(caption = c.get(it), head = h.get(it), bodies = b.get(it), foot = f.get(it)) }
                }
                is Block.Figure -> { val c = part(WalkKind.Caption, x.caption); val b = part(WalkKind.Blocks, x.blocks); r.result = { x.copy(caption = c.get(it), blocks = b.get(it)) } }
                else -> r.result = { x }
            }
            WalkKind.Citation -> { val x = value as Citation; val p = part(WalkKind.Inlines, x.citationPrefix); val s = part(WalkKind.Inlines, x.citationSuffix); r.result = { x.copy(citationPrefix = p.get(it), citationSuffix = s.get(it)) } }
            WalkKind.Caption -> {
                val x = value as Caption; val s = x.short?.let { part(WalkKind.Inlines, it) }; val l = part(WalkKind.Blocks, x.long)
                r.result = { x.copy(short = s?.get(it), long = l.get(it)) }
            }
            WalkKind.Cell -> { val x = value as Cell; val b = part(WalkKind.Blocks, x.blocks); r.result = { x.copy(blocks = b.get(it)) } }
            WalkKind.Row -> { val x = value as Row; val c = many(WalkKind.Cell, x.cells); r.result = { x.copy(cells = c.get(it)) } }
            WalkKind.Head -> { val x = value as TableHead; val c = many(WalkKind.Row, x.rows); r.result = { x.copy(rows = c.get(it)) } }
            WalkKind.Body -> { val x = value as TableBody; val h = many(WalkKind.Row, x.head); val b = many(WalkKind.Row, x.body); r.result = { x.copy(head = h.get(it), body = b.get(it)) } }
            WalkKind.Foot -> { val x = value as TableFoot; val c = many(WalkKind.Row, x.rows); r.result = { x.copy(rows = c.get(it)) } }
            WalkKind.Other -> r.result = { value }
        }
        return r.node(visit)
    }
    private fun <T : Any> list(childKind: WalkKind, values: List<T>, kind: WalkKind = WalkKind.Other, visit: Boolean = false) =
        WalkListNode(kind, values, { values.size }, { index -> childLazy(childKind) { values[index] } }, { it }, visit)
}

/** Concrete instances declared by the pinned Text.Pandoc.Walk module. */
object Walk {
    val inlineInInline: Walkable<Inline, Inline> = dictionary(WalkKind.Inline, WalkKind.Inline)
    val inlinesInInlines: Walkable<List<Inline>, List<Inline>> = dictionary(WalkKind.Inlines, WalkKind.Inlines)
    val inlinesInInline: Walkable<List<Inline>, Inline> = dictionary(WalkKind.Inlines, WalkKind.Inline)
    val inlineInBlock: Walkable<Inline, Block> = dictionary(WalkKind.Inline, WalkKind.Block)
    val inlinesInBlock: Walkable<List<Inline>, Block> = dictionary(WalkKind.Inlines, WalkKind.Block)
    val blockInBlock: Walkable<Block, Block> = dictionary(WalkKind.Block, WalkKind.Block)
    val blocksInBlock: Walkable<List<Block>, Block> = dictionary(WalkKind.Blocks, WalkKind.Block)
    val blocksInBlocks: Walkable<List<Block>, List<Block>> = dictionary(WalkKind.Blocks, WalkKind.Blocks)
    val blockInInline: Walkable<Block, Inline> = dictionary(WalkKind.Block, WalkKind.Inline)
    val blocksInInline: Walkable<List<Block>, Inline> = dictionary(WalkKind.Blocks, WalkKind.Inline)
    val blockInPandoc: Walkable<Block, Pandoc> = dictionary(WalkKind.Block, WalkKind.Pandoc)
    val blocksInPandoc: Walkable<List<Block>, Pandoc> = dictionary(WalkKind.Blocks, WalkKind.Pandoc)
    val inlineInPandoc: Walkable<Inline, Pandoc> = dictionary(WalkKind.Inline, WalkKind.Pandoc)
    val inlinesInPandoc: Walkable<List<Inline>, Pandoc> = dictionary(WalkKind.Inlines, WalkKind.Pandoc)
    val metaInPandoc: Walkable<Map<String, MetaValue>, Pandoc> = dictionary(WalkKind.Meta, WalkKind.Pandoc)
    val metaValueInPandoc: Walkable<MetaValue, Pandoc> = dictionary(WalkKind.MetaValue, WalkKind.Pandoc)
    val pandocInPandoc: Walkable<Pandoc, Pandoc> = dictionary(WalkKind.Pandoc, WalkKind.Pandoc)
    val metaInMeta: Walkable<Map<String, MetaValue>, Map<String, MetaValue>> = dictionary(WalkKind.Meta, WalkKind.Meta)
    val inlineInMeta: Walkable<Inline, Map<String, MetaValue>> = dictionary(WalkKind.Inline, WalkKind.Meta)
    val inlinesInMeta: Walkable<List<Inline>, Map<String, MetaValue>> = dictionary(WalkKind.Inlines, WalkKind.Meta)
    val blockInMeta: Walkable<Block, Map<String, MetaValue>> = dictionary(WalkKind.Block, WalkKind.Meta)
    val blocksInMeta: Walkable<List<Block>, Map<String, MetaValue>> = dictionary(WalkKind.Blocks, WalkKind.Meta)
    val metaValueInMeta: Walkable<MetaValue, Map<String, MetaValue>> = dictionary(WalkKind.MetaValue, WalkKind.Meta)
    val metaValueInMetaValue: Walkable<MetaValue, MetaValue> = dictionary(WalkKind.MetaValue, WalkKind.MetaValue)
    val inlineInMetaValue: Walkable<Inline, MetaValue> = dictionary(WalkKind.Inline, WalkKind.MetaValue)
    val inlinesInMetaValue: Walkable<List<Inline>, MetaValue> = dictionary(WalkKind.Inlines, WalkKind.MetaValue)
    val blockInMetaValue: Walkable<Block, MetaValue> = dictionary(WalkKind.Block, WalkKind.MetaValue)
    val blocksInMetaValue: Walkable<List<Block>, MetaValue> = dictionary(WalkKind.Blocks, WalkKind.MetaValue)
    val inlineInRow: Walkable<Inline, Row> = dictionary(WalkKind.Inline, WalkKind.Row)
    val inlinesInRow: Walkable<List<Inline>, Row> = dictionary(WalkKind.Inlines, WalkKind.Row)
    val blockInRow: Walkable<Block, Row> = dictionary(WalkKind.Block, WalkKind.Row)
    val blocksInRow: Walkable<List<Block>, Row> = dictionary(WalkKind.Blocks, WalkKind.Row)
    val inlineInHead: Walkable<Inline, TableHead> = dictionary(WalkKind.Inline, WalkKind.Head)
    val inlinesInHead: Walkable<List<Inline>, TableHead> = dictionary(WalkKind.Inlines, WalkKind.Head)
    val blockInHead: Walkable<Block, TableHead> = dictionary(WalkKind.Block, WalkKind.Head)
    val blocksInHead: Walkable<List<Block>, TableHead> = dictionary(WalkKind.Blocks, WalkKind.Head)
    val inlineInBody: Walkable<Inline, TableBody> = dictionary(WalkKind.Inline, WalkKind.Body)
    val inlinesInBody: Walkable<List<Inline>, TableBody> = dictionary(WalkKind.Inlines, WalkKind.Body)
    val blockInBody: Walkable<Block, TableBody> = dictionary(WalkKind.Block, WalkKind.Body)
    val blocksInBody: Walkable<List<Block>, TableBody> = dictionary(WalkKind.Blocks, WalkKind.Body)
    val inlineInFoot: Walkable<Inline, TableFoot> = dictionary(WalkKind.Inline, WalkKind.Foot)
    val inlinesInFoot: Walkable<List<Inline>, TableFoot> = dictionary(WalkKind.Inlines, WalkKind.Foot)
    val blockInFoot: Walkable<Block, TableFoot> = dictionary(WalkKind.Block, WalkKind.Foot)
    val blocksInFoot: Walkable<List<Block>, TableFoot> = dictionary(WalkKind.Blocks, WalkKind.Foot)
    val inlineInCaption: Walkable<Inline, Caption> = dictionary(WalkKind.Inline, WalkKind.Caption)
    val inlinesInCaption: Walkable<List<Inline>, Caption> = dictionary(WalkKind.Inlines, WalkKind.Caption)
    val blockInCaption: Walkable<Block, Caption> = dictionary(WalkKind.Block, WalkKind.Caption)
    val blocksInCaption: Walkable<List<Block>, Caption> = dictionary(WalkKind.Blocks, WalkKind.Caption)
    val inlineInCell: Walkable<Inline, Cell> = dictionary(WalkKind.Inline, WalkKind.Cell)
    val inlinesInCell: Walkable<List<Inline>, Cell> = dictionary(WalkKind.Inlines, WalkKind.Cell)
    val blockInCell: Walkable<Block, Cell> = dictionary(WalkKind.Block, WalkKind.Cell)
    val blocksInCell: Walkable<List<Block>, Cell> = dictionary(WalkKind.Blocks, WalkKind.Cell)
    val inlineInCitation: Walkable<Inline, Citation> = dictionary(WalkKind.Inline, WalkKind.Citation)
    val inlinesInCitation: Walkable<List<Inline>, Citation> = dictionary(WalkKind.Inlines, WalkKind.Citation)
    val blockInCitation: Walkable<Block, Citation> = dictionary(WalkKind.Block, WalkKind.Citation)
    val blocksInCitation: Walkable<List<Block>, Citation> = dictionary(WalkKind.Blocks, WalkKind.Citation)
    private fun <A, B : Any> dictionary(target: WalkKind, root: WalkKind): Walkable<A, B> = AstWalkable({ WalkShape(target).node(root, it) }, target, { supply -> WalkNode.defer(root, supply) { WalkShape(target).node(root, it) } })
}

/** The exported helpers traverse children only, using the supplied child dictionaries. */
object WalkHelpers {
    internal fun <A, B : Any> helper(kind: WalkKind, dictionaries: Map<WalkKind, Walkable<A, *>>): Walkable<A, B> {
        val target = combinedTarget(dictionaries.values)
        lateinit var shape: WalkShape
        shape = WalkShape(target) { k, v ->
            val dictionary = dictionaries[k]
            if (dictionary == null) WalkNode.defer(k, v, false) { shape.node(k, it, false) }
            else {
                @Suppress("UNCHECKED_CAST")
                dictionaryNode(dictionary as Walkable<A, Any>, v)
            }
        }
        return AstWalkable({ shape.node(kind, it, false) }, target)
    }
    fun <F, A> walkInlineM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: Inline, citation: Walkable<A, Citation>, blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>): WalkEffect<F, Inline> =
        helper<A, Inline>(WalkKind.Inline, mapOf(WalkKind.Citation to citation, WalkKind.Blocks to blocks, WalkKind.Inlines to inlines)).walkM(monad, transform, value)
    fun <A, C> queryInline(transform: (A) -> C, value: Inline, monoid: WalkMonoid<C>, citation: Walkable<A, Citation>, blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>): C =
        helper<A, Inline>(WalkKind.Inline, mapOf(WalkKind.Citation to citation, WalkKind.Blocks to blocks, WalkKind.Inlines to inlines)).query(transform, value, monoid)
    fun <F, A> walkBlockM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: Block, blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>, row: Walkable<A, Row>, caption: Walkable<A, Caption>, head: Walkable<A, TableHead>, body: Walkable<A, TableBody>, foot: Walkable<A, TableFoot>, citation: Walkable<A, Citation>): WalkEffect<F, Block> =
        helper<A, Block>(WalkKind.Block, mapOf(WalkKind.Blocks to blocks, WalkKind.Inlines to inlines, WalkKind.Row to row, WalkKind.Caption to caption, WalkKind.Head to head, WalkKind.Body to body, WalkKind.Foot to foot, WalkKind.Citation to citation)).walkM(monad, transform, value)
    fun <A, C> queryBlock(transform: (A) -> C, value: Block, monoid: WalkMonoid<C>, blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>, row: Walkable<A, Row>, caption: Walkable<A, Caption>, head: Walkable<A, TableHead>, body: Walkable<A, TableBody>, foot: Walkable<A, TableFoot>, citation: Walkable<A, Citation>): C =
        helper<A, Block>(WalkKind.Block, mapOf(WalkKind.Blocks to blocks, WalkKind.Inlines to inlines, WalkKind.Row to row, WalkKind.Caption to caption, WalkKind.Head to head, WalkKind.Body to body, WalkKind.Foot to foot, WalkKind.Citation to citation)).query(transform, value, monoid)
    fun <F, A> walkMetaValueM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: MetaValue, metaValue: Walkable<A, MetaValue>, blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>): WalkEffect<F, MetaValue> =
        helper<A, MetaValue>(WalkKind.MetaValue, mapOf(WalkKind.MetaValue to metaValue, WalkKind.Blocks to blocks, WalkKind.Inlines to inlines)).walkM(monad, transform, value)
    fun <A, C> queryMetaValue(transform: (A) -> C, value: MetaValue, monoid: WalkMonoid<C>, metaValue: Walkable<A, MetaValue>, blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>): C =
        helper<A, MetaValue>(WalkKind.MetaValue, mapOf(WalkKind.MetaValue to metaValue, WalkKind.Blocks to blocks, WalkKind.Inlines to inlines)).query(transform, value, monoid)
    fun <F, A> walkCitationM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: Citation, inlines: Walkable<A, List<Inline>>): WalkEffect<F, Citation> =
        helper<A, Citation>(WalkKind.Citation, mapOf(WalkKind.Inlines to inlines)).walkM(monad, transform, value)
    fun <A, C> queryCitation(transform: (A) -> C, value: Citation, monoid: WalkMonoid<C>, inlines: Walkable<A, List<Inline>>): C =
        helper<A, Citation>(WalkKind.Citation, mapOf(WalkKind.Inlines to inlines)).query(transform, value, monoid)
    fun <F, A> walkRowM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: Row, cell: Walkable<A, Cell>): WalkEffect<F, Row> =
        helper<A, Row>(WalkKind.Row, mapOf(WalkKind.Cell to cell)).walkM(monad, transform, value)
    fun <A, C> queryRow(transform: (A) -> C, value: Row, monoid: WalkMonoid<C>, cell: Walkable<A, Cell>): C =
        helper<A, Row>(WalkKind.Row, mapOf(WalkKind.Cell to cell)).query(transform, value, monoid)
    fun <F, A> walkTableHeadM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: TableHead, row: Walkable<A, Row>): WalkEffect<F, TableHead> =
        helper<A, TableHead>(WalkKind.Head, mapOf(WalkKind.Row to row)).walkM(monad, transform, value)
    fun <A, C> queryTableHead(transform: (A) -> C, value: TableHead, monoid: WalkMonoid<C>, row: Walkable<A, Row>): C =
        helper<A, TableHead>(WalkKind.Head, mapOf(WalkKind.Row to row)).query(transform, value, monoid)
    fun <F, A> walkTableBodyM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: TableBody, row: Walkable<A, Row>): WalkEffect<F, TableBody> =
        helper<A, TableBody>(WalkKind.Body, mapOf(WalkKind.Row to row)).walkM(monad, transform, value)
    fun <A, C> queryTableBody(transform: (A) -> C, value: TableBody, monoid: WalkMonoid<C>, row: Walkable<A, Row>): C =
        helper<A, TableBody>(WalkKind.Body, mapOf(WalkKind.Row to row)).query(transform, value, monoid)
    fun <F, A> walkTableFootM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: TableFoot, row: Walkable<A, Row>): WalkEffect<F, TableFoot> =
        helper<A, TableFoot>(WalkKind.Foot, mapOf(WalkKind.Row to row)).walkM(monad, transform, value)
    fun <A, C> queryTableFoot(transform: (A) -> C, value: TableFoot, monoid: WalkMonoid<C>, row: Walkable<A, Row>): C =
        helper<A, TableFoot>(WalkKind.Foot, mapOf(WalkKind.Row to row)).query(transform, value, monoid)
    fun <F, A> walkCellM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: Cell, blocks: Walkable<A, List<Block>>): WalkEffect<F, Cell> =
        helper<A, Cell>(WalkKind.Cell, mapOf(WalkKind.Blocks to blocks)).walkM(monad, transform, value)
    fun <A, C> queryCell(transform: (A) -> C, value: Cell, monoid: WalkMonoid<C>, blocks: Walkable<A, List<Block>>): C =
        helper<A, Cell>(WalkKind.Cell, mapOf(WalkKind.Blocks to blocks)).query(transform, value, monoid)
    // ShortCaption is [Inline]; the source resolves Maybe traversal from that
    // dictionary inside the helper, rather than accepting a Maybe dictionary.
    private fun <A> captionHelper(blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>): Walkable<A, Caption> {
        val target = combinedTarget(listOf(blocks, inlines))
        val shortCaption = Walk.traversable(inlines, WalkContainers.nullable())
        return AstWalkable({ value -> WalkNode(WalkKind.Caption, value, 2,
            { if (it == 0) dictionaryNode(shortCaption) { value.short } else dictionaryNode(blocks) { value.long } },
            { @Suppress("UNCHECKED_CAST") value.copy(short = if (it[0] === WalkNull) null else it[0] as List<Inline>, long = it[1] as List<Block>) }, false)
        }, target)
    }
    fun <F, A> walkCaptionM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: Caption, blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>): WalkEffect<F, Caption> =
        captionHelper(blocks, inlines).walkM(monad, transform, value)
    fun <A, C> queryCaption(transform: (A) -> C, value: Caption, monoid: WalkMonoid<C>, blocks: Walkable<A, List<Block>>, inlines: Walkable<A, List<Inline>>): C =
        captionHelper(blocks, inlines).query(transform, value, monoid)
    fun <F, A> walkPandocM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: Pandoc, meta: Walkable<A, Map<String, MetaValue>>, blocks: Walkable<A, List<Block>>): WalkEffect<F, Pandoc> =
        helper<A, Pandoc>(WalkKind.Pandoc, mapOf(WalkKind.Meta to meta, WalkKind.Blocks to blocks)).walkM(monad, transform, value)
    fun <A, C> queryPandoc(transform: (A) -> C, value: Pandoc, monoid: WalkMonoid<C>, meta: Walkable<A, Map<String, MetaValue>>, blocks: Walkable<A, List<Block>>): C =
        helper<A, Pandoc>(WalkKind.Pandoc, mapOf(WalkKind.Meta to meta, WalkKind.Blocks to blocks)).query(transform, value, monoid)
    fun <F> walkMetaValueMPrime(monad: WalkMonad<F>, transform: (MetaValue) -> WalkEffect<F, MetaValue>, value: MetaValue): WalkEffect<F, MetaValue> =
        AstWalkable<MetaValue, MetaValue>({ WalkShape(WalkKind.MetaValue).node(WalkKind.MetaValue, it).let { n -> WalkNode(n.kind, n.original, n.size, n.child, n.rebuild, false) } }, WalkKind.MetaValue).walkM(monad, transform, value)
    fun <C> queryMetaValuePrime(transform: (MetaValue) -> C, value: MetaValue, monoid: WalkMonoid<C>): C =
        AstWalkable<MetaValue, MetaValue>({ WalkShape(WalkKind.MetaValue).node(WalkKind.MetaValue, it).let { n -> WalkNode(n.kind, n.original, n.size, n.child, n.rebuild, false) } }, WalkKind.MetaValue).query(transform, value, monoid)
}

/** Finite ordered view of a source Traversable container, including reconstruction. */
interface WalkTraversable<B, T> {
    fun size(value: T): Int
    fun element(value: T, index: Int): B
    fun rebuild(original: T, elements: List<B>): T
    fun view(value: T): WalkContainerView<B, T> = WalkContainerView(size(value), { element(value, it) }, { rebuild(value, it) })
}
class WalkContainerView<B, T>(val size: Int, val element: (Int) -> B, val rebuild: (List<B>) -> T)

/** The generic instance body; select the named concrete dictionary for overlapping list instances. */
fun <A, B, T> Walk.traversable(element: Walkable<A, B>, container: WalkTraversable<B, T>): Walkable<A, T> =
    AstWalkable({ value ->
        val view = container.view(value)
        WalkNode(WalkKind.Other, value as Any? ?: WalkNull, view.size, { index -> dictionaryNode(element) { view.element(index) } }, {
            @Suppress("UNCHECKED_CAST")
            view.rebuild(WalkDeferredList(it.size) { index -> if (it[index] === WalkNull) null else it[index] } as List<B>) as Any? ?: WalkNull
        }, false)
    }, dictionaryTarget(element), pureWalk = { transform, value ->
        val view = container.view(value)
        view.rebuild(WalkDeferredList(view.size) { index -> element.walk(transform, view.element(index)) })
    }, lazyPureWalk = { transform, value ->
        val view = container.view(value())
        view.rebuild(WalkDeferredList(view.size) { index -> element.walkLazy(transform) { view.element(index) } })
    })

/** The OVERLAPS pair instance traverses both fields, in left-to-right order. */
fun <A, B, C> Walk.pair(left: Walkable<A, B>, right: Walkable<A, C>): Walkable<A, Pair<B, C>> {
    val target = combinedTarget(listOf(left, right))
    return AstWalkable({ value ->
        WalkNode(WalkKind.Other, value, 2, { if (it == 0) dictionaryNode(left) { value.first } else dictionaryNode(right) { value.second } }, {
            @Suppress("UNCHECKED_CAST")
            ((if (it[0] === WalkNull) null else it[0]) as B) to ((if (it[1] === WalkNull) null else it[1]) as C)
        }, false)
    }, target, pureWalk = { transform, value -> left.walk(transform, value.first) to right.walk(transform, value.second) })
}

/** Explicit source pair fields when either field may be unevaluated.
 * Ordinary Kotlin Pair remains the strict-field convenience representation. */
class WalkLazyPair<B, C>(first: () -> B, second: () -> C) {
    private val left by lazy { runCatching(first) }
    private val right by lazy { runCatching(second) }
    val first: B get() = left.getOrThrow()
    val second: C get() = right.getOrThrow()
}

/** The pair instance with independently memoized source fields. */
fun <A, B, C> Walk.lazyPair(left: Walkable<A, B>, right: Walkable<A, C>): Walkable<A, WalkLazyPair<B, C>> =
    object : Walkable<A, WalkLazyPair<B, C>> {
        override fun walk(transform: (A) -> A, value: WalkLazyPair<B, C>): WalkLazyPair<B, C> =
            walkLazy({ transform(it()) }, { value })
        override fun walkLazy(transform: (() -> A) -> A, value: () -> WalkLazyPair<B, C>): WalkLazyPair<B, C> {
            val input by lazy(value)
            return WalkLazyPair({ left.walkLazy(transform) { input.first } }, { right.walkLazy(transform) { input.second } })
        }
        override fun <F> walkM(monad: WalkMonad<F>, transform: (A) -> WalkEffect<F, A>, value: WalkLazyPair<B, C>): WalkEffect<F, WalkLazyPair<B, C>> =
            monad.flatMap(left.walkM(monad, transform, value.first)) { first ->
                monad.flatMap(right.walkM(monad, transform, value.second)) { second -> monad.pure(WalkLazyPair({ first }, { second })) }
            }
        override fun <D> query(transform: (A) -> D, value: WalkLazyPair<B, C>, monoid: WalkMonoid<D>): D =
            queryLazy({ transform(it()) }, { value }, monoid)
        override fun <D> queryLazy(transform: (() -> A) -> D, value: () -> WalkLazyPair<B, C>, monoid: WalkMonoid<D>): D {
            val input by lazy(value)
            val first by lazy { left.queryLazy(transform, { input.first }, monoid) }
            val second by lazy { right.queryLazy(transform, { input.second }, monoid) }
            return monoid.fold(WalkQuery(sequence { yield(first); yield(second) }, sequence { yield(second); yield(first) }))
        }
    }

object WalkContainers {
    val inlines: WalkTraversable<Inline, Inlines> = object : WalkTraversable<Inline, Inlines> {
        override fun size(value: Inlines) = value.values.size
        override fun element(value: Inlines, index: Int) = value.values[index]
        override fun rebuild(original: Inlines, elements: List<Inline>) = Inlines(elements)
    }
    val blocks: WalkTraversable<Block, Blocks> = object : WalkTraversable<Block, Blocks> {
        override fun size(value: Blocks) = value.values.size
        override fun element(value: Blocks, index: Int) = value.values[index]
        override fun rebuild(original: Blocks, elements: List<Block>) = Blocks(elements)
    }
    fun <B> list(): WalkTraversable<B, List<B>> = object : WalkTraversable<B, List<B>> {
        override fun size(value: List<B>) = value.size
        override fun element(value: List<B>, index: Int) = value[index]
        override fun rebuild(original: List<B>, elements: List<B>) = elements
    }
    fun <B : Any> nullable(): WalkTraversable<B, B?> = object : WalkTraversable<B, B?> {
        override fun size(value: B?) = if (value == null) 0 else 1
        override fun element(value: B?, index: Int): B { require(index == 0); return value!! }
        override fun rebuild(original: B?, elements: List<B>) = elements.firstOrNull()
    }
    fun <K, B> map(order: Comparator<K>): WalkTraversable<B, Map<K, B>> = object : WalkTraversable<B, Map<K, B>> {
        override fun size(value: Map<K, B>) = value.size
        override fun element(value: Map<K, B>, index: Int) = value.getValue(value.keys.sortedWith(order)[index])
        override fun rebuild(original: Map<K, B>, elements: List<B>): Map<K, B> = WalkDeferredMap(original.keys.sortedWith(order), elements)
        override fun view(value: Map<K, B>): WalkContainerView<B, Map<K, B>> {
            val keys = value.keys.sortedWith(order)
            return WalkContainerView(keys.size, { value.getValue(keys[it]) }, { WalkDeferredMap(keys, it) })
        }
    }
}

sealed interface WalkOptional<out C> {
    data object None : WalkOptional<Nothing>
    data class Some<C>(val value: C) : WalkOptional<C>
}
object WalkMonoids {
    /** Lazy list-monoid adapter: prefixes do not demand later query branches. */
    fun <C> sequence(): WalkMonoid<Sequence<C>> = WalkMonoid { values -> values.flatMap { it } }
    fun <C> list(): WalkMonoid<List<C>> = WalkMonoid { values -> values.flatMap { it.asSequence() }.toList() }
    fun <C> first(): WalkMonoid<WalkOptional<C>> = WalkMonoid { values -> values.firstOrNull { it is WalkOptional.Some } ?: WalkOptional.None }
    fun <C> last(): WalkMonoid<WalkOptional<C>> = WalkMonoid { values -> values.reversed.firstOrNull { it is WalkOptional.Some } ?: WalkOptional.None }
    val unit: WalkMonoid<Unit> = WalkMonoid { Unit }
    val any: WalkMonoid<Boolean> = WalkMonoid { it.any { value -> value } }
    val all: WalkMonoid<Boolean> = WalkMonoid { it.all { value -> value } }
    val sumLong: WalkMonoid<Long> = WalkMonoid { it.fold(0L, Long::plus) }
}

/** Memoized immutable list: constructing its spine never evaluates an element. */
internal class WalkDeferredList<T>(extent: () -> Int, private val element: (Int) -> T) : AbstractList<T>() {
    constructor(size: Int, element: (Int) -> T) : this({ size }, element)
    private val extentResult by lazy { runCatching(extent) }
    override val size: Int get() = extentResult.getOrThrow()
    private val elements = HashMap<Int, Lazy<Result<T>>>()
    override fun get(index: Int): T {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index: $index, size: $size")
        val cell = synchronized(elements) { elements.getOrPut(index) { lazy { runCatching { element(index) } } } }
        return cell.value.getOrThrow()
    }
}

internal class WalkDeferredMap<K, V>(private val orderedKeys: List<K>, private val mappedValues: List<V>) : AbstractMap<K, V>() {
    override val entries: Set<Map.Entry<K, V>> = object : AbstractSet<Map.Entry<K, V>>() {
        override val size: Int get() = orderedKeys.size
        override fun iterator(): Iterator<Map.Entry<K, V>> = orderedKeys.indices.asSequence().map { index ->
            object : Map.Entry<K, V> {
                override val key: K get() = orderedKeys[index]
                override val value: V get() = mappedValues[index]
                override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && key == other.key && value == other.value
                override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
                override fun toString(): String = "$key=$value"
            }
        }.iterator()
    }
}

internal interface WalkExternal {
    fun walkLazy(transform: (() -> Any?) -> Any?): Any?

    fun <F> walkM(monad: WalkMonad<F>, transform: (Any?) -> WalkEffect<F, Any?>): WalkEffect<F, Any?>
    fun <C> query(transform: (() -> Any?) -> C, monoid: WalkMonoid<C>): C
}
internal fun dictionaryTarget(dictionary: Walkable<*, *>): WalkKind = (dictionary as? AstWalkable<*, *>)?.target ?: WalkKind.Other
@Suppress("UNCHECKED_CAST")
internal fun <A, B> dictionaryNode(dictionary: Walkable<A, B>, value: () -> B): WalkNode {
    if (dictionary is AstWalkable<A, B>) return dictionary.lazyPlan(value)
    val external=object:WalkExternal {
        override fun walkLazy(transform: (() -> Any?) -> Any?): Any? =
            dictionary.walkLazy({ thunk -> transform { thunk() } as A }, value)

        override fun <F> walkM(monad: WalkMonad<F>, transform: (Any?) -> WalkEffect<F, Any?>): WalkEffect<F, Any?> =
            dictionary.walkM(monad, { transform(it) as WalkEffect<F, A> }, value()) as WalkEffect<F, Any?>
        override fun <C> query(transform: (() -> Any?) -> C, monoid: WalkMonoid<C>): C = dictionary.queryLazy({ thunk -> transform { thunk() } }, value, monoid)
    }
    return WalkNode(WalkKind.Other, Unit, 0, { error("external") }, { error("external") }, false, external)
}

internal fun combinedTarget(dictionaries: Collection<Walkable<*, *>>): WalkKind {
    val known=dictionaries.map(::dictionaryTarget).filter { it != WalkKind.Other }.distinct()
    require(known.size <= 1)
    return known.firstOrNull() ?: WalkKind.Other
}
