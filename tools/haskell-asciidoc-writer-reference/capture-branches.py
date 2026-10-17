#!/usr/bin/env python3
import pathlib,json,gzip,subprocess
from requests import requests
HERE=pathlib.Path(__file__).resolve().parent;TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer')
original=[json.loads(x)for x in gzip.decompress((HERE/'fixtures/original12.jsonl.gz').read_bytes()).splitlines()];qs=requests(original)
raw=subprocess.run([str(TMP/'oracle')],input=b''.join((json.dumps(q)+'\n').encode()for q in qs),capture_output=True,check=True).stdout
answers=[json.loads(x)for x in raw.splitlines()];assert len(qs)==len(answers)
p=HERE/'fixtures/branches.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(dict(request=q,expected=a))+'\n').encode()for q,a in zip(qs,answers)),mtime=0));print(len(qs),'independent original branch observations captured')
