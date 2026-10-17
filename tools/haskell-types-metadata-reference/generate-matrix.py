#!/usr/bin/env python3
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
rows=[json.loads(x)for x in(HERE/'requests.jsonl').read_text().splitlines()]
slots=[]
def add(id,source,kotlin,predicate):
 cases=[r['case']for r in rows if predicate(r)];assert cases,id
 slots.append(dict(id=id,exact_source=source,kotlin=kotlin,original_cases=cases))
for kind,line in [('meta',277),('blocks',280),('inlines',283),('bool',286),('text',289),('string',292),('list',295),('mapText',298),('mapString',301)]:
 add('ToMetaValue/'+kind,'Builder.hs:'+str(line),'MetadataConversions.'+kind,lambda r,k=kind:r['operation']=='convert'and r['spec']['type']==k)
for carrier in ['Meta','Pandoc']:
 for name in ['setMeta','deleteMeta']:
  add('HasMeta/'+carrier+'/'+name,'Builder.hs:'+str(308 if carrier=='Meta'else 312),'PandocMetadata.'+carrier.lower()+'.'+name,lambda r,o=name+carrier:r['operation']==o)
for carrier in ['Meta','Pandoc']:
 for cls,method,op in [('Semigroup','<>','Append'),('Semigroup','sconcat','Sconcat'),('Semigroup','stimes','Stimes'),('Monoid','mempty','Empty'),('Monoid','mappend','Mappend'),('Monoid','mconcat','Concat')]:
  add(cls+'/'+carrier+'/'+method,'Definition.hs:'+str((118 if cls=='Semigroup'else 122)if carrier=='Meta'else(107 if cls=='Semigroup'else 110))+'; OriginalBase.iface.txt defaults; OriginalDefinition.iface.txt specialized demand','MetadataAlgebra.'+carrier.lower()+'.'+('append'if method=='<>'else 'empty'if method=='mempty'else method),lambda r,o=carrier.lower()+op:r['operation']==o)
helpers=[]
for method,file,line in [('nullMeta','Definition',134),('isNullMeta','Definition',137),('lookupMeta','Definition',143),('docTitle','Definition',147),('docAuthors','Definition',158),('docDate','Definition',170),('doc','Builder',271),('setTitle','Builder',318),('setAuthors','Builder',321),('setDate','Builder',324)]:
 cases=[r['case']for r in rows if r['operation']==method];assert cases
 helpers.append(dict(id=method,exact_source=f'{file}.hs:{line}',kotlin='PandocMetadata.'+method,original_cases=cases))
assert len(slots)==25 and len(helpers)==10
(HERE/'acceptance-matrix.json').write_text(json.dumps(dict(schema=1,instance_dictionaries=15,instance_method_slots=25,public_helpers=10,slots=slots,helpers=helpers,source_original_tests='test-pandoc-types.hs has JSON metadata examples; no standalone ToMetaValue/HasMeta/metadata algebra property suite. Independent native dictionaries supply direct behavior/demand proof.',remaining='Other generic Many/count classes, remaining Builder exports, Data.Version, JSONfilter, and full140exports/350expanded-instance audit remain open.'),indent=2)+'\n')
