#!/usr/bin/env python3
"""Translate literal Skylighting theme tables; expectations use a separate native oracle."""
import argparse,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'vendor/skylighting-core-0.15/src/Skylighting/Styles.hs').read_text()
target=root/'core/src/main/kotlin/io/heapy/kotdoc/highlighting/HighlightStyles.kt'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
starts=list(re.finditer(r'^(\w+)\s*=\s*Style\b',source,re.M))
value=r'(Nothing|color\s+0x[\da-fA-F]+|Just\s*\(RGB\s+\d+\s+\d+\s+\d+\))'
def color(v):
 if v=='Nothing':return 'null'
 if v.startswith('color'):return 'rgb('+v.split()[1].lower()+'L)'
 r,g,b=map(int,re.findall(r'\d+',v));return f'rgb(0x{r:02x}{g:02x}{b:02x}L)'
out=['// Generated from literal Skylighting.Styles 0.15 data by tools/import-highlight-style-data.py.',
 '// Copyright (c) 2016-2024 John MacFarlane, BSD-3-Clause; see licenses/.',
 'package io.heapy.kotdoc.highlighting','',
 'import io.heapy.kotdoc.ast.UnicodeText','import kotlinx.serialization.SerializationException',
 'import java.nio.file.Files','import java.nio.file.Path','import java.nio.charset.CharacterCodingException','',
 'class HighlightStyleLookupException(message: String) : IllegalArgumentException(message)','',
 'object HighlightStyles {','    private fun rgb(value: Long) = HighlightColor.fromInt(value)']
names=[]
for i,m in enumerate(starts):
 name=m.group(1);block=source[m.end():starts[i+1].start() if i+1<len(starts) else len(source)]
 names.append(name);out+=['',f'    val {name}: HighlightStyle = HighlightStyle(', '        tokenStyles = mapOf(']
 pairs=list(re.finditer(r'\(\s*(\w+Tok)\s*,\s*(?:defStyle|TokenStyle)(?:\s*\{([^}]+)\})?\s*\)',block))
 assert pairs,name
 for pair in pairs:
  attrs=pair.group(2) or '';args=[]
  for hs,kt in [('tokenColor','color'),('tokenBackground','background')]:
   v=re.search(hs+r'\s*=\s*'+value,attrs)
   if v and v.group(1)!='Nothing':args.append(kt+' = '+color(v.group(1)))
  for hs,kt in [('tokenBold','bold'),('tokenItalic','italic'),('tokenUnderline','underline')]:
   if re.search(hs+r'\s*=\s*True',attrs):args.append(kt+' = true')
  out.append(f'            TokenType.{pair.group(1)} to TokenStyle('+', '.join(args)+'),')
 out.append('        ),')
 for key in ['defaultColor','backgroundColor','lineNumberColor','lineNumberBackgroundColor']:
  v=re.search(key+r'\s*=\s*'+value,block);assert v,(name,key)
  out.append(f'        {key} = {color(v.group(1))},')
 out.append('    )')
assert set(names)=={'kate','breezeDark','pygments','tango','espresso','haddock','monochrome','zenburn'}
out+=['','    val all: Map<String, HighlightStyle> = java.util.Collections.unmodifiableMap(linkedMapOf(',
 *[f'        "{name.lower()}" to {name},' for name in ['pygments','tango','espresso','zenburn','kate','monochrome','breezeDark','haddock']],
 '    ))','    val defaultStyle get() = pygments','',
 '    fun lookup(name: String, readTheme: (String) -> String = { Files.readString(Path.of(it)) }): HighlightStyle {',
 '        if (name.endsWith(".theme")) {',
 '            return try { HighlightStyle.parseTheme(readTheme(name)) }',
 '            catch (_: HighlightJsonException) { throw HighlightStyleLookupException("Could not read highlighting theme $name") }',
 '            catch (_: SerializationException) { throw HighlightStyleLookupException("Could not read highlighting theme $name") }',
 '            catch (_: CharacterCodingException) { throw HighlightStyleLookupException("Could not read highlighting theme $name") }',
 '        }',
 '        return all[UnicodeText.toLower(name)] ?: throw HighlightStyleLookupException("Unknown highlight-style $name")',
 '    }','}']
generated='\n'.join(out)+'\n'
if a.check:assert target.read_text()==generated;print('Verified all eight original highlighting style tables')
else:target.write_text(generated);print('Generated all eight literal highlighting style tables')
