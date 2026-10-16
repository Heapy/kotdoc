#!/usr/bin/env python3
"""Read-only source export census. Names are not semantic coverage evidence."""
from pathlib import Path
import hashlib,json,re
ROOT=Path(__file__).resolve().parents[2]
MODULES=['Definition','Walk','Builder','Generic']
def census():
    rows=[];modules=[]
    for module in MODULES:
        path=ROOT/f'upstream/pandoc-types/src/Text/Pandoc/{module}.hs';source=path.read_text()
        header=re.search(r'module Text\.Pandoc\.'+module+r'\s*(?:\((.*?)\)\s*where|where)',source,re.S)
        assert header,module
        if header.group(1) is None:
            entries=re.findall(r'^(\w+)\s*::',source,re.M)
        else:
            block=re.sub(r'--[^\n]*','',header.group(1));entries=[x.strip()for x in block.split(',')if x.strip()]
        reexports=[e for e in entries if e.startswith('module ') or (module=='Builder' and e=='(<>)')]
        entries=[e for e in entries if e not in reexports]
        modules.append(dict(module=module,source=str(path.relative_to(ROOT)),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),owned_export_count=len(entries),reexports=reexports))
        for entry in entries:
            name=re.sub(r'\(\.\.\)$','',entry).removeprefix('pattern ').strip()
            if name=='(<>)':name='<>'
            pattern=r'^(?:(?:data|newtype|type|class|pattern)\s+)?'+re.escape(name)+r'(?:\b|\s|::)'
            match=re.search(pattern,source,re.M)
            rows.append(dict(id=f'{module}/{name}',source_export=entry,source=modules[-1]['source'],source_line=source[:match.start()].count('\n')+1 if match else None,
                             kotlin=[],original_tests=[],finite_proof=[],status='audit_pending',remaining_gap='Semantic/API/member/instance audit pending; symbol presence is not proof.'))
    assert len(rows)==140,(len(rows),modules)
    return dict(schema=1,scope='140 module-owned exports before member expansion; re-exported Definition entries excluded from Builder',modules=modules,exports=rows)
if __name__=='__main__':
    data=census();Path(__file__).with_name('export-matrix.json').write_text(json.dumps(data,indent=2)+'\n')
    print([(m['module'],m['owned_export_count'])for m in data['modules']])
