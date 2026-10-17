#!/usr/bin/env python3
"""Read-only final source/installed-artifact/evidence bindings; no conversions."""
import hashlib,json,pathlib,xml.etree.ElementTree as ET,zipfile
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 m=json.loads((HERE/'integration.json').read_text())
 for group in ['owned','noticeSources','oldExpectedUnchanged']:
  for name,pin in m[group].items():assert sha(ROOT/name)==pin,name
 for name,pin in m['history']['beforeCliSnapshots'].items():
  snapshot=HERE/'work-in-progress/cli'/(pathlib.Path(name).name+'.before');assert sha(snapshot)==pin,name
 for name,pin in m['artifacts'].items():assert sha(pathlib.Path(name))==pin,name
 p=json.loads(pathlib.Path(m['packageReport']).read_text())
 dist=pathlib.Path(m['installedDirectory'])
 for name,pin in p['installedFiles'].items():assert sha(dist/name)==pin,name
 for directory in m['durableProofs']:
  d=pathlib.Path(directory);s=json.loads((d/'status.json').read_text());assert s['state']=='terminal' and s['exitCode']==0,d
  for name,pin in s['logs'].items():assert sha(d/name)==pin['sha256'],(d,name)
 total=0
 for name in m['xml']:
  r=ET.parse(name).getroot();assert all(int(r.attrib[k])==0 for k in ['failures','errors','skipped']);total+=int(r.attrib['tests'])
 assert total==m['xmlTotal']
 for prefix in ['core','cli']:
  with zipfile.ZipFile(m[prefix+'BeforeJar'])as z:before={n:hashlib.sha256(z.read(n)).hexdigest()for n in z.namelist()if n.endswith('.class')}
  with zipfile.ZipFile(m[prefix+'AfterJar'])as z:after={n:hashlib.sha256(z.read(n)).hexdigest()for n in z.namelist()if n.endswith('.class')}
  changed=sorted(n for n,h in before.items()if after.get(n)!=h)
  assert changed==m[prefix+'ChangedClasses'],(prefix,changed)
 print('PASS final owned hashes, immutable installed files, terminal durable proofs, XML and historical/current class bindings')
if __name__=='__main__':main()
