#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,sys
R=Path(__file__).resolve().parents[1];D=R/'tools/ed448-public-reference'
def sources():
 result={}
 for package,expected in [('crypton-2.0.1','9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'),('ram-0.22.1','72474962349dd101b970cc46bcc06c799f7311d228e7b59f1a44f81427159f70')]:
  V=R/'vendor'/package;m=json.loads((V/'SOURCE.json').read_text());assert m['sha256']==expected
  for p,h in m['files'].items():assert hashlib.sha256((V/p).read_bytes()).hexdigest()==h,p
  selected=['Crypto/PubKey/Ed448.hs','Crypto/Debug.hs','Crypto/Error.hs','Crypto/Internal/ByteArray.hs','tests/Ed448Spec.hs','tests/Imports.hs','tests/Utils.hs']if package.startswith('crypton')else['Data/ByteArray/Bytes.hs','Data/ByteArray/ScrubbedBytes.hs','Data/ByteArray/Types.hs']
  for path in selected:result[D/'original'/package/path]=(V/path).read_bytes()
 source=(R/'vendor/crypton-2.0.1/tests/Ed448Spec.hs').read_text();result[D/'OriginalEd448Spec.hs']=source.replace('module Ed448Spec (spec) where','module OriginalEd448Spec where').encode()
 result[D/'SourceMatrix.json']=(json.dumps(dict(types=['SecretKey(ScrubbedBytes)','PublicKey(Bytes)','Signature(Bytes)'],sizes=dict(publicKeySize=57,secretKeySize=57,signatureSize=114),functions=['signature','publicKey','secretKey','toPublic','sign','unsafeSign','verify','generateSecretKey'],instances=dict(SecretKey=['Show','Eq','ByteArrayAccess','NFData','DebugShow'],PublicKey=['Show','Eq','ByteArrayAccess','NFData'],Signature=['Show','Eq','ByteArrayAccess','NFData']),lengthErrors=dict(secretKey='CryptoError_SecretKeyStructureInvalid',publicKey='CryptoError_PublicKeySizeInvalid',signature='CryptoError_SecretKeyStructureInvalid'),sourceQuirks=['secretKey pointer validation unconditional True','sign ignores supplied public, including bottom','unsafeSign uses supplied public','RNG one57byte request, no clamp','pure signatures only; context/prehash not exposed','no Crypto.ECC Curve_Ed448 instance'],originalExamples=25,literalVectors=8,remaining='all other local613 and fullPandoc/calibre'),indent=2)+'\n').encode()
 return result
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('PASS full publicEd4483types/3sizes/8exports and immutable original expectations')
