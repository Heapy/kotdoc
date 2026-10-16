#!/usr/bin/env python3
"""Capture original Tasty registration only; no assertion or resource action runs."""
import argparse
from collections import Counter
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import tarfile

ROOT=Path(__file__).resolve().parents[1]
CAPTURE=ROOT/'docs/test-inventory-runtime.json'
PROFILE_PROJECT='tools/haskell-html-reader-reference/cabal.project'
PROFILE_FREEZE='tools/haskell-html-reader-reference/cabal.project.freeze'


def digest(data):return hashlib.sha256(data).hexdigest()


def original_sources():
    result={}
    for folder in ['upstream/pandoc','upstream/pandoc-types']:
        pin=subprocess.check_output(['git','rev-parse','HEAD:'+folder],cwd=ROOT,text=True).strip()
        head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/folder,text=True).strip()
        if head!=pin:raise ValueError('Runtime source checkout differs from root pin')
        paths=[]
        listing=subprocess.check_output(['git','ls-tree','-rz','HEAD'],cwd=ROOT/folder)
        for row in listing.split(b'\0'):
            if not row:continue
            metadata,path=row.split(b'\t',1);mode,kind,blob=metadata.split();path=path.decode()
            if kind!=b'blob':raise ValueError('Unexpected nested source object')
            source=ROOT/folder/path
            data=os.readlink(source).encode() if mode==b'120000' else source.read_bytes()
            if hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()!=blob.decode():
                raise ValueError('Original source differs from pinned Git blob: '+folder+'/'+path)
            paths.append(dict(path=folder+'/'+path,mode=mode.decode(),git_blob=blob.decode(),sha256=digest(data)))
        result[folder]=dict(revision=pin,files=paths)
    return result


def path_id(components,occurrence):
    raw=json.dumps(components,ensure_ascii=False,separators=(',',':')).encode()
    return 'pandoc/runtime/'+digest(raw)+'/occurrence-'+str(occurrence)


def normalize_paths(paths,names):
    if not paths or not isinstance(paths,list) or not isinstance(names,list) or not all(isinstance(x,str) for x in names) or len(paths)!=len(names):raise ValueError('Invalid structured Tasty path inventory')
    counts=Counter();records=[]
    for components,name in zip(paths,names):
        if not isinstance(components,list) or not components or not all(isinstance(x,str) for x in components):raise ValueError('Invalid Tasty group components')
        if components[0]!='pandoc tests' or '.'.join(components)!=name:raise ValueError('Structured paths differ from original Tasty names')
        key=tuple(components);counts[key]+=1
        records.append(dict(id=path_id(components,counts[key]),components=components,display_name=name,occurrence=counts[key]))
    return records


def source_file(path):return dict(path=path,sha256=digest((ROOT/path).read_bytes()))


def validate_profile(profile):
    required=dict(compiler='ghc-9.12.2',os='osx',arch='aarch64',cabal='3.14.2.0',
                  pandoc_flags=dict(embed_data_files=True,http=True),optimization=dict(pandoc=0,project=2))
    for key,value in required.items():
        if profile.get(key)!=value:raise ValueError('Unsupported or changed runtime registration profile: '+key)
    for name,version in [('pandoc','3.12.1'),('pandoc-types','1.23.1.2'),('tasty','1.5.4')]:
        units=[x for x in profile['dependencies'] if x.get('pkg-name')==name and x.get('component-name')=='lib']
        if len(units)!=1 or units[0]['pkg-version']!=version:raise ValueError('Runtime dependency condition drift: '+name)


