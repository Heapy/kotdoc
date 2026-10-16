#!/usr/bin/env python3
"""Read-only Python AST/source inventory. Never import calibre or execute test bodies."""
import ast
from collections import Counter
import hashlib
from functools import lru_cache
import importlib.util
import json
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'upstream/calibre/src'
SETUP='upstream/calibre/setup/test.py'
RUNNER='upstream/calibre/src/calibre/utils/run_tests.py'
BUILTINS='upstream/calibre/src/calibre/customize/builtins.py'
UI='upstream/calibre/src/calibre/customize/ui.py'
spec=importlib.util.spec_from_file_location('calibre_haskell_census',ROOT/'tools/census-test-inventory.py')
census=importlib.util.module_from_spec(spec);spec.loader.exec_module(census)


def source(path):
    return dict(path=str(path),sha256=hashlib.sha256((ROOT/path).read_bytes()).hexdigest())


def parsed(path):
    text=(ROOT/path).read_text();return text,ast.parse(text,filename=str(path))


@lru_cache(maxsize=8)
def source_offsets(text):
    data=text.encode()
    return data,[0]+[m.end() for m in re.finditer(b'\n',data)]


def fragment(path,text,node):
    data,starts=source_offsets(text)
    start=starts[node.lineno-1]+node.col_offset
    end=starts[node.end_lineno-1]+node.end_col_offset
    segment=data[start:end]
    return dict(path=str(path),first_line=node.lineno,last_line=node.end_lineno,sha256=hashlib.sha256(segment).hexdigest())


def module_path(module):
    base=SRC.joinpath(*module.split('.'))
    for path in [base.with_suffix('.py'),base/'__init__.py']:
        if path.is_file():return str(path.relative_to(ROOT))
    raise ValueError('Registered module source unavailable: '+module)


def imports(statements,environment=None):
    environment=dict(environment or {})
    for stmt in statements:
        if isinstance(stmt,ast.ImportFrom) and stmt.level==0:
            for a in stmt.names:environment[a.asname or a.name]=(stmt.module,a.name)
        elif isinstance(stmt,ast.Import):
            for a in stmt.names:environment[a.asname or a.name]=(a.name,None)
    return environment


def factory_routes(text,tree):
    fn=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='find_tests')
    rows=[]
    def walk(stmts,env,conditions=(),group=None):
        env=dict(env)
        for stmt in stmts:
            if isinstance(stmt,(ast.ImportFrom,ast.Import)):env.update(imports([stmt]))
            elif isinstance(stmt,ast.If):
                test=stmt.test;newgroup=group
                if isinstance(test,ast.Call) and isinstance(test.func,ast.Name) and test.func.id=='ok':
                    if len(test.args)!=1 or not isinstance(test.args[0],ast.Constant) or not isinstance(test.args[0].value,str):raise ValueError('Dynamic root group')
                    newgroup=test.args[0].value
                walk(stmt.body,env,conditions+(ast.unparse(test),),newgroup)
                if stmt.orelse:walk(stmt.orelse,env,conditions+('NOT ('+ast.unparse(test)+')',),group)
            elif isinstance(stmt,ast.Expr) and isinstance(stmt.value,ast.Call) and isinstance(stmt.value.func,ast.Name) and stmt.value.func.id=='a':
                append=stmt.value
                if group is None or len(append.args)!=1 or append.keywords or not isinstance(append.args[0],ast.Call):raise ValueError('Unsupported root append')
                call=append.args[0]
                if isinstance(call.func,ast.Name):
                    if call.func.id not in env:raise ValueError('Unresolved registered factory '+call.func.id)
                    module,symbol=env[call.func.id]
                elif ast.unparse(call.func)=='unittest.defaultTestLoader.loadTestsFromTestCase' and len(call.args)==1 and isinstance(call.args[0],ast.Name):
                    module,symbol='calibre.utils.run_tests',call.args[0].id
                else:raise ValueError('Unsupported registered factory '+ast.unparse(call))
                rows.append(dict(group=group,module=module,symbol=symbol,conditions=list(conditions),call=ast.unparse(call),registration=fragment(RUNNER,text,append)))
            elif any(isinstance(n,ast.Call) and isinstance(n.func,ast.Name) and n.func.id=='a' for n in ast.walk(stmt)):
                raise ValueError('Unrecognized root append context')
    walk(fn.body,imports(tree.body))
    # Independent count prevents control-flow traversal from omitting an append.
    expected=sum(isinstance(n,ast.Call) and isinstance(n.func,ast.Name) and n.func.id=='a' for n in ast.walk(fn))
    if len(rows)!=expected:raise ValueError('Root factory append census differs')
    counts=Counter()
    for row in rows:
        key=(row['group'],row['module'],row['symbol']);counts[key]+=1
        row['id']='calibre/factory/'+('/'.join(key))+'/occurrence-'+str(counts[key])
        row.update(kind='registered-factory-route',status='unmapped',kotlin=None,source=source(module_path(row['module'])),
                   counting_units=dict(declared_route=1,listed=None,runtime_leaves=None,assertions=None),
                   expectation_origin='Original factory, test method bodies and independently retained fixtures; runtime expansion not executed.',
                   gap='Unobserved runtime imports/Qt/native dependencies/platform/network/resources and generated/inherited test multiplicities; no executable Kotlin mapping.')
    return rows


