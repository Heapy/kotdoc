#!/usr/bin/env python3
"""Extract every active uri001 assertion's literals, without executing either port.

Kotlin implements the original assertion helpers and properties. This exporter
recognizes the pinned source shapes and fails on unhandled/new test definitions.
Copyright (c) 2004 Graham Klyne; source BSD-3-Clause notice retained in licenses/.
"""
import argparse, hashlib, json, re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=root/'vendor/network-uri-2.6.4.2/tests/uri001.hs'
target=root/'core/src/test/resources/uri/upstream-units.json'
original=source.read_text()
quote=r'"(?:[^"\\]|\\.)*"'
# Preserve quoted URLs containing -- or {-; drop source comments only.
clean=re.sub(quote+r'|\{-.*?-\}|--[^\n]*',lambda m:m[0] if m[0].startswith('"') else '',original,flags=re.S)
def body(name):
 m=re.search(r'^'+name+r'\s*=(.*?)(?=^\S|\Z)',clean,re.M|re.S)
 assert m,name
 return m[1].strip()
def strings(s):return [json.loads(t) for t in re.findall(quote,s)]
def literal(name):
 s=body(name);assert re.fullmatch(quote,s),name
 return json.loads(s)
def record(s):
 fields={name:strings(v)[0] for name,v in re.findall(r'(uriScheme|uriPath|uriQuery|uriFragment)\s*=\s*('+quote+r')',s)}
 assert len(fields)==4,s
 a=re.search(r'uriAuthority\s*=\s*(Nothing|Just\s*\(URIAuth\s*('+quote+r')\s*('+quote+r')\s*('+quote+r')\s*\))',s)
 assert a,s
 return [fields['uriScheme'],None if a[1]=='Nothing' else strings(a[0]),fields['uriPath'],fields['uriQuery'],fields['uriFragment']]
constants={n:literal(n) for n in re.findall(r'^(\w+)\s*=\s*'+quote+r'\s*$',clean,re.M)}
def expand(s):
 return re.sub(quote+r'|\b\w+\b',lambda m:json.dumps(constants[m[0]],ensure_ascii=False) if m[0] in constants else m[0],s)
units=[]
def add(name,kind,args,expected=None,**extra):units.append(dict(name=name,kind=kind,args=args,expected=expected,**extra))
# All non-inline registrations, in the source order, including source typos.
for label,name in re.findall(r'TF\.testCase\s+('+quote+r')\s+(\w+)',clean):
 b=body(name);kind=b.split()[0];ss=strings(expand(b));display=json.loads(label)
 if kind=='testURIRef':
  m=re.fullmatch(r'testURIRef\s+(AbsId|AbsRf|RelRf|InvRf)\s+('+quote+r')',b);assert m,b
  add(display,kind,[json.loads(m[2])],m[1])
 elif kind=='testURIRefComponents':
  assert ss[0]==name
  add(display,kind,[ss[-1]],None if re.search(r'^testURIRefComponents\s+'+quote+r'\s+Nothing',b) else record(b))
 elif kind in ['testRelative','testRelJoin','testRelSplit']:
  assert len(ss)==4,b
  add(display,kind,ss[1:],label=ss[0])
 elif kind=='assertUnescapeEscapeInverse':
  assert len(ss)==2,b
  add(display,kind,[ss[1]])
 elif name=='testURIRef122':
  assert len(ss)==5,b
  add(display,'authority',[ss[4]],ss[1:4])
 elif name.startswith('testNormalize'):
  assert len(ss)==3,b
  fns=re.findall(r'\b(normalizeCase|normalizeEscape|normalizePathSegments)\b',b)
  assert 1<=len(fns)<=3,b
  add(display,'normalization',[ss[-1],fns[::-1]],ss[1])
 elif name.startswith('testShowURI'):
  if 'nullURI' in b:u=['',None,'','','']
  else:u=record(body(re.search(r'\b(ts\d+URI)\b',b)[1]))
  add(display,'render',[u,'uriToString' in b],ss[1])
 elif name.startswith('testEscapeURIString'):
  assert len(ss)==3,b
  fn=re.search(r'\b(escapeURIString|unEscapeString)\b',b)[1]
  pred='component' if 'isUnescapedInURIComponent' in b else 'uri'
  add(display,fn,[ss[-1],pred],ss[1])
 elif name.startswith('testRelativeTo'):
  assert len(ss)==3,b
  add(display,'relativeTo',[ss[-1],strings(body('trbase'))[0],'nonStrictRelativeTo' in b],ss[1])
 elif name.startswith('testAltFn'):
  fn=re.search(r'\b(parseURI|parseURIReference|parseRelativeReference|parseAbsoluteURI|isURI|isURIReference|isRelativeReference|isAbsoluteURI)\b',b)[1]
  result=ss[1] if fn.startswith('parse') else ' True ' in b
  add(display,'alternative',[fn,ss[-1]],result)
 else:raise AssertionError((name,b))
# Inline assertions have no top-level test definition; retain every registration.
for group in ['testIsAbsolute','testIsRelative']:
 for label,arg in re.findall(r'TF\.testCase\s+('+quote+r')\s*\$\s*testUriIs(?:Absolute|Relative)\s+('+quote+r')',body(group)):
  add(json.loads(label),group,[json.loads(arg)])
for label,fn,arg in re.findall(r'TF\.testCase\s+('+quote+r')\s*\$\s*assertJust\s+testPathSegmentsRoundTrip\s*\$\s*(parseURIReference|parseURI)\s+('+quote+r')',body('testPathSegments')):
 add(json.loads(label),'pathSegments',[fn,json.loads(arg)])
rects=re.split(r'TF\.testCase',body('testRectify'))[1:];assert len(rects)==3
for i,b in enumerate(rects):
 if i<2:
  u=record(b);ss=strings(b)
  expected=ss[-1] if i==0 else ss[2]
  add('', 'rectify',[u],expected)
 else:
  ss=strings(b);assert len(ss)==7,ss
  add('','rectifyAuth',[ss[3:6]],ss[2])
# Fail rather than silently omit active or newly introduced cases/properties.
assert len(units)==len(re.findall(r'\bTF\.testCase\b',clean))==339,len(units)
properties=re.findall(r'TF\.testProperty\s+('+quote+r')\s+(\w+)',clean)
assert [(json.loads(a),b) for a,b in properties]==[(n,n) for n in ['propEscapeUnEscapeLoop','propEscapeUnEscapeLoopHiChars']]
result=dict(source=str(source.relative_to(root)),sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),units=units,properties=[b for a,b in properties])
output=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
if args.check:assert target.read_text()==output,'URI source test extraction changed'
else:target.write_text(output)
print('Verified' if args.check else 'Extracted',len(units),'original URI unit tests and',len(properties),'property definitions')
