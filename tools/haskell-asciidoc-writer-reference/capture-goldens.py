#!/usr/bin/env python3
"""Capture only original module answers, first asserting verbatim Tests.Old golden bytes."""
import pathlib,json,subprocess,gzip
HERE=pathlib.Path(__file__).resolve().parent;ROOT=HERE.parents[1];TMP=pathlib.Path('/tmp/kotdoc-asciidoc-writer')
EXE='/tmp/kotdoc-test-inventory-runtime/build/build/aarch64-osx/ghc-9.12.2/pandoc-3.12.1/t/test-pandoc/noopt/build/test-pandoc/test-pandoc'
rows=[]
for legacy in [False,True]:
 fmt='asciidoc_legacy'if legacy else'asciidoc'
 for source,golden,standalone in [('testsuite.native','writer.'+fmt,True),('tables.native','tables.'+fmt,False)]:
  doc=json.loads(subprocess.check_output([EXE,'--emulate','-f','native','-t','json',str(ROOT/'upstream/pandoc/test'/source)]))
  q=dict(name='Old/'+fmt+'/'+source,legacy=legacy,document=doc,options=dict(wrap='WrapAuto',columns=80,extensions=['auto_identifiers'],variables={'pandoc-version':''}))
  if standalone:q['template']=(ROOT/'upstream/pandoc/data/templates/default.asciidoc').read_text()
  actual=json.loads(subprocess.check_output([str(TMP/'oracle')],input=(json.dumps(q)+'\n').encode()))
  expected=(ROOT/'upstream/pandoc/test'/golden).read_text();assert actual['output']==expected,(golden,'original module versus golden')
  rows.append(dict(request=q,expected=actual,golden=golden))
p=HERE/'fixtures/goldens.jsonl.gz';assert not p.exists();p.write_bytes(gzip.compress(b''.join((json.dumps(r)+'\n').encode()for r in rows),mtime=0));print('PASS4 original module outputs equal verbatim Old goldens')
