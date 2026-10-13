#!/usr/bin/env python3
"""Unpack Haskell text's complete 21-bit-per-character case tables."""
import argparse
import hashlib
import json
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
source=root/'vendor/text-2.1.3/CaseMapping.hs'
target=root/'core/src/main/resources/io/heapy/kotdoc/case-mappings.json'
s=source.read_text()
result={'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest()}
for name in ['upper','lower','title','fold']:
    section=s.split(name+'Mapping = \\case\n',1)[1].split('  _ -> unI64 0',1)[0]
    entries={}
    for cp,packed in re.findall(r"'\\x([0-9a-f]+)'# -> unI64 ([0-9]+)",section):
        v=int(packed);chars=[]
        while v:chars.append(chr(v&((1<<21)-1)));v>>=21
        assert cp not in entries
        entries[cp]=''.join(chars)
    assert len(entries)>1000
    result[name]=entries
expected=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
tests=root/'vendor/text-2.1.3/TestsPropertiesText.hs'
t=tests.read_text()
def haskell(s):return re.sub(r'\\([0-9]+)',lambda m:chr(int(m[1])),s)
examples={}
for name,method in [('toCaseFold','fold'),('toUpper','upper')]:
    input=re.search(r'^'+name+'Exceptions = "([^"]*)"',t,re.M)[1]
    output=re.search(r'^t_'+name+'_exceptions = .* === "([^"]*)"',t,re.M)[1]
    examples[method]={'input':haskell(input),'expected':haskell(output)}
exampleTarget=root/'core/src/test/resources/structure/case-exceptions.json'
exampleExpected=json.dumps({'sourceSha256':hashlib.sha256(tests.read_bytes()).hexdigest(),'examples':examples},ensure_ascii=False,indent=2)+'\n'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
if args.check:
    assert target.read_text()==expected,'Case mapping drift'
    assert exampleTarget.read_text()==exampleExpected,'Case exception example drift'
else:
    target.write_text(expected)
    exampleTarget.write_text(exampleExpected)
print({k:len(result[k]) for k in ['upper','lower','title','fold']})
