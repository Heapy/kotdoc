// Control.DeepSeq: Copyright (c) The University of Glasgow 2001-2009.
// BSD-style; the complete original notice is retained in licenses/deepseq-BSD-3-Clause.txt.
package io.heapy.kotdoc.ast

/** A source value thunk: demanding it also observes intrinsic constructor WHNF. */
class SourceThunk<A>(value: () -> A) : () -> A {
    private val input = GenericMemo {
        @Suppress("UNCHECKED_CAST")
        (DeepSeqData.sourceWHNF(value()) as A)
    }
    override fun invoke(): A = input.get()
}
fun <A> NFData<A>.rnf(value: () -> A) = rnf(SourceThunk(value))

/** Source NFData dictionary. Callbacks represent values whose demand may fail. */
fun interface NFData<A> {
    fun rnf(value: SourceThunk<A>)
}

/** One deferred dictionary application; successful values and failures are shared. */
class DeepSeqValue<A>(val dictionary: NFData<A>, value: () -> A) {
    private val input = SourceThunk(value)
    fun value(): A = input()
}

internal interface PlannedNFData<A> : NFData<A> {
    fun children(value: () -> A): Iterator<DeepSeqValue<*>>
    override fun rnf(value: SourceThunk<A>) = PandocDeepSeq.run(DeepSeqValue(this, value))
}

object PandocDeepSeq {
    fun <A> rnf(dictionary: NFData<A>, value: () -> A) = dictionary.rnf(value)

    /** Explicit root-demand adapter for the source's lazy unit result. */
    fun <A> rnfLazy(dictionary: NFData<A>, value: () -> A): () -> Unit {
        val result=GenericMemo { dictionary.rnf(value) }
        return result::get
    }
    fun rwhnfLazy(value:DeepSeqValue<*>):()->Unit {
        val result=GenericMemo { rwhnf(value) }
        return result::get
    }

    /** Source force is lazy until its returned value is demanded. */
    fun <A> force(dictionary: NFData<A>, value: () -> A): () -> A {
        val input = SourceThunk(value)
        val result = GenericMemo { dictionary.rnf(input); input() }
        return result::get
    }

    fun <A, B> deepseq(dictionary: NFData<A>, value: () -> A, result: () -> B): () -> B {
        val output = SourceThunk { dictionary.rnf(value); result() }
        return output
    }

    fun <A, B> strictApply(dictionary: NFData<A>, function: () -> (() -> A) -> B, value: () -> A): () -> B {
        val input = SourceThunk(value)
        return deepseq(dictionary, input) { function()(input) }
    }

    /** Plain Kotlin root callback. Source constructor/newtype WHNF is represented
     * by DeepSeqData's root callbacks rather than arbitrary JVM object creation. */
    fun <A> rwhnf(value: () -> A) { SourceThunk(value)() }
    /** WHNF adapter for reified source constructors, including StrictData/newtypes. */
    fun rwhnf(value: DeepSeqValue<*>) { DeepSeqData.whnf(value) }

    internal fun run(root: DeepSeqValue<*>) {
        val pending = ArrayDeque<Iterator<DeepSeqValue<*>>>()
        pending.addLast(listOf(root).iterator())
        while (pending.isNotEmpty()) {
            val iterator = pending.last()
            if (!iterator.hasNext()) { pending.removeLast(); continue }
            val current = iterator.next()
            @Suppress("UNCHECKED_CAST")
            val dictionary = current.dictionary as NFData<Any?>
            if (dictionary is PlannedNFData<Any?>) {
                pending.addLast(dictionary.children { current.value() })
            } else dictionary.rnf { current.value() }
        }
    }
}