def method_sites(path,text,tree,prefix=()):
    rows=[]
    def visit(node,qual=()):
        for child in ast.iter_child_nodes(node):
            if isinstance(child,(ast.ClassDef,ast.FunctionDef,ast.AsyncFunctionDef)):
                name=qual+(child.name,)
                if isinstance(child,(ast.FunctionDef,ast.AsyncFunctionDef)) and child.name.startswith('test_'):
                    assertions=[]
                    for x in ast.walk(child):
                        if isinstance(x,ast.Assert) or (isinstance(x,ast.Call) and isinstance(x.func,ast.Attribute) and isinstance(x.func.value,ast.Name) and x.func.value.id in ('self','cls') and x.func.attr.startswith('assert')):
                            assertions.append(dict(expression=ast.unparse(x),source=fragment(path,text,x)))
                    rows.append(dict(id='calibre/source-method/'+path+'/'+'.'.join(name),name='.'.join(name),kind='source-test-named-function-declaration',
                                     source=fragment(path,text,child),decorators=[ast.unparse(d) for d in child.decorator_list],assertion_sites=assertions,
                                     counting_unit='Source test_-named function/method declaration/sites; not runtime/inherited/parameterized leaf or executed assertion count.',
                                     expectation_origin='Original method body, assertion expressions and referenced original fixture/helper values; fixtures not rewritten.',
                                     status='unmapped',kotlin=None,gap='Reachability, inherited/generated cases, subTest/loop multiplicities, helper/fixture expectations and executable mappings pending.'))
                visit(child,name)
            else:visit(child,qual)
    visit(tree,prefix)
    seen=Counter()
    for row in rows:
        seen[row['id']]+=1;row['id']+='/occurrence-'+str(seen[row['id']])
    return rows


