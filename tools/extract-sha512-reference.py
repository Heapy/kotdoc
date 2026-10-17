#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parents[1];V=R/'vendor/crypton-2.0.1';D=R/'tools/sha512-reference'
FILES=['cbits/'+p for p in ['crypton_sha512.c','crypton_sha512.h','crypton_bitfn.h','crypton_align.h','crypton_hash_prefix.c','crypton_hash_prefix.h','sha512_armv8.c']]+['Crypto/Hash.hs','Crypto/Hash/Types.hs','Crypto/Hash/SHA384.hs','Crypto/Hash/SHA512.hs','Crypto/Hash/SHA512t.hs','tests/HashSpec.hs','tests/Imports.hs','tests/Utils.hs']
def sources():
 m=json.loads((V/'SOURCE.json').read_text());assert m['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
 for p,h in m['files'].items():assert hashlib.sha256((V/p).read_bytes()).hexdigest()==h,p
 out={str(Path('original')/p):(V/p).read_bytes()for p in FILES}
 c=(V/'cbits/crypton_sha512.c').read_text();needle='\ta = ctx->h[0];';assert c.count(needle)==1;c=c.replace(needle,'\tref_snapshot(0,80,w);\n'+needle)
 for i in range(8):
  line=next(x for x in c.splitlines()if f'k[i + {i}], w[i + {i}]'in x)
  names=['a','b','c','d','e','f','g','h'];rotation=names[-((i+1)%8):]+names[:-((i+1)%8)]if(i+1)%8 else names
  c=c.replace(line,line+'\n\t\t{uint64_t v[8]={'+','.join(rotation)+'};ref_snapshot(i+'+str(i+1)+',8,v);}')
 out['ObservedSha512.c']=c.encode()
 defs=[]
 for p in ['cbits/crypton_sha512.c','cbits/crypton_hash_prefix.h','cbits/crypton_hash_prefix.c']:
  text=(V/p).read_text()
  for x in re.finditer(r'^(?:static inline |static )?(?:void|uint32_t|uint64_t)\s+(\w+(?:\(\w+\))?)\s*\(',text,re.M):defs.append(dict(name=x.group(1),source=p,line=text[:x.start()].count('\n')+1))
 assert len(defs)==23,len(defs)
 out['SourceMatrix.json']=(json.dumps(dict(definitions=defs,portableCount=23,publicExports=11,roundConstants=80,algorithms=['SHA384','SHA512','SHA512t_224','SHA512t_256'],conditionalBackends=['WITH_ARMV8_SHA512','WITH_X86_SHA512_ASM'],rawTDomain='init:all uint32;finalize:0..512 output bytes=floor(t/8);header documents multiples of8',prefixDomain='readable len bytes; uint32 conversion; len+144 must not wrap for finite bridge',remaining=['ChaChaDRG/Probabilistic/Prime','signatures','trust/chain','full613','Pandoc623/calibre624-626']),indent=2)+'\n').encode()
 h=(V/'tests/HashSpec.hs').read_text().replace('module HashSpec (\n    spec,\n) where','module OriginalSha512HashSpec where');h=h[:h.index('spec :: Spec')]+'''selected (name,_,_) = name `elem` ["SHA384","SHA512","SHA512/224","SHA512/256"]
selectedPrefix (name,_) = name `elem` ["SHA384","SHA512"]
spec :: Spec
spec = do
    describe "KATs" $ mapM_ makeTestAlg (filter selected expected)
    describe "KATs over several blocks" $ mapM_ (makeTestAlgWith longVectors) (filter selected expectedLong)
    describe "Chunking" $ mapM_ makeTestChunk (filter selected expected)
    describe "Prefix" $ mapM_ makeTestPrefix (filter selectedPrefix expectedPrefix)
    describe "Hybrid" $ mapM_ makeTestHybrid (filter selectedPrefix expectedPrefix)
''';out['OriginalSha512HashSpec.hs']=h.encode()
 return out
if __name__=='__main__':
 for p,data in sources().items():
  path=D/p
  if '--check'in sys.argv:assert path.read_bytes()==data,path
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 print('PASS23 SHA512 family definitions and exact originals/observer/test partition')
