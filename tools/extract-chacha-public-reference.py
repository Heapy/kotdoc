#!/usr/bin/env python3
from pathlib import Path
import json,hashlib,re,sys
R=Path(__file__).resolve().parents[1];V=R/'vendor/crypton-2.0.1';D=R/'tools/chacha-public-reference';m=json.loads((V/'SOURCE.json').read_text());assert m['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
files=['Crypto/Cipher/ChaCha.hs','tests/StreamCipher/ChaChaSpec.hs'];out={}
for p in files:
 data=(V/p).read_bytes();assert hashlib.sha256(data).hexdigest()==m['files'][p];out['original/'+p]=data
s=out['original/Crypto/Cipher/ChaCha.hs'].decode();names=['initialize','initializeX','combine','generate','initializeSimple','generateSimple','generateSimpleBlock'];methods=['getCounter64','setCounter64','getCounter32','setCounter32'];private=[x+"'"for x in methods];ffi=re.findall(r'^    (ccrypton_\w+)\s*(?:::|$)',s,re.M);assert len(ffi)==12,ffi
matrix=dict(moduleFunctions=[dict(name=n,line=s[:s.index('\n'+n+'\n'if'\n'+n+'\n'in s else '\n'+n+' ::')].count('\n')+2)for n in names],states=['State','StateSimple'],instances={n:methods for n in ['State','StateSimple']},derivedInstances=['NFData'],absentInstances=['Show','Eq','Ord','ByteArrayAccess'],privateWrappers=private,ffi=ffi,logicalContextBytes=132,observedCContextBytes=136,remaining=['ChaChaDRG','Random.Probabilistic','optimized raw backends','Prime/signatures/trust/chain','full613/Pandoc623/calibre624-626'])
out['SourceMatrix.json']=(json.dumps(matrix,indent=2)+'\n').encode()
for p,data in out.items():
 target=D/p
 if '--check'in sys.argv:assert target.read_bytes()==data,p
 else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
print('PASS7functions/2states/8instance-methods/4private/12FFI exact sources')
