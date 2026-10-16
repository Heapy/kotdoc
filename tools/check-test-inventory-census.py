#!/usr/bin/env python3
"""Check source-census boundaries and original nested Lua registration snapshots."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]

def module(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'tools'/file)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

c=module('census','census-test-inventory.py')
e=module('extract','extract-test-inventory.py')


class CensusTest(unittest.TestCase):
    def fixture(self,text,action):
        with tempfile.TemporaryDirectory(prefix='kotdoc-inventory-census-') as folder:
            p=Path(folder)/'Tests'/'Synthetic.hs';p.parent.mkdir();p.write_text(text)
            return action(str(p))

    def test_all_reachable_registered_modules_have_complete_root_spans(self):
        x=c.haskell_inventory()
        self.assertEqual(60,len(x['modules']))
        self.assertEqual(62,len(x['module_registrations']))
        self.assertEqual(2391,sum(len(m['registration_sites']) for m in x['modules']))
        self.assertEqual(0,sum(len(m['cpp_directives']) for m in x['modules']))
        ids=[r['id'] for m in x['modules'] for r in m['registration_sites']]
        self.assertEqual(len(ids),len(set(ids)))
        self.assertIn('Tests.Readers.Org.Block.Table',{m['module'] for m in x['modules']})
        for m in x['modules']:
            self.assertTrue(m['root_tests_definition']['sha256'])
            self.assertTrue(all(s['status']=='unmapped' and s['kotlin'] is None for s in m['registration_sites']))

    def test_nested_comments_and_strings_are_not_registration_sites(self):
        text='''module Synthetic where
-- testCase "comment" undefined
{- testCase "outer" {- testGroup "inner" [] -} undefined -}
tests :: [TestTree]
tests = [testGroup "outer" ["real" =: input =?> "testCase fake"]]
helper = "testGroup fake"
'''
        x=self.fixture(text,c.haskell_sites)
        self.assertEqual(['outer','real'],[r['name'] for r in x['registration_sites']])
        self.assertEqual(['outer'],x['registration_sites'][1]['groups'])
        self.assertTrue(all(r['declaration']=='tests' for r in x['registration_sites']))

    def test_alias_reachability_excludes_string_and_commented_references(self):
        text='''module Synthetic where
import qualified Tests.Real as Alias
-- import qualified Tests.Fake
x = "import qualified Tests.String as Bogus; Bogus.tests"
tests = [Alias.tests] -- Tests.Fake.tests
'''
        x=self.fixture(text,c.module_edges)
        self.assertEqual(['Tests.Real'],[e['module'] for e in x])
        with self.assertRaisesRegex(ValueError,'Unresolved'):
            self.fixture('tests = [Unknown.tests]\n',c.module_edges)

    def test_cpp_is_preserved_without_selecting_a_branch(self):
        text='''tests = [
#if FLAG
 testCase "enabled" undefined
#else
 testCase "disabled" undefined
#endif
 ]
'''
        x=self.fixture(text,c.haskell_sites)
        self.assertEqual(3,len(x['cpp_directives']))
        self.assertEqual(['enabled','disabled'],[r['name'] for r in x['registration_sites']])
        self.assertTrue(all('Not inferred' in r['multiplicity'] for r in x['registration_sites']))

    def test_computed_templates_are_not_multiplied_into_runtime_leaves(self):
        text='''testTemplate :: String -> TestTree
testTemplate name = testCase name undefined
tests :: [TestTree]
tests = map testTemplate ["a","b","c"]
'''
        x=self.fixture(text,c.haskell_sites)
        self.assertEqual(2,len(x['registration_sites']))
        self.assertTrue(all(r['name']=='<dynamic-name>' for r in x['registration_sites']))
        self.assertIn('map',[m['token'] for m in x['computed_syntax_markers']])

    def test_unbalanced_or_unknown_module_root_fails_closed(self):
        with self.assertRaises(ValueError):self.fixture('tests = [testCase "x" undefined\n',c.haskell_sites)
        with self.assertRaises(ValueError):self.fixture('notTests = []\n',c.haskell_sites)
        with self.assertRaises(ValueError):c.lex_haskell('{- unfinished')
        with self.assertRaises(ValueError):c.lex_haskell('"unfinished')

    def test_invalid_lua_observer_execution_and_callback_span_are_rejected(self):
        original=c.LUA_CAPTURE
        captured=json.loads(original.read_text())
        with tempfile.TemporaryDirectory(prefix='kotdoc-lua-census-negative-') as folder:
            candidate=Path(folder)/'capture.json'
            try:
                c.LUA_CAPTURE=candidate
                x=json.loads(json.dumps(captured));x['callbacks_executed']=1
                candidate.write_text(json.dumps(x))
                with self.assertRaisesRegex(ValueError,'Invalid Lua registration capture'):
                    c.lua_leaves(e.lua_inventory())
                x=json.loads(json.dumps(captured))
                def first_test(nodes):
                    for node in nodes:
                        if node['kind']=='test':return node
                        found=first_test(node['children'])
                        if found:return found
                first_test(x['scripts'][0]['tree'])['last_line']=10**9
                candidate.write_text(json.dumps(x))
                with self.assertRaisesRegex(ValueError,'Invalid Lua callback span'):
                    c.lua_leaves(e.lua_inventory())
            finally:c.LUA_CAPTURE=original

    def test_nested_lua_trees_have_original_callback_spans_and_no_mappings(self):
        x=c.lua_leaves(e.lua_inventory())
        self.assertEqual(309,len(x['leaves']))
        self.assertEqual(14,len({r['source']['path'] for r in x['leaves']}))
        self.assertEqual(1,len(x['empty_groups']))
        self.assertEqual(309,len({r['id'] for r in x['leaves']}))
        self.assertTrue(all(r['status']=='unmapped' and r['kotlin'] is None for r in x['leaves']))
        self.assertTrue(all(r['registration_span']['first_line']<=r['registration_span']['last_line'] for r in x['leaves']))


if __name__=='__main__':unittest.main()
