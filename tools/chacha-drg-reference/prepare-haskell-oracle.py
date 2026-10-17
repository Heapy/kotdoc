#!/usr/bin/env python3
"""Development-only package visibility overlay; cryptographic implementations stay byte-identical."""
from pathlib import Path
import hashlib,json,shutil,sys
check="--check" in sys.argv
def save(path,data):
 if check:assert path.read_bytes()==data,path
 else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
D=Path(__file__).resolve().parent;R=D.parents[1];V=R/'vendor/crypton-2.0.1';target=Path('/tmp/kotdoc-drg-haskell-source');manifest=json.loads((V/'SOURCE.json').read_text());assert manifest['sha256']=='9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914'
for path,expected in manifest['files'].items():
 data=(V/path).read_bytes();assert hashlib.sha256(data).hexdigest()==expected,path;out=target/path
 if path!="crypton.cabal":save(out,data)
ram=R/'vendor/ram-0.22.1';rm=json.loads((ram/'SOURCE.json').read_text());assert rm['sha256']=='72474962349dd101b970cc46bcc06c799f7311d228e7b59f1a44f81427159f70'
for path,expected in rm['files'].items():assert hashlib.sha256((ram/path).read_bytes()).hexdigest()==expected,path
original=(V/'crypton.cabal').read_text();overlay=original
for module in ['Crypto.Random.ChaChaDRG','Crypto.Random.Probabilistic','Crypto.Internal.ByteArray','Crypto.Internal.Compat','Crypto.Internal.Imports','Crypto.Random.SystemDRG']:
 line='        '+module+'\n';assert overlay.count(line)==1;overlay=overlay.replace(line,'');overlay=overlay.replace('    exposed-modules:\n','    exposed-modules:\n'+line,1)
save(target/'crypton.cabal',overlay.encode())
for path,expected in manifest['files'].items():
 if path!='crypton.cabal':assert hashlib.sha256((target/path).read_bytes()).hexdigest()==expected,path
project=Path('/tmp/kotdoc-drg-haskell-oracle.project');save(project,f'packages: {D} {target} {R/"vendor/ram-0.22.1"}\noptimization: 2\nindex-state: 2026-10-13T00:00:00Z\npackage aeson\n  flags: +ordered-keymap\n'.encode());save(Path(str(project)+'.freeze'),(D/'cabal.project.freeze').read_bytes())
provenance=dict(kind='onlyCabalmodulevisibilitychanged;allimplementation/testfilesverbatim',sourceArchive=manifest['sha256'],ramArchive=rm['sha256'],ramFiles=len(rm['files']),files=len(manifest['files']),originalCabalSha256=hashlib.sha256(original.encode()).hexdigest(),overlayCabalSha256=hashlib.sha256(overlay.encode()).hexdigest(),exposedModules=['Crypto.Random.ChaChaDRG','Crypto.Random.Probabilistic','Crypto.Internal.ByteArray','Crypto.Internal.Compat','Crypto.Internal.Imports','Crypto.Random.SystemDRG'],overlayCabal=overlay)
save(D/'HaskellOracleProvenance.json',(json.dumps(provenance,indent=2)+'\n').encode());print('Prepared',len(manifest['files']),'verifiedfiles; only6hiddenmodulevisibilitydeclarationschanged')
