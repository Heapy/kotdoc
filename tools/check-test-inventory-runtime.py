#!/usr/bin/env python3
"""Registration boundaries/multiplicity regressions; never execute source assertions."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]

def load(name,file):
    s=importlib.util.spec_from_file_location(name,ROOT/'tools'/file)
    m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

r=load('runtime_inventory','runtime-test-inventory.py')
e=load('source_inventory','extract-test-inventory.py')


class RuntimeRegistrationTest(unittest.TestCase):
    def test_json_components_preserve_dots_newlines_and_duplicate_occurrences(self):
        paths=[['pandoc tests','a.b','c\nx'],['pandoc tests','a','b.c\nx'],['pandoc tests','a.b','c\nx']]
        rows=r.normalize_paths(paths,['.'.join(x) for x in paths])
        self.assertEqual(rows[0]['display_name'],rows[1]['display_name'])
        self.assertNotEqual(rows[0]['id'],rows[1]['id'])
        self.assertEqual([1,1,2],[x['occurrence'] for x in rows])
        self.assertNotEqual(rows[0]['id'],rows[2]['id'])
        self.assertEqual(rows[0]['id'],r.normalize_paths(paths[:1],['.'.join(paths[0])])[0]['id'])

    def test_malformed_structured_paths_and_name_serialization_fail_closed(self):
        for paths,names in [([],[]),([['wrong root','a']],['wrong root.a']),([['pandoc tests','a']],['rewritten']),([['pandoc tests',1]],['pandoc tests.1'])]:
            with self.assertRaises(ValueError):r.normalize_paths(paths,names)

    def test_source_blob_and_checkout_pin_validation_without_mutating_git(self):
        with tempfile.TemporaryDirectory(prefix='kotdoc-runtime-source-check-') as folder:
            root=Path(folder)
            expected=b'original source\n';blob=r.hashlib.sha1(b'blob '+str(len(expected)).encode()+b'\0'+expected).hexdigest()
            for sub in ['upstream/pandoc','upstream/pandoc-types']:
                (root/sub).mkdir(parents=True);(root/sub/'file.hs').write_bytes(expected)
            def git(command,**kwargs):
                if command[1]=='rev-parse':return 'pin\n' if kwargs.get('text') else b'pin\n'
                if command[1]=='ls-tree':return b'100644 blob '+blob.encode()+b'\tfile.hs\0'
                raise AssertionError(command)
            with patch.object(r,'ROOT',root),patch.object(r.subprocess,'check_output',git):
                self.assertEqual(2,len(r.original_sources()))
                (root/'upstream/pandoc/file.hs').write_bytes(b'changed')
                with self.assertRaisesRegex(ValueError,'differs from pinned Git blob'):r.original_sources()

    def test_captured_runtime_leaves_match_original_command_and_old_multiplicities(self):
        x=r.runtime_inventory(e.command_inventory(),e.old_inventory())
        self.assertTrue(x['leaves'])
        self.assertEqual(len(x['leaves']),len({a['id'] for a in x['leaves']}))
        self.assertTrue(all(a['status']=='unmapped' and a['kotlin'] is None for a in x['leaves']))
        refs=[a['source_registration_ref'] for a in x['leaves'] if a['source_registration_ref']]
        self.assertEqual(1870+174,len(refs))
        rtf=[a for a in x['leaves'] if a['components'][1:3]==['Readers','RTF']]
        self.assertEqual(16,len(rtf))
        self.assertTrue(any(a['count']>1 for a in x['duplicate_paths']))

    def test_lost_computed_source_leaf_is_not_silently_accepted(self):
        with self.assertRaisesRegex(ValueError,'Old tree differs|multiplicity mismatch'):
            r.runtime_inventory(e.command_inventory(),e.old_inventory()[:-1])
        with self.assertRaisesRegex(ValueError,'Command leaf differs|multiplicity mismatch'):
            r.runtime_inventory(e.command_inventory()[:-1],e.old_inventory())

    def test_profile_os_flags_and_dependency_conditions_fail_closed(self):
        profile=json.loads(r.CAPTURE.read_text())['profile']
        r.validate_profile(profile)
        for field,value in [('os','windows'),('compiler','ghc-unknown'),('pandoc_flags',{'embed_data_files':False,'http':True})]:
            changed=json.loads(json.dumps(profile));changed[field]=value
            with self.assertRaisesRegex(ValueError,'runtime registration profile'):r.validate_profile(changed)
        changed=json.loads(json.dumps(profile))
        next(u for u in changed['dependencies'] if u.get('pkg-name')=='tasty' and u.get('component-name')=='lib')['pkg-version']='1.0'
        with self.assertRaisesRegex(ValueError,'dependency condition drift'):r.validate_profile(changed)

    def test_capture_leaf_count_and_stdout_hash_drift_are_rejected(self):
        original=r.CAPTURE;data=json.loads(original.read_text())
        with tempfile.TemporaryDirectory(prefix='kotdoc-runtime-capture-negative-') as folder:
            target=Path(folder)/'capture.json'
            try:
                r.CAPTURE=target
                for field,value in [('leaves',-1),('original_list_stdout_sha256','0'*64)]:
                    candidate=json.loads(json.dumps(data));candidate[field]=value;target.write_text(json.dumps(candidate))
                    with self.assertRaisesRegex(ValueError,'serialization/count drift'):
                        r.runtime_inventory(e.command_inventory(),e.old_inventory())
            finally:r.CAPTURE=original


if __name__=='__main__':unittest.main()