def factory_details(route):
    path=route['source']['path'];text,tree=parsed(path)
    targets=[n for n in tree.body if isinstance(n,(ast.FunctionDef,ast.ClassDef)) and n.name==route['symbol']]
    if len(targets)!=1:raise ValueError('Factory source definition missing/ambiguous: '+route['id'])
    target=targets[0]
    methods=method_sites(path,text,target,(target.name,))
    packages=[]
    for node in ast.walk(target):
        if isinstance(node,ast.Call) and isinstance(node.func,ast.Name) and node.func.id=='find_tests_in_package':
            if len(node.args)!=1 or node.keywords or not isinstance(node.args[0],ast.Constant) or not isinstance(node.args[0].value,str):raise ValueError('Unsupported package loader arguments')
            package=node.args[0].value;directory=SRC.joinpath(*package.split('.'))
            if not directory.is_dir():raise ValueError('Registered package unavailable')
            modules=[]
            for child in sorted(directory.iterdir()):
                if child.suffix=='.py' and child.name!='main.py':modules.append(dict(module=package+'.'+child.stem,source=source(str(child.relative_to(ROOT)))))
            packages.append(dict(package=package,call_source=fragment(path,text,node),source_modules=modules,
                                 semantics='Immediate .py modules including __init__, excluding main.py. Original runtime also accepts .pyc and deduplicates same basename; source profile contains no .pyc. No recursive filename test filter.',
                                 runtime_leaves=None,gap='Original imports/loadTestsFromModule, load_tests hooks, inherited/generated cases and native availability unobserved.'))
    return dict(id=route['id'],definition=fragment(path,text,target),calls=[dict(expression=ast.unparse(n),source=fragment(path,text,n)) for n in ast.walk(target) if isinstance(n,ast.Call)],
                nested_source_methods=methods,package_expansions=packages,
                conditions='Factory body preserved, not executed; keyword arguments such as only_build/return_tests remain in route call.',
                gap='Calls are source expressions, not automatically followed/evaluated factory closure. Runtime dynamic registration remains unknown.')


def static_value(node):
    value=ast.literal_eval(node)
    if isinstance(value,str):return value
    if isinstance(value,(set,list,tuple)) and all(isinstance(x,str) for x in value):return sorted(value)
    raise ValueError('Nonliteral format declaration')


def builtin_formats():
    text,tree=parsed(BUILTINS)
    aliases={}
    for stmt in tree.body:
        if isinstance(stmt,ast.ImportFrom) and stmt.module and stmt.module.startswith('calibre.ebooks.conversion.plugins.'):
            for a in stmt.names:aliases[a.asname or a.name]=(stmt.module,a.name)
    registered=[]
    for stmt in tree.body:
        if isinstance(stmt,ast.AugAssign) and isinstance(stmt.target,ast.Name) and stmt.target.id=='plugins' and isinstance(stmt.value,ast.List):
            for item in stmt.value.elts:
                if isinstance(item,ast.Name) and item.id in aliases:registered.append((item.id,fragment(BUILTINS,text,item)))
    if Counter(n for n,_ in registered)!=Counter(list(aliases)):raise ValueError('Imported conversion classes are omitted/duplicated in builtin registration')
    rows=[]
    for alias,registration in registered:
        module,name=aliases[alias];path=module_path(module);body,t=parsed(path)
        classes={n.name:n for n in t.body if isinstance(n,ast.ClassDef)}
        def ancestry(n,seen=()):
            if n in seen:raise ValueError('Cyclic format inheritance')
            node=classes[n];bases=[ast.unparse(b) for b in node.bases]
            found=[b for b in bases if b in ('InputFormatPlugin','OutputFormatPlugin')]
            for b in bases:
                if b in classes:found+=ancestry(b,seen+(n,))
            return found
        bases=ancestry(name)
        if len(bases)!=1:raise ValueError('Unknown conversion plugin direction '+name)
        direction='input' if bases[0]=='InputFormatPlugin' else 'output'
        attribute='file_types' if direction=='input' else 'file_type'
        declarations=[n for n in classes[name].body if isinstance(n,ast.Assign) and any(isinstance(x,ast.Name) and x.id==attribute for x in n.targets)]
        if len(declarations)!=1:raise ValueError('Missing/dynamic format attribute '+name)
        value=static_value(declarations[0].value);formats=[value] if isinstance(value,str) else value
        rows.append(dict(id='calibre/plugin/'+module+'/'+name,class_name=name,direction=direction,formats=formats,source=source(path),registration=registration,
                         declaration=fragment(path,body,declarations[0]),ancestry=[ast.unparse(b) for b in classes[name].bases],
                         availability='builtin declaration; initialization/disabled/user/system plugin overrides not executed',status='unmapped',kotlin=None))
    utext,utree=parsed(UI)
    extras={}
    for direction in ('input','output'):
        fn=next(n for n in utree.body if isinstance(n,ast.FunctionDef) and n.name=='available_'+direction+'_formats')
        values=[]
        for n in ast.walk(fn):
            if isinstance(n,ast.Call) and ast.unparse(n.func)=='formats.add':
                if len(n.args)!=1 or n.keywords:raise ValueError('Unexpected UI add format call')
                arg=n.args[0]
                if isinstance(arg,ast.Constant) and isinstance(arg.value,str):values.append(dict(format=arg.value,source=fragment(UI,utext,n)))
                elif ast.unparse(arg) not in ('format','plugin.file_type'):raise ValueError('Unknown dynamic UI format addition')
        extras[direction]=values
    return rows,extras


