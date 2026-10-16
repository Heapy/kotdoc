// Data.X509.CertificateStore, crypton-x509-store 1.9.0; Vincent Hanquez.
// licenses/crypton-x509-store-BSD-3-Clause.txt; Map balance: Containers-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Subject-indexed maps and ordered composites retain the upstream duplicate policy. */
sealed class X509CertificateStore private constructor() {
    private class Node(val key: Lazy<X509DistinguishedName>,val cert: Lazy<X509SignedCertificate>,val left: Node?,val right: Node?) {
        val size: Int=1+(left?.size ?: 0)+(right?.size ?: 0)
    }
    private class Single(val tree: Lazy<Node?>): X509CertificateStore()
    private class Composite(val stores: X509Values<X509CertificateStore>): X509CertificateStore()
    private class Deferred(val store: Lazy<X509CertificateStore>): X509CertificateStore()
    private fun resolved(): X509CertificateStore {var current=this;while(current is Deferred) current=current.store.value;return current}
    fun isEmpty(): Boolean = when(this) {
        is Deferred -> store.value.isEmpty()
        is Single -> tree.value==null
        is Composite -> !stores.cells().iterator().hasNext()
    }
    operator fun plus(other: X509CertificateStore): X509CertificateStore {
        val left=resolved();val right=other.resolved()
        fun children(s: X509CertificateStore)=if(s is Composite) s.stores.cells() else sequenceOf(lazyOf(s))
        return compositeDeferred {children(left)+children(right)}
    }
    fun findCertificate(dn: X509DistinguishedName): X509SignedCertificate? = findCertificateCell(lazyOf(dn))?.value
    fun findCertificateCell(dn: Lazy<X509DistinguishedName>): Lazy<X509SignedCertificate>? = when(this) {
        is Deferred -> store.value.findCertificateCell(dn)
        is Single -> {
            // Map.lookup is strict in the DN even when its map is empty/bottom.
            dn.value.elements;var n=tree.value;var found: Lazy<X509SignedCertificate>?=null
            while(n!=null) {val cmp=dn.value.compareTo(n.key.value);if(cmp==0) {found=n.cert;break};n=if(cmp<0) n.left else n.right}
            found
        }
        is Composite -> {
            // foldl mplus traverses the complete outer spine before trying the first lookup.
            val children=stores.cells().toList()
            var found: Lazy<X509SignedCertificate>?=null
            for(cell in children) {found=cell.value.findCertificateCell(dn);if(found!=null) break}
            found
        }
    }
    fun certificateCells(): Sequence<Lazy<X509SignedCertificate>> = when(this) {
        is Deferred -> sequence {yieldAll(store.value.certificateCells())}
        is Single -> sequence {
            suspend fun SequenceScope<Lazy<X509SignedCertificate>>.walk(n: Node?) {
                if(n!=null) {walk(n.left);yield(n.cert);walk(n.right)}
            }
            walk(tree.value)
        }
        is Composite -> stores.cells().flatMap {it.value.certificateCells()}
    }
    fun certificates()=certificateCells().map {it.value}
    companion object {
        fun deferred(store: ()->X509CertificateStore): X509CertificateStore = Deferred(lazy(store))
        fun empty(): X509CertificateStore = Single(lazyOf(null))
        fun make(certificates: List<X509SignedCertificate>): X509CertificateStore {
            val owned=certificates.toList()
            return makeDeferred {owned.asSequence().map(::lazyOf)}
        }
        fun makeDeferred(certificates: ()->Sequence<Lazy<X509SignedCertificate>>): X509CertificateStore = Single(lazy {
            var tree: Node?=null
            for(cert in certificates()) tree=insert(tree,lazy {X509.getCertificate(cert.value).subjectDN},cert)
            tree
        })
        fun compositeDeferred(stores: ()->Sequence<Lazy<X509CertificateStore>>): X509CertificateStore = Composite(X509Values(stores))
        fun concatenate(stores: List<X509CertificateStore>): X509CertificateStore = stores.foldRight(empty()) {s,acc->s+acc}
        private fun insert(n: Node?,key: Lazy<X509DistinguishedName>,cert: Lazy<X509SignedCertificate>): Node {
            key.value.elements
            if(n==null) return Node(key,cert,null,null)
            val cmp=key.value.compareTo(n.key.value)
            return when {cmp<0->balance(Node(n.key,n.cert,insert(n.left,key,cert),n.right));cmp>0->balance(Node(n.key,n.cert,n.left,insert(n.right,key,cert)));else->Node(key,cert,n.left,n.right)}
        }
        // Data.Map's weight-balanced insertion uses delta=3 and ratio=2.
        private fun balance(n: Node): Node {
            val l=n.left;val r=n.right;val ls=l?.size ?: 0;val rs=r?.size ?: 0
            if(ls+rs<=1) return n
            if(ls>3*rs) {
                l!!
                if((l.right?.size ?: 0)<2*(l.left?.size ?: 0)) return Node(l.key,l.cert,l.left,Node(n.key,n.cert,l.right,r))
                val middle=l.right!!
                return Node(middle.key,middle.cert,Node(l.key,l.cert,l.left,middle.left),Node(n.key,n.cert,middle.right,r))
            }
            if(rs>3*ls) {
                r!!
                if((r.left?.size ?: 0)<2*(r.right?.size ?: 0)) return Node(r.key,r.cert,Node(n.key,n.cert,l,r.left),r.right)
                val middle=r.left!!
                return Node(middle.key,middle.cert,Node(n.key,n.cert,l,middle.left),Node(r.key,r.cert,middle.right,r.right))
            }
            return n
        }
        fun readCertificates(path: Path): Sequence<X509SignedCertificate> = try {
            val bytes=Files.readAllBytes(path)
            sequence {yieldAll(X509Memory.readSignedCertificates(bytes))}
        } catch(_: IOException) {emptySequence()}
        internal fun doesFileExist(path: Path): Boolean = try {
            // directory's doesFileExist includes devices/FIFOs, not only regular files.
            !Files.readAttributes(path,BasicFileAttributes::class.java).isDirectory
        } catch(_: IOException) {false}
        fun isCertificateFileName(name: String): Boolean {
            val points=name.codePoints().toArray()
            return !name.startsWith(".") && !(points.size==10 && points[8]==46 && points[9] in 48..57 && points.take(8).all {it in 48..57 || it in 65..70 || it in 97..102})
        }
        fun read(path: Path): X509CertificateStore? = readDeferred(path).value
        /** Perform filesystem effects now, leaving wrapStore and certificate decoding lazy. */
        fun readDeferred(path: Path): Lazy<X509CertificateStore?> {
            val dir=Files.isDirectory(path);val file=doesFileExist(path)
            val certs=when {
                dir -> {
                    val entries=try {Files.newDirectoryStream(path).use {it.toList()}} catch(_: IOException) {emptyList()}
                    // Directory order is retained; no sort or extension filter is added.
                    val paths=entries.filter {isCertificateFileName(it.fileName.toString())}.filter(::doesFileExist)
                    val blocks=paths.map(::readCertificates)
                    blocks.asSequence().flatten()
                }
                file -> readCertificates(path)
                else -> emptySequence()
            }
            val saved=X509Values {certs.map(::lazyOf)}
            return lazy {if(!saved.cells().iterator().hasNext()) null else makeDeferred {saved.cells()}}
        }
    }
}
