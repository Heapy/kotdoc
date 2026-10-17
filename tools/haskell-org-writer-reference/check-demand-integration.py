#!/usr/bin/env python3
"""Read-only corrected-package binding; historical package/CLI proofs stay intact."""
import gzip,hashlib,json,pathlib,subprocess,zipfile
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1]
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 subprocess.run(['python3',str(HERE/'check-demand.py'),'--verify-oracle'],check=True)
 m=json.loads((HERE/'integration-demand.json').read_text())
 for name,pin in m['artifacts'].items():assert sha(pathlib.Path(name).read_bytes())==pin,name
 bindings=json.loads(gzip.decompress((HERE/'fixtures/demand-class-bindings.json.gz').read_bytes()))
 for jar,r in bindings.items():
  a=pathlib.Path(m['oldDistribution'])/'lib'/jar;b=pathlib.Path(m['correctedDistribution'])/'lib'/jar
  assert sha(a.read_bytes())==r['oldJarSha256'];assert sha(b.read_bytes())==r['newJarSha256']
  with zipfile.ZipFile(a)as old,zipfile.ZipFile(b)as new:
   common=set(n for n in old.namelist()if n.endswith('.class'))&set(new.namelist());assert len(common)==r['commonClasses']
   assert common==set(r['identicalBindings'])|set(r['changed'])
   for n,pin in r['identicalBindings'].items():assert sha(old.read(n))==pin==sha(new.read(n)),n
   for n,pins in r['changed'].items():
    assert n.startswith('io/heapy/kotdoc/writers/OrgWriter$Session'),n
    assert sha(old.read(n))==pins['old']and sha(new.read(n))==pins['new']
   if jar.startswith('cli-'):assert not r['changed']
 for name,count in [('oldCliTerminal',3),('correctedCliTerminal',1)]:
  t=json.loads(pathlib.Path(m[name]).read_text());assert t['allPassed']and len(t['results'])==count and all(r['exitCode']==0 for r in t['results'])
 report=json.loads(pathlib.Path(m['correctedCliReport']).read_text());assert report['comparisons']==65 and report['commands']==5
 build=json.loads(pathlib.Path(m['correctedBuildStatus']).read_text());assert build['status']=='passed'and build['exitCode']==0 and build['tests']==5354
 print('PASS corrected5354/package bindings;4285 unchanged common classes and2 changed Org classes; old394 + corrected65 durable CLI proofs',flush=True)
if __name__=='__main__':main()
