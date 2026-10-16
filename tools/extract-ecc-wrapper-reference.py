#!/usr/bin/env python3
"""Only module/import/FFI boundaries differ from the complete pinned source."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
def sources():
 d=root/'vendor/crypton-2.0.1'
 out={}
 for original,name in [('Crypto/Number/Serialize/Internal.hs','ObservedSerialize'),('Crypto/Number/Compat.hs','ObservedNumberCompat'),('Crypto/Internal/Compat.hs','ObservedCompat'),('Crypto/Internal/ECC.hs','ObservedECC')]:
  s=(d/original).read_text();start=s.index('\nmodule ')+1;stop=s.index(') where',start)+len(') where')
  s=s[:start]+'module '+name+' where'+s[stop:]
  s=s.replace('import Crypto.Number.Compat','import ObservedNumberCompat')
  s=s.replace('import Crypto.Internal.Compat','import ObservedCompat')
  s=s.replace('import qualified Crypto.Number.Serialize.Internal as Internal','import qualified ObservedSerialize as Internal')
  if name=='ObservedECC':
   a='import Foreign.ForeignPtr (ForeignPtr, mallocForeignPtrBytes, withForeignPtr)';assert s.count(a)==1
   s=s.replace(a,'import Foreign.ForeignPtr (ForeignPtr, withForeignPtr)')
   a='import Foreign.Marshal.Alloc (allocaBytes)';assert s.count(a)==1
   s=s.replace(a,'import ObserverMemory (allocaBytes, mallocForeignPtrBytes)')
   for symbol in ['crypton_ecc_table_size','crypton_ecc_table_build','crypton_ecc_table_mul','crypton_ecc_mul','crypton_f2m_mul']:
    assert s.count('"'+symbol+'"')==1
    s=s.replace('"'+symbol+'"','"kotdoc_wrapped_'+symbol+'"')
  out[name+'.hs']=s
 return out
if __name__=='__main__':
 for name,s in sources().items():(root/'tools/haskell-ecc-wrapper-reference'/name).write_text(s)
