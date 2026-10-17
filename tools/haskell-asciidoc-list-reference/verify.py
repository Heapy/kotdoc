#!/usr/bin/env python3
"""Read-only current integration and historical failed-baseline checks."""
import pathlib,json,hashlib,gzip,argparse,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2];HERE=pathlib.Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--verify-oracle',action='store_true');args=p.parse_args()
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
for name,pin in json.loads((HERE/'current-source.json').read_text())['files'].items():assert sha(ROOT/name)==pin,name
bindings=json.loads((HERE/'historical132-bindings.json').read_text())
for name,pin in json.loads((HERE/'historical132.json').read_text())['files'].items():assert sha(ROOT/bindings.get(name,name))==pin,name
for fixture,source,count in [('list','ListOracle.hs',15),('adjacent-list','AdjacentListOracle.hs',4),('comma-list','CommaListOracle.hs',5),('table-list','TableListOracle.hs',19),('null-title-list','NullTitleListOracle.hs',4),('attr-list','AttrListOracle.hs',8),('metadata-list','MetadataListOracle.hs',6),('metadata-map-list','MetadataMapOracle.hs',1),('infinite-list','InfiniteListOracle.hs',2),('protocol-only','ProtocolOnlyOracle.hs',11),('protocol-header','ProtocolHeaderOracle.hs',1),('borrowed-boundary','BorrowedBoundaryOracle.hs',20),('identifier-boundary','IdentifierBoundaryOracle.hs',10)]:
 expected=[json.loads(x)for x in gzip.open(HERE/'fixtures'/f'{fixture}.jsonl.gz','rt')];assert len(expected)==count
 if args.verify_oracle:
  with tempfile.TemporaryDirectory(prefix='asciidoc-list-') as tmp:
   binary=pathlib.Path(tmp)/'oracle';subprocess.run(['python3',str(ROOT/'tools/haskell-asciidoc-writer-reference/build-cached.py'),'--main',str(HERE/source),'--output',str(binary)],check=True)
   actual=[json.loads(x)for x in subprocess.check_output([str(binary)],text=True).splitlines()];assert actual==expected,fixture
for family,source in [('comma-walk','CommaWalkOracle.hs'),('stringify','StringifyOracle.hs'),('identity-list','IdentityListOracle.hs')]:
 requestFamily='comma-walk' if family=='identity-list' else family
 requests=gzip.decompress((HERE/f'fixtures/{requestFamily}-requests.jsonl.gz').read_bytes())
 expected=[json.loads(x)for x in gzip.open(HERE/f'fixtures/{family}-expected.jsonl.gz','rt')];assert len(expected)==34
 if args.verify_oracle:
  with tempfile.TemporaryDirectory(prefix='asciidoc-helper-') as tmp:
   binary=pathlib.Path(tmp)/'oracle';subprocess.run(['python3',str(ROOT/'tools/haskell-asciidoc-writer-reference/build-cached.py'),'--main',str(HERE/source),'--output',str(binary)],check=True)
   actual=[json.loads(x)for x in subprocess.check_output([str(binary)],input=requests).splitlines()];assert actual==expected,family
print('PASS current integration, unchanged historical132/115 expectations; 106 original writer +34 Walk +34 stringify +34 identity observations'+(' replayed'if args.verify_oracle else''))
