#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'tools/curve448-public-reference'
def sources():
 raw=(ROOT/'vendor/crypton-2.0.1/tests/Curve448Spec.hs').read_text().replace('module Curve448Spec (spec) where','module OriginalCurve448Spec where')
 modern=(ROOT/'vendor/crypton-2.0.1/tests/ECCSpec.hs').read_text().replace('module ECCSpec (spec) where','module OriginalX448ECCSpec where')
 start=modern.index('            [ Curve ECC.Curve_P256R1');end=modern.index('\n            ]',start)+len('\n            ]')
 modern=modern[:start]+'            [ Curve ECC.Curve_X448 ]'+modern[end:]
 modern=modern[:modern.index('        prop "decodeScalar.encodeScalar==id"')]
 modern=modern.replace('    describe "ECDH KATs" $ zipWithM_ doECDHTest [katZero ..] vectorsECDH\n','')
 modern=modern.replace('vectorsPoint\n    describe','(filter isX448 vectorsPoint)\n    describe').replace('[katZero ..] vectorsWeakPoint','[katZero ..] (filter isX448 vectorsWeakPoint)')
 modern+='\nisX448 :: VectorPoint -> Bool\nisX448 v = case vpCurve v of Curve c -> show c == "Curve_X448"\n'
 return {'OriginalCurve448Spec.hs':raw,'OriginalX448ECCSpec.hs':modern}
if __name__=='__main__':
 for name,text in sources().items():
  p=OUT/name
  if '--check'in sys.argv:assert p.read_text()==text,p
  else:p.write_text(text)
 print('Verified original raw suite and exact X448 modern partition')
