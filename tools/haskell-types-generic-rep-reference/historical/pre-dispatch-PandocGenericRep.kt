// Derived representation generation: (c) The University of Glasgow 2011.
// GHC.Internal.Generics: (c) Universiteit Utrecht 2010-2011, University of Oxford 2012-2014.
// Original GHC notice: licenses/GHC-base-BSD-3-Clause.txt.
// pandoc-types Copyright (c) 2006–2023 John MacFarlane, BSD-3-Clause.
package io.heapy.kotdoc.ast

/** Static representation identity. M1 and K1 are source newtypes, not data nodes. */
sealed interface GenericRepType {
    data object Unit : GenericRepType
    data class Field(val type: GenericType) : GenericRepType
    data class Product(val left: GenericRepType, val right: GenericRepType) : GenericRepType
    data class Sum(val left: GenericRepType, val right: GenericRepType) : GenericRepType
    data class Metadata(val information: GenericRepMetadata, val child: GenericRepType) : GenericRepType
}
sealed interface GenericRepMetadata {
    data class Datatype(val name: String, val module: String, val packageName: String, val newtype: Boolean) : GenericRepMetadata
    data class Constructor(val name: String, val fixity: String, val record: Boolean) : GenericRepMetadata
    data class Selector(val name: String, val unpackedness: String, val strictness: String, val decidedStrictness: String) : GenericRepMetadata
}
sealed interface GenericRepLayer {
    data object Unit : GenericRepLayer
    class Field(val value: GenericRepField) : GenericRepLayer
    class Product(val left: GenericRepValue, val right: GenericRepValue) : GenericRepLayer
    class Sum(val right: Boolean, val child: GenericRepValue) : GenericRepLayer
    class Metadata(val child: GenericRepValue) : GenericRepLayer
}
/** Open source-carrier root protocol, independent of Generic from/to dictionaries. */
interface GenericRepRoot { fun forceGenericRepRoot() }

/** Shared intrinsic source WHNF for both field and dictionary-result boundaries. */
internal object GenericRepIntrinsic {
    fun force(value:Any?):Any? {
        val root=SourceThunk{value}()
        when(root){
            is GenericRepRoot->root.forceGenericRepRoot()
            is Many<*>->tree(root.tree.get())
            is ManySequence<*>->tree(root.tree.get())
        }
        return root
    }
    private fun tree(value:ManyTree<*>) {
        if(value is ManyTree.Deep){SourceThunk{value.prefix}();SourceThunk{value.suffix}()}
    }
}

/** K1 field has no class dictionary constraint. Demand is intrinsic source WHNF. */
class GenericRepField(val type: GenericType, value: () -> Any?) {
    private val input=GenericMemo { GenericRepIntrinsic.force(value()) }
    fun value():Any?=input.get()
}
/** Layer access models source pattern projection. whnf separately erases newtypes. */
class GenericRepValue(val type: GenericRepType, layer: () -> GenericRepLayer):GenericRepRoot {
    private val input = GenericMemo(layer)
    fun layer(): GenericRepLayer = input.get().also { node ->
        require(when(val spec=type) {
            GenericRepType.Unit -> node===GenericRepLayer.Unit
            is GenericRepType.Field -> node is GenericRepLayer.Field && node.value.type==spec.type
            is GenericRepType.Product -> node is GenericRepLayer.Product && node.left.type==spec.left && node.right.type==spec.right
            is GenericRepType.Sum -> node is GenericRepLayer.Sum && node.child.type==(if(node.right)spec.right else spec.left)
            is GenericRepType.Metadata -> node is GenericRepLayer.Metadata && node.child.type==spec.child
        }) { "Representation layer does not match its source type" }
    }
    override fun forceGenericRepRoot(){whnf()}
    fun whnf() {
        var current=this
        while(true) when(val node=current.layer()) {
            is GenericRepLayer.Metadata -> current=node.child
            is GenericRepLayer.Field -> {node.value.value();return}
            else -> return
        }
    }
}
/** Root-demand protocol for source values, independently of custom dictionaries. */
class GenericRepThunk<A>(value:()->A):()->A {
    private val input=GenericMemo {
        @Suppress("UNCHECKED_CAST")
        (GenericRepIntrinsic.force(value()) as A)
    }
    override fun invoke():A=input.get()
}
fun <A> GenericRepDictionary<A>.from(value:()->A):GenericRepValue=from(GenericRepThunk(value))

/** Generic has two methods and associated Rep; user dictionaries remain explicit. */
interface GenericRepDictionary<A> {
    val representation: GenericRepType
    fun from(value: GenericRepThunk<A>): GenericRepValue
    fun to(value: GenericRepValue): GenericRepThunk<A>
}
object PandocGenericRep {
    fun <A> from(dictionary: GenericRepDictionary<A>, value: () -> A): GenericRepValue = dictionary.from(value)
    fun <A> to(dictionary: GenericRepDictionary<A>, value: GenericRepValue): GenericRepThunk<A> = dictionary.to(value)
}
