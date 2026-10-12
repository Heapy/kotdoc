#!/usr/bin/env python3
"""Translate the complete declarative extension registry from pinned Pandoc."""
import argparse
import json
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
source=(root/'upstream/pandoc/src/Text/Pandoc/Extensions.hs').read_text()
source=re.sub(r'--[^\n]*','',source)
names=re.findall(r'\bExt_(\w+)',source.split('data Extension =')[1].split('deriving')[0])
assert len(names)==len(set(names))
base=source.split('pandocExtensions :: Extensions')[1].split('getDefaultExtensions ::')[0]
presets={m.group(1):m.group(2) for m in re.finditer(r'(\w+Extensions)\s*=\s*(extensionsFromList\s*\[[^]]*\])', 'pandocExtensions :: Extensions'+base)}
def definitions(section,pattern):
 matches=list(re.finditer(pattern,section,re.M))
 return {m.group(1):section[m.end():matches[i+1].start() if i+1<len(matches) else len(section)].strip() for i,m in enumerate(matches)}
defaults=definitions(source.split('getDefaultExtensions :: T.Text -> Extensions')[1].split('getAllExtensions ::')[0],r'^getDefaultExtensions\s+("[^"]*"|_)\s*=')
all_section=source.split('getAllExtensions f = universalExtensions <> getAll f')[1].split(' where',1)[1]
all_defs=definitions(all_section,r'^  (\w+(?:\s+"[^"]*"|\s+_)?)\s*=')
all_defs={re.sub(r'\s+',' ',k):v for k,v in all_defs.items()}
registry={**presets,**{'getDefaultExtensions '+k:v for k,v in defaults.items()},**all_defs}
def evaluate(expr):
 values=set()
 for part in expr.split('<>'):
  part=part.strip()
  if part=='mempty':continue
  if part.startswith('extensionsFromList'):
   assert re.fullmatch(r'extensionsFromList\s*\[[^]]*\]',part),part
   values.update(re.findall(r'Ext_(\w+)',part))
  else:
   part=re.sub(r'\s+',' ',part)
   values.update(evaluate(registry[part]))
 return values
ordered=lambda values:sorted(values,key=names.index)
data={'presets':{k:ordered(evaluate(v)) for k,v in presets.items()},
      'defaults':{k.strip('"'):ordered(evaluate(v)) for k,v in defaults.items()},
      'supported':{k.removeprefix('getAll ').strip('"'):ordered(evaluate('universalExtensions <> '+k)) for k in all_defs if k.startswith('getAll ')}}
source_format=(root/'upstream/pandoc/src/Text/Pandoc/Format.hs').read_text()
data['fileTypes']={ext:fmt for ext,fmt in re.findall(r'"(\.[^"]+)"\s*-> defFlavor "([^"]+)"',source_format)}
name_code='// Generated from Text.Pandoc.Extensions (GPL-2.0-or-later).\n// Copyright (C) 2012-2024 John MacFarlane. See licenses/.\npackage io.heapy.kotdoc.options\n\n'
name_code+='enum class ExtensionName(val wireName: String) {\n'+''.join('    '+''.join(p.capitalize() for p in name.split('_'))+'("'+name+'"),\n' for name in names)+'}\n'
outputs={'core/src/main/kotlin/io/heapy/kotdoc/options/ExtensionName.kt':name_code,
         'core/src/main/resources/io/heapy/kotdoc/extensions.json':json.dumps(data,ensure_ascii=False,indent=2)+'\n'}
for name,content in outputs.items():
 p=root/name
 if args.check:assert p.read_text()==content,name
 else:p.write_text(content)
print(f'{"Verified" if args.check else "Imported"} {len(names)} extensions, {len(presets)} presets, {len(defaults)-1} default and {len(data["supported"])-1} supported format overrides, {len(data["fileTypes"])} file mappings')
