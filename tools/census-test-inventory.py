#!/usr/bin/env python3
"""Finite source-registration census, not a Haskell evaluator or port coverage."""
import bisect
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MAIN = 'upstream/pandoc/test/test-pandoc.hs'
LUA_CAPTURE = ROOT / 'docs/test-inventory-lua-trees.json'
OBSERVER = 'tools/capture-test-inventory-lua.lua'


def source(path):
    return dict(path=path, sha256=hashlib.sha256((ROOT / path).read_bytes()).hexdigest())


def lex_haskell(text):
    """Retain offsets while excluding nested comments, strings and character bodies."""
    tokens, i = [], 0
    while i < len(text):
        start = i
        if text[i].isspace(): i += 1; continue
        if text.startswith('--', i):
            end = text.find('\n', i); i = len(text) if end < 0 else end; continue
        if text.startswith('{-', i):
            level, i = 1, i + 2
            while level and i < len(text):
                if text.startswith('{-', i): level += 1; i += 2
                elif text.startswith('-}', i): level -= 1; i += 2
                else: i += 1
            if level: raise ValueError('Unclosed Haskell block comment')
            continue
        if text[i] == '"':
            i += 1
            while i < len(text):
                if text[i] == '\\':
                    i += 1
                    if i < len(text) and text[i].isspace():
                        while i < len(text) and text[i].isspace(): i += 1
                        if i == len(text) or text[i] != '\\': raise ValueError('Malformed string gap')
                    i += 1
                elif text[i] == '"': i += 1; break
                else: i += 1
            else: raise ValueError('Unclosed Haskell string')
            tokens.append(dict(kind='string', value=text[start:i], start=start, end=i)); continue
        char = re.match(r"'(?:\\(?:[A-Za-z0-9]+|.)|[^'\\\n])'", text[i:])
        if char:
            i += len(char[0]); tokens.append(dict(kind='char', value=char[0], start=start, end=i)); continue
        quasi = re.match(r'\[([A-Za-z_][A-Za-z0-9_.]*|)\|', text[i:])
        if quasi:
            end = text.find('|]', i + len(quasi[0]))
            if end < 0: raise ValueError('Unclosed Haskell quasiquote')
            i = end + 2; tokens.append(dict(kind='quasiquote', value=text[start:i], start=start, end=i)); continue
        match = re.match(r"[A-Za-z_][A-Za-z0-9_'.]*|[!#$%&*+./<=>?@\\^|:~-]+|.", text[i:], re.S)
        i += len(match[0]); tokens.append(dict(kind='token', value=match[0], start=start, end=i))
    return tokens


def name_value(token):
    if token['kind'] != 'string': return None
    try: return json.loads(token['value'])
    except json.JSONDecodeError: return token['value']  # Retain spelling, never invent an evaluated Haskell value.


def module_edges(path):
    text = (ROOT / path).read_text()
    ts = lex_haskell(text)
    aliases = {}
    # Import regex operates on lines only after lexical comment exclusion.
    clean = [c if c in '\r\n' else ' ' for c in text]
    for t in ts:
        if t['kind']=='token': clean[t['start']:t['end']] = t['value']
    clean = ''.join(clean)
    for m in re.finditer(r'\bimport\s+(?:qualified\s+)?(Tests(?:\.\w+)+)(?:\s+as\s+(\w+))?', clean):
        aliases[m[2] or m[1]] = m[1]
    refs = []
    for t in ts:
        if t['kind'] != 'token' or not t['value'].endswith('.tests'): continue
        alias = t['value'][:-6]
        if alias not in aliases: raise ValueError('Unresolved registered module alias: ' + alias)
        refs.append(dict(module=aliases[alias], source_offset=t['start']))
    return refs


def span(text, path, a, b):
    return dict(path=path, start_offset=a, end_offset=b, offset_unit='Unicode code points after UTF8 decode; end-exclusive',
                first_line=text.count('\n', 0, a) + 1,
                last_line=text.count('\n', 0, max(a, b - 1)) + 1,
                sha256=hashlib.sha256(text[a:b].encode()).hexdigest())


