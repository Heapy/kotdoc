#!/usr/bin/env python3
"""Verify verbatim RSA sources and exact mechanical development observers."""
from pathlib import Path
import json,hashlib,re
R=Path(__file__).resolve().parents[1];D=R/'tools/rsa-core-reference';V=R/'vendor/crypton-2.0.1'
m=json.loads((V/'SOURCE.json').read_text())
for name in ['Crypto/PubKey/RSA.hs','Crypto/PubKey/RSA/Prim.hs','Crypto/PubKey/RSA/Types.hs','tests/PubKey/RSASpec.hs','tests/Imports.hs','tests/Utils.hs']:
 b=(V/name).read_bytes();assert hashlib.sha256(b).hexdigest()==m['files'][name],name
 assert(D/'original'/name).read_bytes()==b,name

def module(s,old,new):
 start=s.index('module '+old+' (');end=s.index(') where',start)+len(') where');return s[:start]+'module '+new+' where'+s[end:]
s=(V/'Crypto/PubKey/RSA.hs').read_text()
o=module(s,'Crypto.PubKey.RSA','ObservedRsa').replace('import Crypto.Number.Prime','import ObservedPrime')
assert(D/'ObservedRsa.hs').read_text()==o
p=(V/'Crypto/PubKey/RSA/Prim.hs').read_text();assert(D/'ObservedRsaPrim.hs').read_text()==module(p,'Crypto.PubKey.RSA.Prim','ObservedRsaPrim')
v=(V/'tests/PubKey/RSASpec.hs').read_text();assert(D/'RsaVectors.hs').read_text()==v.replace('module PubKey.RSASpec (spec) where','module RsaVectors where')
b=(D/'BoundedRsa.hs').read_text()
# Reverse only bounded loop instrumentation; the source arithmetic is identical.
b=b.replace('module BoundedRsa where','module ObservedRsa where').replace('generateBounded\n','generate\n').replace('    => Int\n    -> Int','    => Int',1).replace('generateBounded limit size e = loop 0','generate size e = loop').replace('    loop step\n        | step == limit = error ("bounded RSA loop " ++ show step)\n        | otherwise = do','    loop = do').replace('Nothing -> loop (step + 1)','Nothing -> loop')
assert b==o,'bounded observer differs beyond declared loop instrumentation'
matrix=json.loads((D/'SourceMatrix.json').read_text())
assert set(re.findall(r'^([a-zA-Z][a-zA-Z0-9]*)\n?\s*::',re.sub(r'\{-[\s\S]*?-\}', '', s),re.M))==set(matrix['exports'][:3])
assert set(re.findall(r'^([a-zA-Z][a-zA-Z0-9]*)\n?\s*::',p,re.M))==set(matrix['exports'][3:]+matrix['privateHelpers'])
types=(V/'Crypto/PubKey/RSA/Types.hs').read_text()
assert [line for line in types.splitlines()if '!'in line]==['data Blinder = Blinder !Integer !Integer']
print('PASS all 9 source functions, 6 verbatim originals and mechanical observers')
