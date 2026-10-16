#!/usr/bin/env python3
"""Capture actual pinned Walk methods; no Kotlin participates in expectations."""
import argparse,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent

def requests():
    examples={}
    for line in (ROOT/'core/src/test/resources/pandoc-types/read-examples.jsonl').read_text().splitlines():
        row=json.loads(line);examples.setdefault(row['kind'],[]).append(row['input'])
    for a,b in [('head','tableHead'),('body','tableBody'),('foot','tableFoot')]:examples[a]=examples[b]
    examples['inlines']=['['+','.join(examples['inline'])+']','[]']
    examples['blocks']=['['+','.join(examples['block'])+']','[]']
    nested='MetaList [MetaString "outer",MetaList [MetaString "inner"],MetaInlines [Str "metadata",Note [Plain [Str "note"]]],MetaBlocks [Para [Str "block"]]]'
    examples['metaValue'].append(nested)
    meta='Meta {unMeta = fromList [("z",'+nested+'),("a",MetaString "first"),("\\65536",MetaString "astral"),("\\57344",MetaString "bmp")]}'
    examples['meta'].append(meta)
    examples['pandoc'].append('Pandoc ('+meta+') '+examples['blocks'][0])
    def cell(label):
        return 'Cell ("",[],[]) AlignDefault (RowSpan 1) (ColSpan 1) [Plain [Str "'+label+'",Emph [Str "'+label+'nested"]],BlockQuote [Plain [Str "'+label+'quote"]]]'
    def row(label):
        return 'Row ("",[],[]) ['+cell(label+'left')+','+cell(label+'right')+']'
    head='TableHead ("",[],[]) ['+row('head1')+','+row('head2')+']'
    body='TableBody ("",[],[]) (RowHeadColumns 1) ['+row('bodyhead1')+','+row('bodyhead2')+'] ['+row('body1')+','+row('body2')+']'
    foot='TableFoot ("",[],[]) ['+row('foot1')+','+row('foot2')+']'
    caption='Caption (Just [Str "short",Emph [Str "shortnested"]]) [Plain [Str "long"],BlockQuote [Para [Str "longnested"]]]'
    table='Table ("",[],[]) ('+caption+') [] ('+head+') ['+body+'] ('+foot+')'
    figure='Figure ("",[],[]) ('+caption+') [Plain [Str "figure"]]'
    cite='Cite ['+examples['citation'][0]+'] [Str "cite",Note ['+table+','+figure+']]'
    for key,value in [('cell',cell('cell')),('row',row('row')),('head',head),('body',body),('foot',foot),('caption',caption),('block',table),('block',figure),('inline',cite)]:
        examples[key].append(value)
    examples['inlines'].append('['+cite+',Str "aftercite"]')
    examples['blocks'].append('['+table+','+figure+']')
    examples['metaValue'].append('MetaBlocks ['+table+','+figure+']')
    examples['pandoc'].append('Pandoc ('+meta+') ['+table+','+figure+']')
    examples['caption'].append('Caption Nothing []')
    examples['meta'].append('Meta {unMeta = fromList []}')
    examples['metaValue'].extend(['MetaMap (fromList [])','MetaList []','MetaInlines []','MetaBlocks []'])
    examples['metaValuePrime']=examples['metaValue']
    inline='Emph [Str "left",Note [Plain [Str "inside"]],Str "right"]'
    block='BlockQuote [Plain [Str "left"],Para [Emph [Str "inside"]],Plain [Str "right"]]'
    generic={
        'pairMetaBlockMixed':['('+meta+','+block+')'],
        'manyInlines':['Many {unMany = fromList []}','Many {unMany = fromList [Str "a",Emph [Str "b"],Note [Plain [Str "c"]]]}'],
        'manyBlocks':['Many {unMany = fromList []}','Many {unMany = fromList [Plain [Str "a"],BlockQuote [Para [Str "b"]]]}'],
        'seqInlines':['fromList [[Str "a"],[],[Emph [Str "b"]]]'],
        'pairInlinesBlocks':['([Str "a"],[Plain [Str "b"]])'],
        'maybeBlock':['Nothing','Just ('+block+')'],
        'identityBlock':['Identity ('+block+')'],
        'seqBlock':['fromList []','fromList ['+block+',Plain [Str "second"]]'],
        'mapBlock':['fromList [("z",'+block+'),("a",Plain [Str "first"])]'],
        'nestedBlock':['Just (fromList ['+block+'])','Nothing'],
        'pairInlineBlock':['('+inline+','+block+')'],
        'pairMetaPandoc':['('+meta+',Pandoc ('+meta+') ['+block+'])'],
        'pairPandoc':['(Pandoc ('+meta+') [],Pandoc ('+meta+') ['+block+'])'],
    }
    for filename,flag in [('concrete-dictionaries.json',None),('helper-contexts.json','helper'),('generic-contexts.json','generic')]:
        for dictionary in json.loads((HERE/filename).read_text()):
            for index,input in enumerate((generic if flag=='generic' else examples)[dictionary['root']]):
                for method in ['walk','walkM','failure','branching','query','first','last','count']:
                    row={k:dictionary[k] for k in ['target','root']}
                    row.update(method=method,input=input,stop=1)
                    if flag:row[flag]=True
                    row['id']=f"{flag or 'dictionary'}/{row['target']}/{row['root']}/{index}/{method}"
                    yield row

    for probe in ["list-prefix-bottom-inline","list-prefix-bad-tail","list-prefix-nested","unit-ignores-callback","first-bottom-inline","first-constant-bad-head","first-bad-tail","last-bad-head","last-constant-bad-head","meta-skips-blocks","meta-value-skips-blocks","meta-skips-block-elements","meta-value-skips-block-elements","pandoc-constant-bottom","unit-ignores-bottom","any-ignores-bottom","all-ignores-bottom","failure-before-tail","deep-count","deep-walk","deep-state"]:
        yield dict(id="demand/"+probe,demand=True,probe=probe)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--binary',required=True);parser.add_argument('--output',required=True)
    args=parser.parse_args();rows=list(requests())
    payload=''.join(json.dumps(r,ensure_ascii=True,separators=(',',':'))+'\n' for r in rows)
    result=subprocess.run([args.binary],input=payload,text=True,capture_output=True,check=True)
    responses=result.stdout.splitlines();assert len(rows)==len(responses),(len(rows),len(responses),result.stderr)
    out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
    (out/'walk-requests.jsonl').write_text(payload)
    (out/'walk-responses.jsonl').write_text(result.stdout)
    errors=[(r['id'],json.loads(s)) for r,s in zip(rows,responses) if not r.get('demand') and isinstance(json.loads(s),dict) and 'exception' in json.loads(s)]
    assert not errors,errors[:10]
    print(f'{len(rows)} original responses captured')
if __name__=='__main__':main()