def haskell_sites(path):
    text = (ROOT / path).read_text()
    ts = lex_haskell(text)
    # A declaration boundary is a lexical identifier at column zero. Strings and
    # comments are tokens/omitted, so apparent declarations inside them do not count.
    declarations = []
    for i, t in enumerate(ts):
        if t['kind'] == 'token' and re.fullmatch(r"[a-z_][\w']*", t['value']) and (t['start'] == 0 or text[t['start'] - 1] == '\n'):
            declarations.append((t['start'], t['value']))
    starts = [a for a,_ in declarations]
    root_definitions=[]
    for index,(offset,symbol) in enumerate(declarations):
        if symbol!='tests':continue
        end=declarations[index+1][0] if index+1<len(declarations) else len(text)
        region=text[offset:end]
        if re.match(r'^tests\s*=',region):root_definitions.append(span(text,path,offset,end))
    if len(root_definitions)!=1:raise ValueError('Expected one complete root tests definition: '+path)
    pairs, stack, depths = {}, [], []
    for i,t in enumerate(ts):
        depths.append(len(stack))
        if t['value'] in ('[','(','{'): stack.append(i)
        elif t['value'] in (']',')','}'):
            if not stack or ts[stack[-1]]['value'] != {']':'[',')':'(','}':'{'}[t['value']]:
                raise ValueError('Unbalanced source delimiters: ' + path)
            left = stack.pop(); pairs[left] = i
    if stack: raise ValueError('Unclosed source delimiters: ' + path)
    # Typed module-local helpers returning TestTree are explicit registration
    # constructors; signatures themselves are not calls. Imported Helpers.test is
    # included separately. This finite rule is documented rather than extrapolated.
    clean_chars=[c if c in '\r\n' else ' ' for c in text]
    for token in ts:
        if token['kind']=='token':clean_chars[token['start']:token['end']]=token['value']
    clean_text=''.join(clean_chars)
    helpers = {'test','testCase','testProperty','testGolden','testGroup','goldenVsString','goldenTest'}
    for m in re.finditer(r"^([a-z_][\w']*)\s*::([^\n]*(?:\n[ \t]+[^\n]*)*)", clean_text, re.M):
        if 'TestTree' in m[2]: helpers.add(m[1])
    groups = []
    for i,t in enumerate(ts):
        if t['kind']=='token' and t['value']=='testGroup' and i+2<len(ts):
            j=i+2
            if ts[j]['value']=='$':j+=1
            if j<len(ts) and ts[j]['value']=='[' and j in pairs:
                groups.append((j,pairs[j],name_value(ts[i+1]) or '<dynamic:' + ts[i+1]['value'] + '>'))
    out, occurrence = [], {}
    for i,t in enumerate(ts):
        if t['kind']!='token': continue
        form, name, title = None, None, None
        if t['value']=='=:' and i and ts[i-1]['kind']=='string':
            form, title = 'infix-helper-registration', ts[i-1]
        elif t['value'] in helpers and i+1<len(ts) and ts[i+1]['value'] not in ('::','='):
            # Exclude imports, type signatures and function definitions. A candidate
            # application must be inside an expression or a tests/helper RHS.
            line_start=text.rfind('\n',0,t['start'])+1
            prefix=text[line_start:t['start']]
            if prefix.lstrip().startswith(('import ','module ')):continue
            scope=declarations[bisect.bisect_right(starts,t['start'])-1][1] if starts else '<none>'
            if scope in ('module','import','infix') or (not prefix.strip() and t['value']==scope):continue
            form='group-registration' if t['value']=='testGroup' else 'constructor-registration-site'
            depth=depths[i]
            for j in range(i+1,min(len(ts),i+80)):
                if depths[j]<depth or (depths[j]==depth and ts[j]['value'] in (',',']','$','=:')):break
                if depths[j]==depth and ts[j]['kind']=='string':title=ts[j];break
        if form is None:continue
        scope_index=bisect.bisect_right(starts,t['start'])-1
        scope=declarations[scope_index][1] if scope_index>=0 else '<none>'
        # Keep helper templates distinct from direct tests declarations. No helper
        # invocation or list comprehension is assumed to have multiplicity one.
        name=name_value(title) if title else '<dynamic-name>'
        end=t['end'];depth=depths[i]
        for j in range(i+1,len(ts)):
            if (depths[j]<depth or (depths[j]==depth and ts[j]['value']==',') or
                (ts[j]['start'] in starts and ts[j]['start']>t['start'])):break
            end=ts[j]['end']
        origin=span(text,path,title['start'] if form=='infix-helper-registration' else t['start'],end)
        context=[g for a,b,g in groups if a<i<b]
        key=(scope,form,str(name));occurrence[key]=occurrence.get(key,0)+1
        out.append(dict(id='pandoc/haskell-site/'+path.split('/Tests/',1)[1]+'/'+scope+'/'+form+'/'+str(name)+'/occurrence-'+str(occurrence[key]),
                        kind=form,name=name,name_source=title['value'] if title else None,
                        declaration=scope,constructor=t['value'],groups=context,source=source(path),
                        registration_span=origin,expectation_origin=dict(**origin, interpretation='Original unevaluated registration expression; referenced helpers/data remain source dependencies.'),
                        status='unmapped',kotlin=None,execution_command='cabal test pandoc:test-pandoc',
                        multiplicity='Not inferred: helper scope, named-list reuse, comprehension/map and conditional branches require semantic expansion.',
                        gap='Source registration site only; not a proven runtime leaf or executable Kotlin mapping.'))
    directives=[]
    for token in ts:
        if token['kind']!='token' or token['value']!='#':continue
        line_start=text.rfind('\n',0,token['start'])+1
        line_end=text.find('\n',token['start'])
        line=text[line_start:line_end if line_end>=0 else len(text)]
        if re.match(r'^\s*#\s*(?:if|ifdef|ifndef|elif|else|endif)\b',line):
            directives.append(dict(line=text.count('\n',0,line_start)+1,text=line))
    computed=[span(text,path,t['start'],t['end']) | dict(token=t['value']) for t in ts if t['kind']=='token' and t['value'] in ('map','concatMap','mapM','forM','sequence','|','if')]
    return dict(source=source(path), root_tests_definition=root_definitions[0], registration_sites=out, cpp_directives=directives, computed_syntax_markers=computed,
                gap='Finite lexical constructor/infix census; all original source remains authoritative. Unrecognized helpers, local named lists and generated/conditional multiplicities require semantic expansion before a full leaf claim.')


