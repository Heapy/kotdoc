# New-only RSA.Types facade design (early review pending)

Ownership: new CryptoRsaTypes.kt, CryptoRsaTypesData.kt, CryptoRsaTypesGeneric.kt,
CryptoRsaTypesRead.kt, strictly scoped tests/resources/importer/oracles. These are
prospective files; no production installation until complete isolated compile.
No changes to accepted CryptoRsa/X509/Generic/Data/DeepSeq APIs are authorized.

Facade PublicKey and PrivateKey retain independently memoized fields and ordinary
data roots. KeyPair represents a source newtype through an explicit deferred
projection/root protocol (not a Kotlin eager data class). New fields use source
Int64 and BigInteger. Defined accepted-record conversion is explicit, only when
consumers demand it. Blinder and Error reuse accepted identities; source Show/Eq
and strict-root semantics tested without adding undeclared instances.

Data dictionaries use existing DataDictionary/AlgebraicDataDictionary with three
metadata records PublicKey,PrivateKey,KeyPair, source module Crypto.PubKey.RSA.Types,
crypton package identity. All one-constructor gunfold methods need independent
original probes: do not invent rejection for foreign constructor when GHC ignores
its argument. Primitive Int/Integer and recursive PublicKey dictionaries must use
existing canonical representations where exact. Default Data methods inherited
only after original compiler dispatch/demand validation.

Generic dictionaries use existing GenericRepDictionary/Value/Metadata but NEVER
GenericRepData.definition: that schema encodes pandoc StrictData. RSA fields are
NoSourceStrictness/DecidedLazy, no automatic WHNF of fields. PublicKey and
PrivateKey only; KeyPair does not derive Generic. Product tree shape and complete
product pattern demand must be observed from the selected original GHC profile.

Read is a new scoped dictionary: existing PandocRead schemas are closed and
AST-specific, so cannot silently extend them or claim their root constructors
are RSA. Reuse only suitable open lexer/numeric infrastructure after source
comparison. Record read order and precedence must come from original GHC,
including suffix/list/parenthesis/lexical errors and numeric bounds.

NFData dictionaries use existing NFData protocol with exact custom source
sequence, independently lazy field projections. Public n,e,size; Private
pub,d,p,q,dP,dQ,qinv; KeyPair newtype delegates Private. No generic traversal
substitution changes force order.

Independent original observer will cover all declared methods plus default
Data families, Read parser suffixes and root-only/ignored-field/error-sharing
probes. Existing RSASpec37 source proof remains supporting consumer evidence,
not new full Types instance proof. All original source/license bytes retained.