def pandoc_formats(path,symbol,constructor_suffix):
    text=(ROOT/path).read_text();tokens=census.lex_haskell(text)
    starts=[i for i,t in enumerate(tokens[:-2]) if t['value']==symbol and tokens[i+1]['value']=='=' and tokens[i+2]['value']=='[']
    if len(starts)!=1:raise ValueError('Ambiguous Pandoc format list')
    i=starts[0]+3;rows=[]
    while tokens[i]['value']!=']':
        if tokens[i]['value']==',':i+=1
        chunk=tokens[i:i+6]
        if len(chunk)!=6 or [chunk[j]['value'] for j in (0,2,5)]!=['(',',',')'] or chunk[1]['kind']!='string' or not chunk[3]['value'].endswith(constructor_suffix):raise ValueError('Unsupported Pandoc format tuple')
        name=json.loads(chunk[1]['value']);rows.append(dict(id='pandoc/'+symbol+'/'+name,name=name,kind=chunk[3]['value'],implementation=chunk[4]['value'],source=source(path),line=text.count('\n',0,chunk[0]['start'])+1))
        i+=6
    if len(set(r['name'] for r in rows))!=len(rows):raise ValueError('Duplicate Pandoc format name')
    return rows


def directional_formats():
    plugins,extras=builtin_formats()
    readers=pandoc_formats('upstream/pandoc/src/Text/Pandoc/Readers.hs','readers','Reader')
    writers=pandoc_formats('upstream/pandoc/src/Text/Pandoc/Writers.hs','writers','Writer')
    fmt='upstream/pandoc/src/Text/Pandoc/Format.hs';text=(ROOT/fmt).read_text()
    aliases={m[1][1:]:dict(target=m[2],source=source(fmt),line=text.count('\n',0,m.start())+1) for m in re.finditer(r'^\s*"(\.[^"]+)"\s*->\s*defFlavor "([^"]+)"',text,re.M)}
    archive_path='upstream/calibre/src/calibre/ebooks/conversion/archives.py'
    archive_text,archive_tree=parsed(archive_path)
    archive_node=next(n for n in archive_tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='ARCHIVE_FMTS' for t in n.targets))
    archive_formats=static_value(archive_node.value)
    plumber_path='upstream/calibre/src/calibre/ebooks/conversion/plumber.py'
    plumber_text,plumber_tree=parsed(plumber_path)
    supported=next(n for n in plumber_tree.body if isinstance(n,ast.FunctionDef) and n.name=='supported_input_formats')
    loops=[n for n in ast.walk(supported) if isinstance(n,ast.For)]
    if len(loops)!=1 or static_value(loops[0].iter)!=archive_formats:raise ValueError('Archive/pipeline format reconciliation differs')
    archive_routes=[dict(format=f,source=fragment(archive_path,archive_text,archive_node),pipeline_registration=fragment(plumber_path,plumber_text,loops[0])) for f in archive_formats]
    rows=[]
    for direction,registry in [('input',readers),('output',writers)]:
        index={r['name']:r for r in registry}
        names=sorted({f for p in plugins if p['direction']==direction for f in p['formats']}|{r['format'] for r in extras[direction]}|({r['format'] for r in archive_routes} if direction=='input' else set()))
        for name in names:
            exact=index.get(name);heuristic=aliases.get(name);target=index.get(heuristic['target']) if heuristic else None
            if exact:relation='exact-direction-registry-name'
            elif direction=='output' and name=='pdf':relation='separate-Pandoc-PDF-engine-output-route'
            elif target:relation='extension-heuristic-target-in-direction-registry'
            else:relation='no-matching-pinned-direction-registry-route'
            rows.append(dict(id='calibre/format/'+direction+'/'+name,direction=direction,format=name,plugin_refs=[p['id'] for p in plugins if p['direction']==direction and name in p['formats']],
                             ui_extra=[r for r in extras[direction] if r['format']==name],pipeline_archive=[r for r in archive_routes if direction=='input' and r['format']==name],pandoc_exact=exact,pandoc_extension_inference=heuristic,pandoc_heuristic_registry_target=target,relation=relation,
                             container_semantics='Plugin token/container route is retained separately; names/extensions and shared plugin class do not prove format equivalence. ZIP/RAR input extras are archive routes; ZIP output is HTML container; OEB output is a directory route.',
                             status='unmapped',kotlin=None,gap='Directional registration comparison only. Semantic equivalence, complete container/alias distinctions and actual Kotlin conversion/tests require independent acceptance; calibre implementation waits for full Pandoc.'))
    return dict(plugins=plugins,ui_extra_formats=extras,pipeline_archive_routes=archive_routes,pipeline_source=source(plumber_path),
                pipeline_conditions='supported_input_formats adds zip/rar/oebzip; Plumber strips original_ prefix, optionally maps kepub to epub for viewing, unarchives ARCHIVE_FMTS; existing output directories select oeb. These are route conditions, not semantic alias equivalence.',pandoc_readers=readers,pandoc_writers=writers,pandoc_extension_heuristics=aliases,rows=rows,
                pdf_output_origin=source('upstream/pandoc/src/Text/Pandoc/App/OutputSettings.hs'),
                runtime_profile='Source declarations only. No calibre imports/initialization, third-party/system plugins, disabled states, native toolchains or format conversion execution.',
                gap='Runtime availability profiles and semantic per-format/container parity remain unestablished; absent direction routes retain the full additional-format goal.')





