#!/usr/bin/env python3
"""Add read-only counters at explicit boundaries of the pinned C reference."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]

def powm():
    s=(root/'vendor/crypton-2.0.1/cbits/crypton_powm.c').read_text()
    s=s.replace('int crypton_powm_sec(', 'int kotdoc_original_powm(')
    s=s.replace('mont_mul(', 'audit_mul(').replace('mont_sqr(', 'audit_sqr(')
    s=s.replace('for (i = explen * 2; i > 0; i--) {', 'for (i = explen * 2; i > 0; i--) {\n        audit_windows++;')
    s=s.replace('for (l = 0; l < n; l++)\n\t\t\t\tsel[l] |= table[k * n + l] & mask;',
                'for (l = 0; l < n; l++) {\n                audit_read(k * n + l);\n                sel[l] |= table[k * n + l] & mask;\n            }')
    assert s.count('audit_read(')==1
    s=s.replace('free(space);','audit_clear(space, words);\n\tfree(space);')
    assert s.count('audit_clear(')==2
    return s

def number_tests():
    s=(root/'vendor/crypton-2.0.1/tests/NumberSpec.hs').read_text()
    s=s.replace('module NumberSpec (spec) where','module OriginalNumberTests where')
    start=s.index('[ (0, 1)',s.index('inverseTests ='))
    end=s.index(']',start)+1
    return s+'\noriginalInverseCorners :: [(Integer,Integer)]\noriginalInverseCorners = '+s[start:end]+'\n'

if __name__=='__main__':
    (root/'tools/haskell-modular-reference/OriginalPowm.c').write_text(powm())
    (root/'tools/haskell-modular-reference/OriginalNumberTests.hs').write_text(number_tests())