def capture(directory,binary,toolchain,write=False):
    directory=directory.resolve();binary=binary.resolve();observer=directory/'observer/runtime-inventory'
    if not binary.is_file() or not observer.is_file():raise ValueError('Build original test binary and structured observer first')
    sources=original_sources()
    main=(ROOT/'upstream/pandoc/test/test-pandoc.hs').read_bytes()
    packaged=(directory/'observer/InventoryOriginalMain.hs').read_bytes()
    if packaged!=main.replace(b'module Main (main) where',b'module InventoryOriginalMain (main, tests) where'):
        raise ValueError('Unexpected original Main body mutation')
    cwd=ROOT/'upstream/pandoc/test'
    binary_sha=digest(binary.read_bytes());observer_sha=digest(observer.read_bytes())
    environment={k:v for k,v in os.environ.items() if not k.startswith('TASTY_')}
    raw=subprocess.check_output([str(binary),'--list-tests'],cwd=ROOT/'upstream/pandoc',env=environment)
    structured=json.loads(subprocess.check_output([str(observer),str(cwd),str(binary)],cwd=ROOT,env=environment))
    if digest(binary.read_bytes())!=binary_sha or digest(observer.read_bytes())!=observer_sha:raise ValueError('Oracle binary changed during capture')
    records=normalize_paths(structured['paths'],structured['names'])
    reconstructed=''.join(n+'\n' for n in structured['names']).encode()
    if raw!=reconstructed:raise ValueError('Unmodified --list-tests differs from independent structured fold')
    plan=json.loads((directory/'build/cache/plan.json').read_text())
    tasty=next(x for x in plan['install-plan'] if x.get('pkg-name')=='tasty' and x.get('component-name')=='lib')
    if tasty['pkg-version']!='1.5.4' or plan['compiler-id']!='ghc-9.12.2':raise ValueError('Unexpected pinned Tasty/compiler profile')
    test=next(x for x in plan['install-plan'] if x.get('pkg-name')=='pandoc' and x.get('component-name')=='test:test-pandoc')
    # Both actual binaries compiled against exactly this plan's original units.
    if Path(test['pkg-src']['path']).resolve()!=ROOT/'upstream/pandoc':raise ValueError('Test plan uses another Pandoc source')
    dependencies=[{k:v for k,v in unit.items() if k in ['id','pkg-name','pkg-version','component-name','flags','depends','pkg-src-sha256','pkg-cabal-sha256']} for unit in plan['install-plan']]
    tasty_archive=toolchain/'cabal-state/packages/hackage.haskell.org/tasty/1.5.4/tasty-1.5.4.tar.gz'
    with tarfile.open(tasty_archive) as archive:
        semantics={name:digest(archive.extractfile('tasty-1.5.4/'+name).read()) for name in ['Test/Tasty/Ingredients/ListTests.hs','Test/Tasty/Core.hs','LICENSE']}
    result=dict(schema_version=1,profile=dict(compiler=plan['compiler-id'],os=plan['os'],arch=plan['arch'],cabal=plan['cabal-version'],pandoc_flags=test['flags'],optimization=dict(pandoc=0,project=2),filter='none; --list-tests only; default OptionSet; TASTY_* environment variables removed from both subprocesses',dependencies=dependencies),
                original_sources=sources,
                original_binary_sha256=binary_sha,observer_binary_sha256=observer_sha,
                original_list_stdout_sha256=digest(raw),original_list_stdout_bytes=len(raw),
                observer_source=source_file('tools/test-inventory-runtime-reference/RuntimeInventory.hs'),
                preparation_source=source_file('tools/prepare-test-inventory-runtime.py'),
                profile_sources=[source_file(PROFILE_PROJECT),source_file(PROFILE_FREEZE)],
                tasty_semantics=dict(version='1.5.4',archive_sha256=digest(tasty_archive.read_bytes()),members=semantics,
                                     contract='listingTests folds names then putStrLn; structured fold uses trivialFold and never invokes foldSingle test values or resource actions.'),
                paths=structured['paths'],names=structured['names'],leaves=len(records),
                execution_conditions=dict(original_binary_cwd='upstream/pandoc (Main changes into test)',observer_tree_cwd='upstream/pandoc/test',test_filter='none',test_assertions_executed=False),
                execution_scope='Registration only. Original --list-tests and export-only structured fold; no original test assertions or Kotlin conversion executes.',
                source_main_packaging='Only module Main(main) -> InventoryOriginalMain(main,tests); every remaining original byte is preserved.',
                ordering='Original process order retained; Command directory enumeration order is filesystem-dependent. IDs derive from exact JSON path components plus duplicate occurrence, not global ordinal or dotted display names.')
    validate_profile(result['profile'])
    if write:CAPTURE.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    return result


