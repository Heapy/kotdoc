#!/usr/bin/env python3
import argparse,gzip,hashlib,json,random,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'tools/edwards25519-public-reference';TARGET=ROOT/'core/src/test/resources/crypto-edwards25519-public/data.jsonl.gz'
def sources():
 for name in ['crypton-2.0.1','ram-0.22.1']:
  folder=ROOT/'vendor'/name;meta=json.loads((folder/'SOURCE.json').read_text())
  expected={'crypton-2.0.1':'9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914','ram-0.22.1':'72474962349dd101b970cc46bcc06c799f7311d228e7b59f1a44f81427159f70'};assert meta['sha256']==expected[name]
  for p,h in meta['files'].items():assert hashlib.sha256((folder/p).read_bytes()).hexdigest()==h,p
 assert (D/'OriginalEdwards25519Spec.hs').read_text()==(ROOT/'vendor/crypton-2.0.1/tests/ECC/Edwards25519Spec.hs').read_text().replace('module ECC.Edwards25519Spec (spec) where','module OriginalEdwards25519Spec where')
 modern=(ROOT/'vendor/crypton-2.0.1/tests/ECCSpec.hs').read_text().replace('module ECCSpec (spec) where','module OriginalEdwardsModernSpec where')
 start=modern.index('instance Arbitrary CurveArith');end=modern.index('data VectorPoint',start);block=modern[start:end]
 old='            [ CurveArith ECC.Curve_P256R1\n            , CurveArith ECC.Curve_P384R1\n            , CurveArith ECC.Curve_P521R1\n            , CurveArith ECC.Curve_Edwards25519\n            ]';assert block.count(old)==1
 modern=modern[:start]+block.replace(old,'            [ CurveArith ECC.Curve_Edwards25519 ]')+modern[end:];assert (D/'OriginalEdwardsModernSpec.hs').read_text()==modern
 paths=[p for p in D.iterdir()if p.is_file()]+[ROOT/'tools/import-edwards25519-public-fixtures.py',ROOT/'tools/edwards25519-scalar-reference/OriginalSamples.json',ROOT/'tools/edwards25519-points-reference/OriginalSamples.json']+[ROOT/p for p in ['vendor/crypton-2.0.1/SOURCE.json','vendor/ram-0.22.1/SOURCE.json','vendor/crypton-2.0.1/Crypto/ECC.hs','vendor/crypton-2.0.1/Crypto/ECC/Edwards25519.hs','vendor/crypton-2.0.1/tests/ECCSpec.hs','vendor/crypton-2.0.1/tests/ECC/Edwards25519Spec.hs','vendor/crypton-2.0.1/tests/Imports.hs','vendor/crypton-2.0.1/tests/Utils.hs','vendor/ram-0.22.1/Data/ByteArray/ScrubbedBytes.hs','vendor/ram-0.22.1/Data/ByteArray/Bytes.hs','vendor/crypton-2.0.1/Crypto/Number/Serialize/LE.hs','licenses/crypton-BSD-3-Clause.txt','licenses/ram-BSD-3-Clause.txt']]
 return {str(p.resolve().relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sorted(paths)}
def requests():
 rng=random.Random(255191518);rows=[]
 def add(op,**kw):rows.append(dict(op=op,**kw))
 scalar=json.loads((ROOT/'tools/edwards25519-scalar-reference/OriginalSamples.json').read_text());points=json.loads((ROOT/'tools/edwards25519-points-reference/OriginalSamples.json').read_text())
 for i,p in enumerate(points):
  s=scalar[i];v=dict(bytes=s['a'],other=s['b'],third=s['c'],point=p['a'],otherPoint=p['b'],thirdPoint=p['c'],prime=p['prime'])
  for op in ['scalarEncode','scalarEq','scalarAdd','scalarMul','pointEncode','pointEq','toPoint','pointHasPrimeOrder','pointNegate','pointAdd','pointDouble','pointMul','pointMulByCofactor','pointsMulVarTime','force','encodeScalar','encodePoint','scalarToInteger','scalarToPoint','pointBaseSmul','pointSmul','modernPointAdd','modernPointNegate','modernScalarAdd','modernScalarMul','pointsSmulVarTime']:add(op,**v)
  for family in range(25):add('property',family=family,**v)
 for sample in json.loads((D/'ModernSamples.json').read_text()):
  for family in range(4):add('modernProperty',family=family,**sample)
 for length in range(0,70):
  b=list(rng.randbytes(length))
  for op in ['scalarDecodeLong','scalarShow','pointDecode','decodeScalar','decodePoint']:add(op,bytes=b)
  for op in ['scalarGenerate','generateScalar','generateKeyPair']:add(op,bytes=b)
 order=2**252+27742317777372353535851937790883648493
 for n in [-2**256,-order,-1,0,1,order-1,order,order+1,2**255,2**256-1,2**256,2**256+1]:add('scalarFromInteger',integer=str(n))
 for n in [0,1,2,2**255-20,2**255-19,2**255-18,2**255-1,2**256-1]:
  b=list(n.to_bytes(32,'little'))
  for op in ['pointDecode','decodePoint']:add(op,bytes=b)
 for i,p in enumerate(points[:10]):
  for precedence in [-1,0,10,11,99]:
   add('scalarShow',bytes=scalar[i]['a'],precedence=precedence);add('pointShow',point=p['a'],precedence=precedence)
 for op in ['scalarShowBottom','pointShowBottom','scalarForceBottom','pointForceBottom','generateThrow','sizes']:add(op,precedence=11)
 return rows
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--oracle',type=Path);p.add_argument('--verify-oracle',action='store_true');p.add_argument('--check-source',action='store_true');a=p.parse_args()
 if a.check_source:
  with gzip.open(TARGET,'rt')as f:m=json.loads(next(f))
  assert sources()==m['sources'];print('PublicEd source pins PASS');sys.exit()
 assert a.oracle
 sample=json.loads(subprocess.check_output([str(a.oracle),'--samples']))
 if a.verify_oracle:assert sample==json.loads((D/'ModernSamples.json').read_text())
 else:(D/'ModernSamples.json').write_text(json.dumps(sample,separators=(',',':'))+'\n')
 suite=subprocess.check_output([str(a.oracle),'--original','--seed','1729','--qc-max-success','100'],text=True);assert '31 examples, 0 failures'in suite and suite.count('+++ OK, passed 100 tests.')==25
 modern=subprocess.check_output([str(a.oracle),'--modern','--seed','1729','--qc-max-success','100'],text=True)
 assert '0 failures'in modern and modern.count('+++ OK, passed 100 tests.')==3 and modern.count('+++ OK, passed 20 tests.')==3
 if not a.verify_oracle:(D/'OriginalSuite.txt').write_text(suite);(D/'OriginalModernSuite.txt').write_text(modern)
 else:
  def stable(v):return '\n'.join(x for x in v.splitlines()if not x.startswith('Finished in '))
  assert stable(suite)==stable((D/'OriginalSuite.txt').read_text());assert stable(modern)==stable((D/'OriginalModernSuite.txt').read_text())
 if a.verify_oracle:
  with gzip.open(TARGET,'rt')as f:m=json.loads(next(f));saved=[json.loads(x)for x in f]
  assert m['sources']==sources();req=[x['request']for x in saved]
 else:req=requests()
 raw=subprocess.check_output([str(a.oracle)],input=''.join(json.dumps(r)+'\n'for r in req),text=True);expected=[json.loads(x)for x in raw.splitlines()];assert len(expected)==len(req)
 rows=[dict(request=r,expected=e)for r,e in zip(req,expected)]
 if a.verify_oracle:assert rows==saved
 else:
  m=dict(sources=sources(),caseHash=hashlib.sha256(json.dumps(rows,sort_keys=True,separators=(',',':')).encode()).hexdigest(),publicFunctions=15,originalProperties=25,originalLiterals=6,modernPropertyFamilies=4)
  TARGET.parent.mkdir(parents=True,exist_ok=True)
  with TARGET.open('wb')as f:
   with gzip.GzipFile(fileobj=f,mode='wb',mtime=0)as z:
    for x in [m]+rows:z.write((json.dumps(x,separators=(',',':'))+'\n').encode())
 print(f'Original publicEd PASS {len(rows)}observations/31examples/100modernDRGtuples')
