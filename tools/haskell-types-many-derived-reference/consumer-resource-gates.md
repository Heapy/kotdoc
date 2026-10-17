# Separate open consumer resource gate

The generic Many Eq/Ord/Show/Read checkpoint does not close resource behavior of
legacy finite Builder AST bridges. Org reader independently exposed repeated
GenericAstData encode/decode and Many wrapping in the accepted finite
Builder.orderedList bridge at nested headline depths 256 and 1024, with a 512 MiB
heap and 256 KiB stack. The original fourteen Org deep fixtures are unchanged.

Author evidence: `/tmp/kotdoc-org-reader/deep-proof1.log`, reported by the Org
reader owner. Its local finite parser constructor adapter is justified by pinned
Builder.hs lines 473–478; it does not establish arbitrary bottom/demand parity.

Status: OPEN for task611/612 consumer resource audit. This checkpoint makes no
Builder/GenericAstData production change and does not represent this gate as
closed by source-protocol constructor equivalence. Integrator must retain the
Org fixture/proof artifacts durably with the Org checkpoint; the temporary log
is an investigation location, not the sole acceptance evidence.
