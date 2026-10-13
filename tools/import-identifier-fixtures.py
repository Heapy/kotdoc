#!/usr/bin/env python3
"""Independent identifier/emoji expectations from Pandoc 3.12.1's Lua API.

Includes every Unicode scalar in Code (bypassing emoji replacement), all emoji
aliases and complete Unicode emoji sequence tables. No Kotlin is run.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
target=root/'core/src/test/resources/structure/identifiers.json.gz'
sources=['upstream/pandoc-types/src/Text/Pandoc/Definition.hs','upstream/pandoc/src/Text/Pandoc/Shared.hs','upstream/pandoc/src/Text/Pandoc/Asciify.hs',
 'upstream/pandoc/src/Text/Pandoc/Emoji.hs','upstream/pandoc/pandoc-lua-engine/test/lua/module/pandoc-structure.lua',
 'upstream/pandoc/test/command/4742.md','upstream/pandoc/test/command/emoji.md',
 'vendor/text-2.1.3/CaseMapping.hs','vendor/text-2.1.3/Common.hs','vendor/text-2.1.3/TestsPropertiesText.hs',
 'upstream/pandoc/pandoc-lua-engine/src/Text/Pandoc/Lua/Module/Text.hs',
 'vendor/emojis-0.1.5/src/Text/Emoji.hs','vendor/emojis-0.1.5/src/Trie.hs',
 'vendor/emojis-0.1.5/src/Text/emojis.inc','vendor/emojis-0.1.5/test/test.hs']
hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}
tables=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/emojis.json').read_text())
variants=[[],['ascii_identifiers'],['gfm_auto_identifiers'],['gfm_auto_identifiers','ascii_identifiers']]
case_tables=json.loads((root/'core/src/main/resources/io/heapy/kotdoc/case-mappings.json').read_text())
format_pairs=[{'a':chr(int(k,16)),'b':v} for k,v in case_tables['fold'].items()]
format_pairs += [{'a':chr(k),'b':chr(k+1)} for k in [0xa7ce,0xa7d2,0xa7d4]]
format_pairs += [{'a':chr(k),'b':chr(k+27)} for k in range(0x16ea0,0x16eb9)]
p=argparse.ArgumentParser();p.add_argument('--check-source',action='store_true');args=p.parse_args()
if args.check_source:
    data=json.loads(gzip.decompress(target.read_bytes()))
    assert data['sources']==hashes
    assert [{'a':c['a'],'b':c['b']} for c in data['formatPairs']]==format_pairs
    assert [c['alias'] for c in data['aliases']]==[a for a,_ in tables['emojis']]
    assert [c['text'] for c in data['emojis']]==list(dict.fromkeys([v for _,v in tables['emojis']]+tables['base']+tables['zwj']))
    assert [(c['start'],c['end']) for c in data['unicode']]==[(s,min(s+4096,0x110000)) for s in range(0,0x110000,4096)]
    print(f"Verified {len(data['aliases'])} aliases, {len(data['emojis'])} emoji sequences, {len(data['edges'])} identifier cases and every Unicode scalar in {len(data['unicode'])} batches")
    raise SystemExit()
assert subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]=='pandoc 3.12.1'
def tag(t,c=None):return {'t':t,**({'c':c} if c is not None else {})}
def text(t):return [tag('Str',t)]
def doc(ils):return {'pandoc-api-version':[1,23,1,2],'meta':{},'blocks':[tag('Plain',ils)]}
def span(classes=['emoji'],kvs=[['data-emoji','alias']],id='',ils=None):return tag('Span',[[id,classes,kvs],ils or text('😡')])
edges=[]
def case(name,ils,used=None):edges.append({'name':name,'document':doc(ils),'used':used or []})
for s in ['', 'Hello, World!', 'Mr. Jones', '123 test', '1_2.3---', '  a \t b \n c  ', 'a\u0085b\u2028c\u2029d',
 'ΟΣ İ I ı ß ẞ ﬃ Æ œ Ø Ł', 'räksmörgås', 'a\u0301 á é e\u0327\u0301', '²Ⅳ四⅓', 'Привет мир 日本語',
 'non ascii ⚠️ räksmörgås', '😡 💯', '❤️‍🔥 ❤️ ❤', '👩‍💻👩x\u200d💻', '1️⃣1⃣', '🇺🇸🇺', '\U0001fae9', 'a\x00b\x1fc',
 'a\u203fb\u2040c\ufe33d\ufe4de', '%i/%s\\%n']:
    case(repr(s),text(s))
case('original Lua emphasized example',[tag('Emph',text('This')),tag('Space'),*text('is nice')])
case('original Lua used id',text('Hello, World!'),['hello-world'])
case('collision holes',text('Title'),['title','title-1','title-3'])
case('empty collisions',[],['section','section-1'])
case('exact emoji span',[span()])
case('emoji span extra id',[span(id='id')])
case('emoji span extra class',[span(classes=['emoji','other'])])
case('emoji span extra attribute',[span(kvs=[['data-emoji','alias'],['x','y']])])
case('emoji span wrong attribute',[span(kvs=[['x','alias']])])
case('emoji span empty alias',[span(kvs=[['data-emoji','']])])
case('emoji span duplicate attribute',[span(kvs=[['data-emoji','first'],['data-emoji','second']])])
case('emoji span unknown alias',[span(kvs=[['data-emoji','unknown-alias']])])
case('nested emoji span',[span(ils=[span(kvs=[['data-emoji','inner']])])])
case('code and math bypass emoji',[tag('Code',[['',[],[]],'😡']),tag('Space'),tag('Math',[tag('InlineMath'),'💯'])])
case('all inline containers',[
 *[tag(t,text('😡')) for t in ['Emph','Underline','Strong','Strikeout','Superscript','Subscript','SmallCaps']],
 tag('Quoted',[tag('DoubleQuote'),text('💯')]),tag('Link',[['',[],[]],text('😀'),['ignored','ignored']]),
 tag('Image',[['',[],[]],text('⚠️'),['ignored','ignored']]),tag('Note',[tag('Para',text('omitted'))]),
 tag('RawInline',['html','<br/>']),tag('RawInline',['html','<b>omitted</b>']),tag('SoftBreak'),tag('LineBreak')])
# Exercise the actual upstream 60000 limit without storing huge repeated used sets.
for n in [59999,60000,60001]:
    case(f'collision range {n}',text('Title'))
    edges[-1]['usedRange']=n
aliases=[{'alias':a} for a,_ in tables['emojis']]
emojis=[{'text':s} for s in dict.fromkeys([v for _,v in tables['emojis']]+tables['base']+tables['zwj'])]
unicode=[{'start':s,'end':min(s+4096,0x110000)} for s in range(0,0x110000,4096)]
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory);request=temp/'request.json'
    request.write_text(json.dumps({'aliases':aliases,'emojis':emojis,'edges':edges,'unicode':unicode,'variants':variants,'formatPairs':format_pairs}))
    script=temp/'identifiers.lua'
    script.write_text('''
local function identify(inlines,variants,used)
 local results={}
 for _,exts in ipairs(variants) do results[#results+1]=pandoc.structure.unique_identifier(inlines,used or {},exts) end
 return results
end
function Writer(doc,opts)
 local f=assert(io.open(os.getenv('KOTDOC_REQUEST')))
 local input=pandoc.json.decode(f:read('*a'),false);f:close()
 for _,c in ipairs(input.aliases) do
  c.expected=pandoc.write(pandoc.read(':'..c.alias..':','markdown+emoji'),'json')
 end
 for _,c in ipairs(input.emojis) do c.expected=identify({pandoc.Str(c.text)},input.variants) end
 for _,c in ipairs(input.edges) do
  local d=pandoc.read(pandoc.json.encode(c.document),'json')
  local used={};for _,s in ipairs(c.used) do used[s]=true end
  if c.usedRange then used.title=true;for i=1,c.usedRange do used['title-'..i]=true end end
  c.expected=identify(d.blocks[1].content,input.variants,used)
 end
 for _,c in ipairs(input.formatPairs) do c.expected=(pandoc.RawInline(c.a,'') == pandoc.RawInline(c.b,'')) end
 for _,c in ipairs(input.unicode) do
  local chars={}
  for cp=c.start,c['end']-1 do if cp<0xd800 or cp>0xdfff then chars[#chars+1]='a'..utf8.char(cp)..'z' end end
  local s=table.concat(chars)
  c.expected=identify({pandoc.Code(s)},input.variants)
  c.lower=pandoc.text.lower(s);c.upper=pandoc.text.upper(s)
 end
 return pandoc.json.encode(input)
end
''')
    output=json.loads(subprocess.check_output(['pandoc','-f','native','-t',str(script)],input=b'[]',env={**os.environ,'KOTDOC_REQUEST':str(request)}))
for c in output['aliases']:c['expected']=json.loads(c['expected'])['blocks']
output['sources']=hashes
target.parent.mkdir(parents=True,exist_ok=True)
target.write_bytes(gzip.compress(json.dumps(output,ensure_ascii=False,separators=(',',':')).encode(),mtime=0))
print(f'Imported {len(aliases)} aliases, {len(emojis)} emoji sequences, {len(edges)} edge cases and {len(unicode)} Unicode batches')