def qualified_definitions(node,prefix=()):
    for child in ast.iter_child_nodes(node):
        if isinstance(child,(ast.FunctionDef,ast.AsyncFunctionDef,ast.ClassDef)):
            name=prefix+(child.name,)
            yield name,child
            yield from qualified_definitions(child,name)
        else:yield from qualified_definitions(child,prefix)

def runner_definition():
    text,tree=parsed('upstream/calibre/src/calibre/utils/rapydscript.py')
    return text,next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='run_rapydscript_tests')

def pinned_code_catalog():
    raw=subprocess.check_output(['git','ls-tree','-r','-z','HEAD','src'],cwd=ROOT/'upstream/calibre')
    paths=[]
    for entry in raw.split(b'\0'):
        if not entry:continue
        metadata,name=entry.split(b'\t',1);mode,kind,oid=metadata.split()
        relative=name.decode()
        if Path(relative).suffix not in ('.py','.pyj'):continue
        path='upstream/calibre/'+relative;data=(ROOT/path).read_bytes()
        if kind!=b'blob' or mode==b'120000':raise ValueError('Unsupported pinned code entry')
        if hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()!=oid.decode():raise ValueError('Pinned calibre source modified: '+path)
        paths.append(path)
    actual=sorted(str(p.relative_to(ROOT)) for p in SRC.rglob('*') if p.is_file() and p.suffix in ('.py','.pyj'))
    if sorted(paths)!=actual:raise ValueError('Untracked/missing calibre code source')
    return sorted(paths)

