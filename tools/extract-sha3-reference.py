#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parents[1];V=R/'vendor/crypton-2.0.1';D=R/'tools/sha3-reference';files=['cbits/crypton_sha3.c','cbits/crypton_sha3.h','cbits/crypton_bitfn.h','cbits/crypton_align.h','cbits/sha3_armv8.c','cbits/asm/keccak1600-armv8-ios64.S','cbits/asm/keccak1600-x86_64-macosx.S','cbits/decaf/include/decaf/shake.h','cbits/decaf/include/decaf/common.h','cbits/decaf/utils.c','Crypto/Hash/SHAKE.hs','Crypto/Hash/SHA3.hs','Crypto/Hash/Keccak.hs','tests/HashSpec.hs']
def sources():
 m=json.loads((V/"SOURCE.json").read_text());assert m["sha256"]=="9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914"
 for path,expected in m["files"].items():assert hashlib.sha256((V/path).read_bytes()).hexdigest()==expected,path
 result={str(Path('original')/p): (V/p).read_bytes()for p in files}
 c=(V/'cbits/crypton_sha3.c').read_text();assert c.count('state[0] ^= keccak_rndc[r];')==1
 c=c.replace('/* run keccak rounds */','sha3_snapshot("absorb",bufsz,state);\n\t/* run keccak rounds */').replace('state[0] ^= keccak_rndc[r];','state[0] ^= keccak_rndc[r];\n\t\tsha3_snapshot("round",r,state);')
 result['ObservedSha3.c']=c.encode()
 matrix=[]
 for path,count in [('cbits/crypton_sha3.c',11),('cbits/decaf/include/decaf/shake.h',6)]:
  text=(V/path).read_text();matches=list(re.finditer(r'^(?:static inline void|void)\s+(\w+)\s*\(',text,re.M));assert len(matches)==count,(path,len(matches))
  matrix += [dict(name=m.group(1),source=path,line=text[:m.start()].count('\n')+1)for m in matches]
 result['SourceMatrix.json']=(json.dumps(dict(portableDefinitions=matrix,conditionalBackends=['sha3_armv8_ok','crypton_sha3_armv8_permute','crypton_keccak_asm_absorb_cext','crypton_keccak_asm_absorb'],constants=dict(roundConstants=24,rotations=24,permutationPositions=24),algorithms=['Keccak-'+str(n)for n in [224,256,384,512]]+['SHA3-'+str(n)for n in [224,256,384,512]]+['SHAKE128','SHAKE256']),indent=2)+'\n').encode()
 h=(V/'tests/HashSpec.hs').read_text().replace('module HashSpec (\n    spec,\n) where','module OriginalSha3HashSpec where')
 start=h.index('spec :: Spec');h=h[:start]+'''selected (name,_,_)=any (`isPrefixOf` name)["SHA3-","Keccak-","SHAKE"]
spec :: Spec
spec = do
    describe "KATs" $ mapM_ makeTestAlg (filter selected expected)
    describe "KATs over several blocks" $ mapM_ (makeTestAlgWith longVectors) (filter selected expectedLong)
    describe "Chunking" $ mapM_ makeTestChunk (filter selected expected)
    describe "Truncating" $ sequence_ (zipWith makeTestSHAKE128Truncation [1 ..] shake128TruncationBytes)
'''
 h=h.replace('import Crypto.Hash','import Data.List (isPrefixOf)\nimport Crypto.Hash',1);result['OriginalSha3HashSpec.hs']=h.encode()
 return result
if __name__=='__main__':
 for name,data in sources().items():
  p=D/name
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('Verified pinned SHA3/SHAKE originals and mechanical absorb/round observer')