def haskell_inventory():
    edges=module_edges(MAIN)
    queue=[x['module'] for x in edges if x['module'] not in ('Tests.Command','Tests.Old')]
    seen={};registrations=[dict(parent=MAIN,**e) for e in edges]
    while queue:
        module=queue.pop(0)
        if module in seen:continue
        path='upstream/pandoc/test/'+module.replace('.','/')+'.hs'
        record=haskell_sites(path)
        next_edges=module_edges(path)
        record['registered_module_references']=next_edges
        seen[module]=record
        for edge in next_edges:
            registrations.append(dict(parent=path,**edge));queue.append(edge['module'])
    return dict(root=source(MAIN),module_registrations=registrations,
                support_sources=[source('upstream/pandoc/test/Tests/Helpers.hs')],
                shared_assertion_semantics=dict(test='Tests.Helpers.test compares Haskell lines of ToString actual/expected; names longer than 54 source Chars are shortened to 52 plus ellipsis. Census source names are not runtime Tasty IDs.',
                                                testGolden='Tests.Helpers.testGolden compares expected/actual Text directly; CR filtering appears only in failure diff display, not acceptance normalization.',
                                                pair_operator='Tests.Helpers.=?> constructs the original input/expected pair; source expressions remain unevaluated.'),
                modules=[dict(module=k,**v) for k,v in sorted(seen.items())],
                counting_unit='Source registration sites/templates, not expanded runtime leaves or QuickCheck trials.',
                gap='Remaining complete leaf extraction requires named helper/list/comprehension expansion and behavior-specific expectations. CPP directives are recorded, not evaluated.')


def script_specs(lua):
    return [dict(id=r['id'],path=r['expectation_origin']['path']) for r in lua['registrations'] if r['kind']=='lua-script-suite']


