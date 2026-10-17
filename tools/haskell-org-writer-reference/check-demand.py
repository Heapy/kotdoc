#!/usr/bin/env python3
"""Read-only additive source/demand verification; old provenance remains immutable."""
import argparse,gzip,hashlib,json,pathlib,subprocess
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];MANIFEST=HERE/'demand-provenance.json';TMP=pathlib.Path('/tmp/kotdoc-org-writer')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def records():return[json.loads(x)for name in ['demand-observations','demand-extra-observations']for x in gzip.decompress((HERE/('fixtures/'+name+'.jsonl.gz')).read_bytes()).splitlines()]
def check():
 subprocess.run(['python3',str(HERE/'prepare.py'),'--check-source'],check=True)
 m=json.loads(MANIFEST.read_text())
 for name,pin in m['files'].items():assert sha(ROOT/name)==pin,name
 for name,pin in m['runtime'].items():assert sha(pathlib.Path(name))==pin,name
 old=json.loads((HERE/'fixtures/history/pre-demand-freeze.json').read_text());source='core/src/main/kotlin/io/heapy/kotdoc/writers/OrgWriter.kt';assert sha(HERE/'fixtures/history/OrgWriter-pre-demand.kt')==old[source]
 for name,pin in old.items():
  if name!=source:assert sha(ROOT/name)==pin,name
 assert len(records())==9
 before=(HERE/'fixtures/history/OrgWriter-pre-demand.kt').read_text();needle='val old=DocumentShared.toLegacyTable(b.copy(caption=Caption(null,emptyList())));val cap=captionInlines(b.caption.long);val caption=inlines(cap);';replacement='val cap=captionInlines(b.caption.long);val caption=inlines(cap);val dimensions=List(if(b.head.rows.isEmpty()&&b.bodies.isEmpty()&&b.foot.rows.isEmpty())0 else b.columns.size){ColSpec(io.heapy.kotdoc.ast.Alignment.AlignDefault,ColWidth.Default)};val old=DocumentShared.toLegacyTable(b.copy(caption=Caption(null,emptyList()),columns=dimensions));';assert before.replace(needle,replacement)==(ROOT/source).read_text()
 print('PASS additive demand pins, exact local transformation, old72 snapshot except documented Org source change',flush=True)
def verify():
 check()
 for row in records():
  p=subprocess.run([str(TMP/('demand-oracle' if row['request']['kind'] in ['bad-root','bad-pair','bad-tuple-fields','bad-spine-tail','empty']else'demand-extra-oracle')),row['request']['kind']],capture_output=True,check=True);assert json.loads(p.stdout)==row['expected'];assert p.stderr.decode().splitlines()==row['events']
 print('PASS9 independent original demand/output/error/ordered-evaluation observations',flush=True)
def capture():
 assert not MANIFEST.exists();paths=[HERE/'DemandOracle.hs',HERE/'DemandExtraOracle.hs',HERE/'DEMAND-NOTES.md',pathlib.Path(__file__),HERE/'fixtures/demand-observations.jsonl.gz',HERE/'fixtures/demand-extra-observations.jsonl.gz',*list((HERE/'fixtures/history').iterdir()),ROOT/'core/src/main/kotlin/io/heapy/kotdoc/writers/OrgWriter.kt',ROOT/'core/src/test/kotlin/io/heapy/kotdoc/writers/OrgWriterDemandTest.kt',ROOT/'core/src/test/resources/org-writer/demand-observations.jsonl.gz',ROOT/'core/src/test/resources/org-writer/demand-extra-observations.jsonl.gz'];MANIFEST.write_text(json.dumps(dict(files={str(p.relative_to(ROOT)):sha(p)for p in paths},runtime={str(TMP/name):sha(TMP/name)for name in ['demand-oracle','demand-extra-oracle']}),indent=2)+'\n')
def main():
 p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');p.add_argument('--verify-oracle',action='store_true');p.add_argument('--capture-manifest',action='store_true');a=p.parse_args()
 if a.capture_manifest:capture()
 elif a.verify_oracle:verify()
 elif a.check_source:check()
 else:p.error('choose mode')
if __name__=='__main__':main()
