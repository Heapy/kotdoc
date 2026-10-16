// Data.ASN1.OID, crypton-asn1-types 0.4.1; Vincent Hanquez 2010-2013.
// Complete notice: licenses/crypton-asn1-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import java.math.BigInteger

typealias Asn1Oid = List<BigInteger>

interface Asn1Oidable {
    fun getObjectId(): Asn1Oid
}

fun interface Asn1OidNameable<A> {
    fun fromObjectId(oid: Asn1Oid): A?
}
