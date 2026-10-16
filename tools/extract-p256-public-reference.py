#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def extract():
 s=(ROOT/'vendor/crypton-2.0.1/tests/PubKey/P256Spec.hs').read_text();marker='module PubKey.P256Spec (spec) where';assert s.count(marker)==1
 return s.replace(marker,'module OriginalP256Spec where')
if __name__=='__main__':
 p=ROOT/'tools/haskell-p256-public-reference/OriginalP256Spec.hs'
 if '--check' in sys.argv:assert p.read_text()==extract();print('Verified complete original P256Spec extraction')
 else:p.write_text(extract())
