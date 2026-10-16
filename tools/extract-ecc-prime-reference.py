#!/usr/bin/env python3
"""Keep the complete pinned prime ECC source, adding only observer boundaries."""
import re,textwrap
from pathlib import Path
root=Path(__file__).resolve().parents[1]

def ecc():
    s=(root/'vendor/crypton-2.0.1/cbits/crypton_ecc.c').read_text()
    s=s.replace('crypton_ecc_', 'kotdoc_source_ecc_')
    for i,name in enumerate(['fe_mul','fe_sqr','fe_add','fe_sub','fe_neg','fe_mul_a','point_add','point_double','table_select']):
        s,n=re.subn(r'(static void '+name+r'\([^{}]*?\)\n\{)',lambda m:m[0]+'\n    audit_ops['+str(i)+']++;',s,count=1)
        assert n==1,name
    old='for (l = 0; l < 3 * n; l++)\n\t\t\tr[l] |= table[3 * j * n + l] & mask;'
    assert s.count(old)==1
    s=s.replace(old,'for (l = 0; l < 3 * n; l++) {\n            audit_read(table,3*j*n+l);\n            r[l] |= table[3*j*n+l] & mask;\n        }')
    return s

def tests():
    s=(root/'vendor/crypton-2.0.1/tests/PubKey/ECCSpec.hs').read_text()
    section=s[s.index('weightTests ='):s.index('-- | The curves over a binary field')]
    pairs=section[section.index('        pairs =\n')+len('        pairs =\n'):].rstrip()
    pairs=textwrap.dedent(pairs)
    scalars=re.search(r'k <- (\[1, 2, 3, 15, 16, 17, n - 1, n, n \+ 1\])',section)[1]
    extra='\noriginalWeightScalars n = '+scalars+'\noriginalWeightPairs n =\n    '+pairs.replace('\n','\n    ')+'\n  where\n    bits = numBits n\n'
    for name in ['ones','alternating']:
        line=next(l.strip() for l in section.splitlines() if l.strip().startswith(name+' k ='))
        extra+='    '+line+'\n'
    return s.replace('module PubKey.ECCSpec (spec) where','module OriginalEccTests where')+extra

if __name__=='__main__':
    folder=root/'tools/haskell-ecc-prime-reference'
    (folder/'OriginalPrimeEcc.c').write_text(ecc())
    (folder/'OriginalEccTests.hs').write_text(tests())
