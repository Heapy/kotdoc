#!/usr/bin/env python3
"""Extract every active F2mSpec property, without rewriting its expression."""
import re, textwrap
from pathlib import Path

root = Path(__file__).resolve().parents[1]

def properties():
    source = (root / 'vendor/crypton-2.0.1/tests/Number/F2mSpec.hs').read_text()
    source = '\n'.join(line.split('--', 1)[0] for line in source.splitlines())
    result = []
    for section in re.finditer(r'(\w+)Tests =\s*describe "([^"]+)" \$ do\n(.*?)(?=\n\w+Tests =|\nspec ::)', source, re.S):
        for match in re.finditer(r'        prop "([^"]+)" \$\s*(.*?)(?=\n        prop |\Z)', section[3], re.S):
            body = textwrap.dedent('            ' + match[2].rstrip())
            body = '\n'.join(line.rstrip() for line in body.splitlines())
            args = []
            if body.startswith('\\'):
                parameters = body[1:body.index('->')].strip()
                args = re.findall(r'\((?:Positive|NonNegative) \w+\)|\b[a-z]\b', parameters)
            result.append((section[2] + '/' + match[1], body, args))
    assert len(result) == 29, len(result)
    return result

def module():
    lines = ['-- Mechanically extracted active expressions from the pinned F2mSpec.',
             'module OriginalF2mProperties (propertyAt) where',
             'import Crypto.Number.F2m', 'import Crypto.Number.Basic (log2)',
             'import Data.Bits', 'import Data.Maybe', 'import Test.QuickCheck (Positive(..),NonNegative(..))',
             'propertyAt :: Int -> Integer -> Integer -> Integer -> Integer -> Bool',
             'propertyAt index m a b c = case index of']
    for i, (_, body, args) in enumerate(properties()):
        expression = '(' + body + ')' + ''.join(' (' + a.strip('()') + ')' for a in args)
        lines.append('  ' + str(i) + ' ->\n    ' + expression.replace('\n', '\n     '))
    lines.append('  _ -> error "unknown property"')
    return '\n'.join(lines) + '\n'

if __name__ == '__main__':
    (root / 'tools/haskell-f2m-reference/OriginalF2mProperties.hs').write_text(module())
