#!/usr/bin/env python3
"""Preserve the complete binary-field C source, adding read-only observation hooks."""
import re,textwrap
from pathlib import Path
root=Path(__file__).resolve().parents[1]

def source():
    s=(root/'vendor/crypton-2.0.1/cbits/crypton_f2m.c').read_text()
    s=s.replace('crypton_f2m_mul','kotdoc_source_f2m_mul')
    for i,name in enumerate(['fe_mul','fe_sqr','fe_add','fe_is_zero','fe_inv','cswap','poly_reduce','poly_mul','poly_sqr']):
        s,n=re.subn(r'(static (?:void|int) '+name+r'\([^{}]*?\)\n\{)',lambda m:m[0]+'\n    audit_ops['+str(i)+']++;',s,count=1)
        assert n==1,name
    before='limb_t t = (a[i] ^ b[i]) & mask;'
    assert s.count(before)==1
    s=s.replace(before,'audit_swap(a,b,i);\n\t\t'+before)
    before='for (i = klen * 8; i > 0; i--) {'
    assert s.count(before)==1
    s=s.replace(before,before+'\n        audit_ops[9]++;')
    before='poly = space;'
    assert s.count(before)==1
    s=s.replace(before,'audit_root = space;\n\t'+before)
    before='if (fe_is_zero(&f, z1)) {'
    assert s.count(before)==1
    s=s.replace(before,'memcpy(audit_ladder_ops,audit_ops,sizeof(audit_ops));\n\t'+before)
    return s

def tests():
    s=(root/'vendor/crypton-2.0.1/tests/PubKey/ECCSpec.hs').read_text()
    section=s[s.index('binaryTests ='):s.index('-- | Points that satisfy')]
    names=re.search(r'names = (\[ECC.SEC_t113r1.*\])',section)[1]
    scalars=textwrap.dedent(section[section.index('            [ 1'):].rstrip())
    return s.replace('module PubKey.ECCSpec (spec) where','module OriginalBinaryTests where')+'\noriginalBinaryNames = '+names+'\noriginalBinaryScalars order =\n    '+scalars.replace('\n','\n    ')+'\n'

if __name__=='__main__':
    (root/'tools/haskell-ecc-binary-reference/OriginalBinaryEcc.c').write_text(source())
    (root/'tools/haskell-ecc-binary-reference/OriginalBinaryTests.hs').write_text(tests())