def capture_lua(lua,write=False):
    if subprocess.check_output(['pandoc','--version'],text=True).splitlines()[0]!='pandoc 3.12.1':raise ValueError('Pinned Lua oracle required')
    specs=script_specs(lua)
    with tempfile.TemporaryDirectory(prefix='kotdoc-lua-inventory-') as temp:
        d=Path(temp);config=d/'scripts.json';output=d/'capture.json';config.write_text(json.dumps(specs))
        subprocess.run(['pandoc','lua',str(ROOT/OBSERVER),str(ROOT),str(config),str(output)],check=True)
        result=json.loads(output.read_text())
    if result['oracle_version']!='3.12.1' or result['callbacks_executed']!=0:raise ValueError('Unexpected Lua observer execution')
    result['observer']=source(OBSERVER)
    for script in result['scripts']:script['source']=source(script['path'])
    result['scope']='Original script registration execution with tasty constructors replaced; NO test callback/assertion or conversion is executed. Finite current registration environment only.'
    if write:LUA_CAPTURE.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    return result


def lua_leaves(lua):
    capture=json.loads(LUA_CAPTURE.read_text())
    if capture['observer']!=source(OBSERVER):raise ValueError('Lua registration observer changed')
    if capture['oracle_version']!='3.12.1' or capture['callbacks_executed']!=0:raise ValueError('Invalid Lua registration capture')
    if [dict(id=s['suite_id'],path=s['path']) for s in capture['scripts']]!=script_specs(lua):raise ValueError('Lua script registration inventory changed')
    out=[];empty=[];counts={}
    for script in capture['scripts']:
        path=script['path'];data=(ROOT/path).read_text();lines=data.splitlines(keepends=True)
        if script['source']!=source(path):raise ValueError('Lua script source drift: '+path)
        def walk(nodes,groups):
            for node in nodes:
                if node['kind']=='group':
                    if not node['children']:empty.append(dict(suite_id=script['suite_id'],groups=groups+[node['name']]))
                    walk(node['children'],groups+[node['name']])
                elif node['kind']=='test':
                    first,last=node['first_line'],node['last_line']
                    if not 1<=first<=last<=len(lines):raise ValueError('Invalid Lua callback span')
                    a=sum(map(len,lines[:first-1]));b=sum(map(len,lines[:last]));origin=span(data,path,a,b)
                    identity=script['suite_id']+'/'+ '/'.join(groups+[node['name']]);counts[identity]=counts.get(identity,0)+1
                    out.append(dict(id=identity+'/occurrence-'+str(counts[identity]),kind='lua-nested-registration-leaf',name=node['name'],groups=groups,
                                    source=source(path),registration_span=origin,expectation_origin=dict(**origin,interpretation='Original callback body; nested callbacks/helper data stay original source, not evaluated expectations.'),
                                    status='unmapped',kotlin=None,execution_command='cabal test pandoc-lua-engine:test-pandoc-lua-engine',
                                    gap='Finite original registration leaf captured without executing assertions; no Kotlin Lua-engine assertion mapping or cross-environment/dynamic multiplicity proof.'))
                else:raise ValueError('Unknown Lua node')
        walk(script['tree'],[])
    return dict(capture=source(str(LUA_CAPTURE.relative_to(ROOT))),leaves=out,empty_groups=empty,
                gap='Original nested registration tree captured for the pinned environment only. Test assertions/expected values are not executed or translated; dynamic platform variants require separate audit.')


if __name__ == '__main__':
    import argparse
    import importlib.util
    import sys
    sys.dont_write_bytecode = True
    parser=argparse.ArgumentParser(description=__doc__)
    action=parser.add_mutually_exclusive_group()
    action.add_argument('--capture-lua-trees',action='store_true')
    action.add_argument('--verify-lua-trees',action='store_true')
    args=parser.parse_args()
    spec=importlib.util.spec_from_file_location('extract_inventory',ROOT/'tools/extract-test-inventory.py')
    extract=importlib.util.module_from_spec(spec);spec.loader.exec_module(extract)
    lua=extract.lua_inventory()
    if args.capture_lua_trees or args.verify_lua_trees:
        result=capture_lua(lua,write=args.capture_lua_trees)
        if args.verify_lua_trees and result!=json.loads(LUA_CAPTURE.read_text()):raise ValueError('Original Lua registration replay differs')
        print('Captured' if args.capture_lua_trees else 'Verified',len(result['scripts']),'original script registration trees; no test callback executed')
    else:
        print(json.dumps(dict(haskell=haskell_inventory(),lua=lua_leaves(lua)),ensure_ascii=False,indent=2))
