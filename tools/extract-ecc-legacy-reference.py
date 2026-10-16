#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def extract():
 s=(ROOT/'vendor/crypton-2.0.1/tests/PubKey/ECCSpec.hs').read_text();marker='module PubKey.ECCSpec (spec) where';assert s.count(marker)==1
 return s.replace(marker,'module OriginalECCSpec where')
if __name__=='__main__':
 p=ROOT/'tools/haskell-ecc-legacy-reference/OriginalECCSpec.hs'
 if '--check' in sys.argv:assert p.read_text()==extract();print('Verified complete original ECCSpec extraction')
 else:p.write_text(extract())