def runtime_inventory(command,old):
    captured=json.loads(CAPTURE.read_text())
    validate_profile(captured['profile'])
    if captured['original_sources']!=original_sources():raise ValueError('Runtime original source inventory drift')
    if captured['observer_source']!=source_file('tools/test-inventory-runtime-reference/RuntimeInventory.hs') or captured['preparation_source']!=source_file('tools/prepare-test-inventory-runtime.py'):
        raise ValueError('Runtime observer/preparation source drift')
    if captured['profile_sources']!=[source_file(PROFILE_PROJECT),source_file(PROFILE_FREEZE)]:raise ValueError('Runtime build profile source drift')
    records=normalize_paths(captured['paths'],captured['names'])
    raw=''.join(n+'\n' for n in captured['names']).encode()
    if captured['leaves']!=len(records) or captured['original_list_stdout_sha256']!=digest(raw) or captured['original_list_stdout_bytes']!=len(raw):raise ValueError('Runtime name serialization/count drift')
    c_index={('pandoc tests','Command:',Path(r['source']['path']).name,r['id'].rsplit('/',1)[1]):r['id'] for r in command}
    old_index={};old_names=Counter()
    for row in old:
        key=tuple(['pandoc tests','Old']+row['groups']+[row['name']])
        old_names[key]+=1
        old_index[(key,old_names[key])]=row['id']
    main=(ROOT/'upstream/pandoc/test/test-pandoc.hs').read_text()
    module_roots={('pandoc tests','Command:'):'Tests.Command'}
    for match in re.finditer(r'testGroup "([^"]+)"\s+\(?\s*(Tests(?:\.\w+)+)\.tests',main):
        name,module=match.groups()
        category=module.split('.')[1]
        prefix=('pandoc tests',category,name) if category in ('Readers','Writers') else ('pandoc tests',name)
        module_roots[prefix]=module
    command_refs=[];old_refs=[]
    for r in records:
        components=r['components'];key=tuple(components);ref=None
        if len(components)>1 and components[1]=='Command:':
            if key not in c_index:raise ValueError('Runtime Command leaf differs from independently parsed source block')
            ref=c_index[key];command_refs.append(ref)
        elif len(components)>1 and components[1]=='Old':
            if (key,r['occurrence']) not in old_index:raise ValueError('Runtime Old tree differs from source helper expansion')
            ref=old_index[(key,r['occurrence'])];old_refs.append(ref)
        roots=[(prefix,module) for prefix,module in module_roots.items() if tuple(components[:len(prefix)])==prefix]
        if len(roots)!=1:raise ValueError('Runtime leaf lacks a unique original main-module registration root')
        module=roots[0][1]
        origin=source_file('upstream/pandoc/test/'+module.replace('.','/')+'.hs')
        r.update(kind='runtime-registration-leaf',source_module_root=dict(module=module,**origin),source_registration_ref=ref,status='unmapped',kotlin=None,
                 expectation_origin='Original pinned test tree and source assertions; complete source catalog is in the registration capture. Exact generated-leaf-to-expression attribution remains pending where source_registration_ref is null.',
                 execution_command='Original test-pandoc --list-tests (registration only, not assertion execution)',
                 gap='No executable Kotlin assertion mapping. One finite build/OS/feature profile; other conditional profiles and detailed expectation attribution remain required.')
    if Counter(command_refs)!=Counter(r['id'] for r in command):raise ValueError('Runtime Command registration multiplicity mismatch')
    if Counter(old_refs)!=Counter(r['id'] for r in old):raise ValueError('Runtime Old helper expansion/multiplicity mismatch')
    return dict(capture=source_file(str(CAPTURE.relative_to(ROOT))),profile=captured['profile'],leaves=sorted(records,key=lambda r:r['id']),
                duplicate_paths=[dict(components=list(path),count=count) for path,count in Counter(tuple(r['components']) for r in records).items() if count>1],
                scope='Concrete runtime registration census for the captured profile; replaces lexical site counts for multiplicity, not source expectation or Kotlin coverage.',
                gap='Remaining exact assertion/expectation attribution, executable Kotlin mappings, other build/OS profiles, dependency embedded suites and calibre directional inventory remain open.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,required=True)
    parser.add_argument('--binary',type=Path,required=True)
    parser.add_argument('--toolchain',type=Path,default=Path('/tmp/kotdoc-haskell'))
    action=parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--capture',action='store_true');action.add_argument('--verify',action='store_true')
    args=parser.parse_args()
    result=capture(args.directory,args.binary,args.toolchain,write=args.capture)
    if args.verify:
        expected=json.loads(CAPTURE.read_text())
        # Machine-linked executable hashes may differ after a clean rebuild;
        # this replay verifies all registration data, sources and build conditions.
        for value in [result,expected]:
            value['canonical_registrations']=sorted(normalize_paths(value.pop('paths'),value.pop('names')),key=lambda row:row['id'])
            for field in ['original_binary_sha256','observer_binary_sha256','original_list_stdout_sha256']:value.pop(field)
            value['profile']['dependencies']=sorted(value['profile']['dependencies'],key=lambda unit:unit['id'])
        if result!=expected:raise ValueError('Original runtime registration replay differs')
    print('Captured' if args.capture else 'Verified',result['leaves'],'runtime registrations; no tests executed')
