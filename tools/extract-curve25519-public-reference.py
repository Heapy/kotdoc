#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'tools/curve25519-public-reference'
def sources():
 raw=(ROOT/'vendor/crypton-2.0.1/tests/Curve25519Spec.hs').read_text().replace('module Curve25519Spec (spec) where','module OriginalCurve25519Spec where')
 modern=(ROOT/'vendor/crypton-2.0.1/tests/ECCSpec.hs').read_text().replace('module ECCSpec (spec) where','module OriginalX25519ECCSpec where')
 start=modern.index('            [ Curve ECC.Curve_P256R1');end=modern.index('\n            ]',start)+len('\n            ]')
 modern=modern[:start]+'            [ Curve ECC.Curve_X25519 ]'+modern[end:]
 modern=modern[:modern.index('        prop "decodeScalar.encodeScalar==id"')]
 modern=modern.replace('    describe "ECDH KATs" $ zipWithM_ doECDHTest [katZero ..] vectorsECDH\n','')
 modern=modern.replace('vectorsPoint\n    describe','(filter isX25519 vectorsPoint)\n    describe').replace('[katZero ..] vectorsWeakPoint','[katZero ..] (filter isX25519 vectorsWeakPoint)')
 modern+='\nisX25519 :: VectorPoint -> Bool\nisX25519 v = case vpCurve v of Curve c -> show c == "Curve_X25519"\n'
 return {'OriginalCurve25519Spec.hs':raw,'OriginalX25519ECCSpec.hs':modern}
if __name__=='__main__':
 for name,text in sources().items():
  p=OUT/name
  if '--check'in sys.argv:assert p.read_text()==text,p
  else:p.write_text(text)
 print('Verified original raw suite and exact X25519 modern partition')
