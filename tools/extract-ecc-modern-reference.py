#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def expected():
 out={name:(ROOT/path).read_text().replace(old,new) for name,path,old,new in [
 ('OriginalModernECCSpec.hs','vendor/crypton-2.0.1/tests/ECCSpec.hs','module ECCSpec (spec) where','module OriginalModernECCSpec where'),
 ('OriginalLegacyECCSpec.hs','vendor/crypton-2.0.1/tests/PubKey/ECCSpec.hs','module PubKey.ECCSpec (spec) where','module OriginalLegacyECCSpec where')]}
 mappings={'Crypto.Internal.Compat':'OriginalCompat','Crypto.Number.Compat':'OriginalNumberCompat','Crypto.Number.Serialize.Internal':'OriginalSerialize','Crypto.Internal.ECC':'OriginalInternalECC','Crypto.Internal.Imports':'OriginalImports','Crypto.Internal.DeepSeq':'OriginalDeepSeq','Crypto.ECC.Simple.Types':'OriginalSimpleTypes','Crypto.ECC.Simple.Prim':'OriginalSimplePrim','Crypto.ECC':'OriginalPrimeECC'}
 import re
 for module,name in mappings.items():
  text=(ROOT/'vendor/crypton-2.0.1'/Path(module.replace('.','/')+'.hs')).read_text()
  if module in ['Crypto.Internal.Imports','Crypto.Internal.DeepSeq']:text=text.replace('module '+module+' (','module '+name+' (',1)
  else:text=re.sub(r'(?m)^module '+re.escape(module)+r' \(.*?\) where','module '+name+' where',text,count=1,flags=re.S)
  if module=='Crypto.ECC':
   start=text.index('data Curve_X25519 =');stop=text.index('encodeECShared\n',start);text=text[:start]+text[stop:]
   for line in ['import qualified Crypto.ECC.Edwards25519 as Edwards25519','import qualified Crypto.PubKey.Curve25519 as X25519','import qualified Crypto.PubKey.Curve448 as X448','import qualified Crypto.Number.Serialize.LE as LE']:text=text.replace(line+'\n','')
   text=text.replace('Crypto.Internal.ByteArray','Data.ByteArray')
  for old,new in mappings.items():text=re.sub(r'(?m)^(import (?:qualified )?)'+re.escape(old)+r'(?= |\n)',r'\g<1>'+new,text)
  out[name+'.hs']=text
 return out
if __name__=='__main__':
 for name,text in expected().items():
  p=ROOT/'tools/haskell-ecc-modern-reference'/name
  if '--check' in sys.argv:assert p.read_text()==text,name
  else:p.write_text(text)
 print('Verified complete modern and subgroup source-suite extraction')
