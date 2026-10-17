#!/usr/bin/env python3
"""Verbatim pinned Ed448 core and dependencies; no Kotlin-derived expectations."""
import hashlib,json,runpy,sys,re,subprocess
from pathlib import Path
R=Path(__file__).resolve().parents[1];D=R/'tools/decaf-ed448-reference';V=R/'vendor/crypton-2.0.1'
def sources():
 subprocess.run([sys.executable,str(R/'tools/extract-decaf448-point-multiplication-reference.py'),'--check'],check=True)
 old=R/'tools/decaf448-point-multiplication-reference';result={D/p.relative_to(old):p.read_bytes()for p in(old/'original').rglob('*')if p.is_file()}
 result[D/'NormalizedPoints.c']=(old/'NormalizedPoints.c').read_bytes()
 result[D/'TableLiterals.json']=(old/'TableLiterals.json').read_bytes()
 for path in ['cbits/decaf/ed448goldilocks/eddsa.c','cbits/decaf/include/decaf/ed448.h','cbits/decaf/include/decaf/shake.h','cbits/decaf/include/decaf/sha512.h','cbits/crypton_sha3.c','cbits/crypton_sha3.h','cbits/crypton_bitfn.h','cbits/crypton_align.h','Crypto/PubKey/Ed448.hs','tests/Ed448Spec.hs']:
  target=path.removeprefix('cbits/decaf/')if path.startswith('cbits/decaf/')else path
  result[D/'original'/target]=(V/path).read_bytes()
 result[D/'OriginalEd448Spec.hs']=(V/'tests/Ed448Spec.hs').read_text().replace('module Ed448Spec (spec) where','module OriginalEd448Spec where').encode()
 c=(V/'cbits/decaf/ed448goldilocks/eddsa.c').read_text();definitions=list(re.finditer(r'^(?:static void|void|crypton_decaf_error_t)\s+(\w+)\s*\(',c,re.M));assert len(definitions)==16
 result[D/'Ed448SourceMatrix.json']=(json.dumps(dict(definitions=[dict(name=m.group(1),line=c[:m.start()].count('\n')+1,private=m.group(1)in['clamp','hash_init_with_dom','crypton_decaf_ed448_sign_internal'])for m in definitions],constants=dict(privateBytes=57,publicBytes=57,signatureBytes=114,prehashBytes=64,encodeRatio=4,decodeRatio=1,cofactor=4,contextless=False),profiles=[[32,32,False],[64,64,False],[64,32,False],[64,32,True]],headerAliases=['prehash_update=shake256_update','prehash_destroy=shake256_destroy','prehash_ctx=shake256_ctx'],remaining=['public3types3sizes8functions','fulloriginalEd448Spec25onKotlinpublic','remaininglocal613']),indent=2)+'\n').encode()
 return result
if __name__=='__main__':
 for p,data in sources().items():
  if '--check'in sys.argv:assert p.read_bytes()==data,p
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 print('PASS verbatimEd44816definitions+acceptedDecafdependencies')
