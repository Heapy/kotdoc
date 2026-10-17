#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parents[1];V=R/'vendor/crypton-2.0.1';D=R/'tools/chacha-core-reference'
FILES=['cbits/'+p for p in ['crypton_chacha.c','crypton_chacha.h','crypton_bitfn.h','crypton_align.h','chacha_neon.c','chacha_sse2.c','chacha_sse_impl.c']]+['Crypto/Cipher/ChaCha.hs','Crypto/Random/ChaChaDRG.hs','Crypto/Random/Probabilistic.hs','Crypto/Random/Types.hs','tests/StreamCipher/ChaChaSpec.hs','tests/Imports.hs','tests/Utils.hs']
def sources():
 m=json.loads((V/'SOURCE.json').read_text());assert m['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914';out={}
 for p in FILES:
  data=(V/p).read_bytes();assert hashlib.sha256(data).hexdigest()==m['files'][p];out['original/'+p]=data
 c=(V/'cbits/crypton_chacha.c').read_text();snap='ref_snapshot("%s",%s,(uint32_t[]){'+','.join('x'+str(i)for i in range(16))+'});'
 # Only insert callbacks; every original round/output expression remains unchanged.
 needle='\tfor (i = rounds; i > 0; i -= 2) {';assert c.count(needle)==2
 for fn,label in [('chacha_core','core'),('hchacha_core','hcore')]:
  start=c.index('static void '+fn+'(');end=c.index('\n}',start)+2;part=c[start:end];part=part.replace(needle,'\t'+snap%(''+label+'Input','rounds')+'\n'+needle);q='\t\tQR(x3, x4, x9, x14);';assert part.count(q)==1;part=part.replace(q,q+'\n\t\t'+snap%(label+'Round','i'))
  if fn=='chacha_core':q='\tout->d[0] = cpu_to_le32(x0);';part=part.replace(q,'\t'+snap%('coreOutput','rounds')+'\n'+q)
  c=c[:start]+part+c[end:]
 out['ObservedChaCha.c']=c.encode()
 h=(V/'tests/StreamCipher/ChaChaSpec.hs').read_text();out['OriginalChaChaCoreSpec.hs']=h.replace('module StreamCipher.ChaChaSpec (spec) where','module OriginalChaChaCoreSpec where').encode()
 vectors=h[:h.index('data Vector')].replace('module StreamCipher.ChaChaSpec (spec) where','module ChaChaCoreVectors where')
 old="xChaCha20_ExampleKAT = fst (ChaCha.combine initState plaintext) `shouldBe` expected";assert vectors.count(old)==1;vectors=vectors.replace(old,'xChaCha20_ExampleKAT = (20,key,iv,plaintext,expected)')
 for i,counter in [(1,0),(2,1),(3,42)]:
  old=f"rfc8439A2_{i} = cipher' `shouldBe` cipher";assert vectors.count(old)==1;vectors=vectors.replace(old,f'rfc8439A2_{i} = (20,key,nonce,plain,cipher,{counter}::Word32)')
 vectors=vectors.replace('b8_128_k0_i0 =','b8_128_k0_i0, b12_128_k0_i0, b20_128_k0_i0, b8_256_k0_i0, b12_256_k0_i0, b20_256_k0_i0 :: ByteString\nb8_128_k0_i0 =',1);vectors=vectors.replace('(20,key,','(20::Int,key,');out['ChaChaCoreVectors.hs']=vectors.encode();defs=[]
 text=(V/'cbits/crypton_chacha.c').read_text()
 for x in re.finditer(r'^(?:static )?(?:void|uint32_t|uint64_t|int)\s+(\w+)\s*\([^;]+?\)\s*\{',text,re.M):defs.append(dict(name=x.group(1),source='cbits/crypton_chacha.c',line=text[:x.start()].count('\n')+1,conditional=x.group(1)=='chacha_asm_state'))
 assert len([d for d in defs if not d['conditional']])==16,len(defs)
 out['SourceMatrix.json']=(json.dumps(dict(definitions=defs,portable=16,exports=13,private=3,macros=['QR','ALIGNED64','crypton_chacha_get_state','CHACHA_SIMD_OK'],constantBytes=32,profiles=['portableGeneric','conditionalNEON','conditionalSSE2','conditionalCRYPTOGAMSassembly'],remaining=['publicChaCha2types7functions4methods2instances','ChaChaDRG','Random.Probabilistic','Prime9exports420primes','signatures/trust/chain','full613/Pandoc623/calibre624-626']),indent=2)+'\n').encode()
 return out
if __name__=='__main__':
 for p,data in sources().items():
  target=D/p
  if '--check'in sys.argv:assert target.read_bytes()==data,p
  else:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
 print('PASS16 portable ChaCha definitions/exact original files/mechanical round observer')