def inventory():
    expected='61c4027d2bfe737e0177e09952ee4942042a5892'
    pin=subprocess.check_output(['git','rev-parse','HEAD:upstream/calibre'],cwd=ROOT,text=True).strip()
    actual=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'upstream/calibre',text=True).strip()
    if pin!=expected or actual!=expected:raise ValueError('Unexpected pinned calibre revision')
    original_setup=subprocess.check_output(['git','show','HEAD:setup/test.py'],cwd=ROOT/'upstream/calibre')
    if original_setup!=(ROOT/SETUP).read_bytes():raise ValueError('Pinned calibre setup registry modified')
    setup,setup_tree=parsed(SETUP)
    assignment=next(n for n in setup_tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='TEST_MODULES' for t in n.targets))
    expr=assignment.value
    if not (isinstance(expr,ast.Call) and ast.unparse(expr.func)=='frozenset' and len(expr.args)==1 and isinstance(expr.args[0],ast.Call) and isinstance(expr.args[0].func,ast.Attribute) and expr.args[0].func.attr=='split' and isinstance(expr.args[0].func.value,ast.Constant)):raise ValueError('Dynamic setup test group registry')
    groups=expr.args[0].func.value.value.split()
    runner,rtree=parsed(RUNNER);routes=factory_routes(runner,rtree)
    if set(groups)!={r['group'] for r in routes} or len(set(groups))!=len(groups):raise ValueError('Setup/runtime group reconciliation differs')
    details=[factory_details(r) for r in routes]
    code_paths=pinned_code_catalog()
    methods=[];factories=[];unsupported=[]
    for path in code_paths:
        if Path(path).suffix=='.pyj':
            unsupported.append(dict(source=source(path),dialect='RapydScript',status='unmapped',gap='Not parsed as Python; separate original RapydScript compiler/runner registration census required.'))
            continue
        text,tree=parsed(path)
        methods+=method_sites(path,text,tree)
        occurrences=Counter()
        for qualified,n in qualified_definitions(tree):
            if isinstance(n,ast.FunctionDef) and n.name in ('find_tests','load_tests','suite','run_tests','test','run_rapydscript_tests'):
                name='.'.join(qualified);occurrences[name]+=1
                factories.append(dict(id='calibre/source-factory/'+path+'/'+name+'/occurrence-'+str(occurrences[name]),symbol=name,definition=fragment(path,text,n),
                                      status='unmapped',kotlin=None,gap='Source factory declaration only; not proof of reachability from primary runner or complete runtime leaves.'))
    return dict(schema_version=1,pinned_calibre=dict(version='9.16.0',revision=pin),roots=[source(SETUP),source(RUNNER),source(BUILTINS),source(UI)],
                source_catalog=[source(p) for p in code_paths],separate_dialect_sources=unsupported,test_groups=groups,registered_factory_routes=routes,factory_source_details=details,
                source_method_declarations=methods,all_source_factory_declarations=factories,directional_formats=directional_formats(),
                separate_runner=dict(id='calibre/runner/TestRS',source=source(SETUP),target='calibre.utils.rapydscript.run_rapydscript_tests',definition=fragment('upstream/calibre/src/calibre/utils/rapydscript.py',*runner_definition()),entry_resource=source('upstream/calibre/src/pyj/test.pyj'),registration_helper=source('upstream/calibre/src/pyj/testing.pyj'),status='unmapped',kotlin=None,gap='Separate RapydScript runner declared; nested registration/resource/runtime closure pending.'),
                runtime_profile=dict(imports_executed=False,test_assertions_executed=False,listed=None,available=None,conditions='Primary registry group predicates, OS conditions and factory kwargs retained. Qt/native modules, resources, network/system services and external plugins not initialized.'),
                gap='Source declaration checkpoint, not complete runtime leaf/expectation inventory or Kotlin coverage. Factory calls/import/inheritance/parameterization closure, RapydScript/other embedded runners, fixture resolution and all executable mappings remain open; calibre implementation remains sequenced after full Pandoc.')


if __name__=='__main__':
    data=inventory()
    print(len(data['test_groups']),'groups;',len(data['registered_factory_routes']),'factory routes;',len(data['source_method_declarations']),'source methods;',len(data['directional_formats']['rows']),'directional format rows; no runtime/assertion execution')
