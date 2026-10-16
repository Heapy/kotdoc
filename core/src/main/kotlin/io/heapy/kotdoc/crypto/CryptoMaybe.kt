package io.heapy.kotdoc.crypto

sealed interface CryptoMaybe<out A> {
    data object None : CryptoMaybe<Nothing>
    class Some<out A> private constructor(private val stored: Lazy<A>) : CryptoMaybe<A> {
        constructor(value: A) : this(lazyOf(value))
        val value: A get() = stored.value
        override fun equals(other: Any?) = other is Some<*> && value == other.value
        override fun hashCode() = value?.hashCode() ?: 0
        companion object { fun <A> deferred(value: () -> A) = Some(lazy(value)) }
    }
}
