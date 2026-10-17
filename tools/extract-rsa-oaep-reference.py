#!/usr/bin/env python3
from pathlib import Path
import argparse
R=Path(__file__).resolve().parent.parent;D=R/'tools/rsa-oaep-reference';p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
for name in ['Crypto/PubKey/RSA/OAEP.hs','Crypto/PubKey/MaskGenFunction.hs','Crypto/PubKey/Internal.hs','Crypto/PubKey/RSA/Types.hs','tests/PubKey/OAEPSpec.hs']:
 source=R/'vendor/crypton-2.0.1'/name;target=D/'original'/name
 if a.check:assert target.read_bytes()==source.read_bytes(),name
 else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes())
expected=(R/'vendor/crypton-2.0.1/tests/PubKey/OAEPSpec.hs').read_text().replace('module PubKey.OAEPSpec (spec) where','module OriginalOaepSpec where')
assert(D/'OriginalOaepSpec.hs').read_text()==expected,'original test body changed'
o=(D/'original/Crypto/PubKey/RSA/OAEP.hs').read_text();m=(D/'original/Crypto/PubKey/MaskGenFunction.hs').read_text()
for name in ['defaultOAEPParams','encryptWithSeed','encrypt','unpad','decrypt','decryptSafer']:assert '\n'+name+'\n' in o or '\n'+name+'\n 'in o or '\n'+name+'\n    'in o or '\n'+name+' ::'in o,name
assert 'let !seededCtx = hashUpdate (hashInitWith hashAlg) seed'in m
assert 'i2ospOf_ 4 counter'in m
assert 'os2ip cipher >= private_n pk'in o
print('PASS verbatim two modules/internal/types/original23-example suite body')
