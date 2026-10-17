#!/usr/bin/env python3
"""Bind development oracles to verbatim pinned HMAC/test/hash sources."""
from pathlib import Path
import argparse
R=Path(__file__).resolve().parent.parent;D=R/'tools/hmac-reference';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
files=['Crypto/MAC/HMAC.hs','tests/MAC/HMACSpec.hs','Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/IO.hs','Crypto/Internal/ByteArray.hs']
for name in files:
 source=R/'vendor/crypton-2.0.1'/name;target=D/'original'/name
 if a.check:assert target.read_bytes()==source.read_bytes(),name
 else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes())
for name in ['Data/ByteArray/Methods.hs','Data/Memory/PtrMethods.hs','Data/ByteArray/Types.hs']:
 source=R/'vendor/ram-0.22.1'/name;target=D/'original/ram'/name
 if a.check:assert target.read_bytes()==source.read_bytes()
 else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes())
expected=(R/'vendor/crypton-2.0.1/tests/MAC/HMACSpec.hs').read_text().replace('module MAC.HMACSpec (spec) where','module OriginalHmacSpec where')
assert(D/'OriginalHmacSpec.hs').read_text()==expected,'original test body modified'
hmac=(R/'vendor/crypton-2.0.1/Crypto/MAC/HMAC.hs').read_text()
for token in ['memXorWith p 0x36','memXorWith p 0x5c','hashMutableReset ctx','Context !(Hash.Context hashalg) !(Hash.Context hashalg)','B.constEq b1 b2']:assert token in hmac,token
assert 'instance Show (HMAC'not in hmac
print('PASS verbatim HMAC/hash/RAM source copies and full original HMACSpec body')
