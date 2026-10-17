#!/usr/bin/env python3
import hashlib,json,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1];D=R/'tools/number-generate-reference';V=R/'vendor/crypton-2.0.1'
def sources():
 m=json.loads((V/'SOURCE.json').read_text());assert m['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 for p,h in m['files'].items():assert hashlib.sha256((V/p).read_bytes()).hexdigest()==h,p
 paths=['Crypto/Number/Generate.hs','Crypto/Number/Basic.hs','Crypto/Number/Serialize.hs','Crypto/Random/Types.hs','Crypto/Random.hs','Crypto/Random/ChaChaDRG.hs','tests/NumberSpec.hs','tests/Imports.hs','tests/Utils.hs'];out={D/'original'/p:(V/p).read_bytes()for p in paths}
 source=(V/'Crypto/Number/Generate.hs').read_text();needle='B.copyAndFreeze orig $ \\p0 -> do';assert source.count(needle)==1
 observed=source.replace('module Crypto.Number.Generate (','module ObservedNumberGenerate (\n    observedFrames,',1).replace(needle,'observedCopy orig $ \\p0 -> do',1)
 observed=observed.replace('Crypto.Internal.ByteArray','Data.ByteArray').replace('import Crypto.Internal.Imports','import Data.Word (Word8)')
 observed=observed.replace('import Foreign.Storable','import Foreign.Storable\nimport Foreign.Marshal.Alloc\nimport Foreign.Marshal.Array\nimport Data.IORef\nimport System.IO.Unsafe (unsafePerformIO)',1)
 observed+='\nobservedFrames :: IORef ([Word8],[Word8])\nobservedFrames = unsafePerformIO (newIORef ([],[]))\n{-# NOINLINE observedFrames #-}\nobservedCopy :: ScrubbedBytes -> (Ptr Word8 -> IO ()) -> ScrubbedBytes\nobservedCopy orig action = unsafePerformIO $ do\n    let input = B.unpack orig :: [Word8]\n        n = length input\n        before = [17] ++ input ++ [34]\n    allocaBytes (n+2) $ \\p -> do\n        pokeArray p before\n        action (p `plusPtr` 1)\n        after <- peekArray (n+2) p\n        writeIORef observedFrames (before,after)\n        result <- peekArray n (p `plusPtr` 1)\n        return (B.pack result)\n{-# NOINLINE observedCopy #-}\n'
 out[D/'ObservedNumberGenerate.hs']=observed.encode()
 h=(V/'tests/NumberSpec.hs').read_text().replace('module NumberSpec (spec) where','module OriginalNumberGenerateSpec where');start=h.index('spec :: Spec');a=h.index('    prop "generate-param"',start);b=h.index('    prop "generate-prime"',a);out[D/'OriginalNumberGenerateSpec.hs']=(h[:start]+'spec :: Spec\nspec = do\n'+h[a:b]).encode()
 matrix=dict(source='Crypto.Number.Generate',types=dict(GenTopPolicy=['SetHighest','SetTwoHighest']),functions=['generateParams','generatePrefix','generateMax','generateBetween'],privateContracts=['copyAndFreeze input ownership','bit/top/mask/odd order','simple range<127 biased modulo','normal rejection sampling','overgeneration r/r-range/r-2range','exact100 attempt error strings','inclusive unsorted generateBetween'],originalPropertyFamilies=['generate-param','generate-param2','generate-param-odd','generate-range'],remaining=dict(number=['Prime9exports','Random.Probabilistic/DRG dependencies'],signatures=['RSA/Prim/PKCS15/PSS/OAEP','DSA','ECDSA/legacyECC.ECDSA','Ed25519/EdDSA','Rabin','ElGamal'],trust=['X509.Validation','Validation.Signature','Validation.Types','Validation.Cache','trust/chain operations'],fullGoal=['remaininglocal613','Pandoc623 allformats/tests','calibre624 MOBI6/KF8/oldKindle','calibre625 otherformats/tests','626finaldelivery']))
 out[D/'SourceMatrix.json']=(json.dumps(matrix,indent=2)+'\n').encode();return out
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('PASS full Number.Generate source/4 original property families')
