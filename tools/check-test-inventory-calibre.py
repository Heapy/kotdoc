#!/usr/bin/env python3
"""Source registry regressions. Never import or execute calibre modules."""
import ast
from collections import Counter
import hashlib
import importlib.util
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('calibre_inventory',ROOT/'tools/calibre-test-inventory.py')
c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)


class CalibreInventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.data=c.inventory()

    def test_complete_groups_and_primary_factory_routes(self):
        self.assertEqual(len(self.data['test_groups']),18)
        rows=self.data['registered_factory_routes']
        self.assertEqual(len(rows),76)
        self.assertEqual(set(r['group'] for r in rows),set(self.data['test_groups']))
        self.assertEqual(len({r['id'] for r in rows}),76)
        for row in rows:
            self.assertEqual(row['status'],'unmapped');self.assertIsNone(row['kotlin'])
            self.assertIsNone(row['counting_units']['runtime_leaves'])

    def test_embedded_nonfilename_roots_not_omitted(self):
        modules={r['module'] for r in self.data['registered_factory_routes']}
        for module in ('calibre.ebooks.mobi.writer2.resources','calibre.ebooks.compression.palmdoc','calibre.ebooks.conversion.plugins.txt_input','calibre.ebooks.conversion.plugins.archive_input'):
            self.assertIn(module,modules)
        names={r['name'] for r in self.data['source_method_declarations']}
        self.assertIn('find_tests.TestProcessJpegsForAmazon.test_jpeg_with_empty_exif_segment_is_stripped',names)
        self.assertIn('find_tests.Test.test_palmdoc_compression',names)

    def test_windows_route_and_factory_kwargs_preserved(self):
        row=next(r for r in self.data['registered_factory_routes'] if r['module']=='calibre.utils.windows.wintest')
        self.assertIn('iswindows',row['conditions'])
        build=next(r for r in self.data['registered_factory_routes'] if r['module']=='calibre.test_build')
        self.assertEqual(build['call'],'find_tests(only_build=True)')
        self.assertTrue(any('return_tests=True' in r['call'] for r in self.data['registered_factory_routes']))

    def test_package_expansion_immediate_including_init_not_filename_filter(self):
        expansions=[p for f in self.data['factory_source_details'] for p in f['package_expansions']]
        self.assertEqual(sorted(len(p['source_modules']) for p in expansions),[7,8,12,14])
        for p in expansions:
            names=[m['module'].rsplit('.',1)[-1] for m in p['source_modules']]
            self.assertIn('__init__',names);self.assertNotIn('main',names)

    def test_unrecognized_primary_append_fails_closed(self):
        source="def find_tests():\n ans=[]\n a=ans.append\n if ok('misc'):\n  for x in things:\n   a(x())\n return ans\n"
        with self.assertRaisesRegex(ValueError,'append context'):c.factory_routes(source,ast.parse(source))
        source="def find_tests():\n ans=[]\n a=ans.append\n if ok('misc'):\n  a(not_imported())\n return ans\n"
        with self.assertRaisesRegex(ValueError,'Unresolved'):c.factory_routes(source,ast.parse(source))

    def test_methods_source_sites_not_loop_or_inherited_runtime_counts(self):
        source="class Base:\n def test_a(self):\n  for x in [1,2,3]:\n   self.assertEqual(x,x)\nclass Child(Base):\n pass\n"
        rows=c.method_sites('fixture.py',source,ast.parse(source))
        self.assertEqual(len(rows),1);self.assertEqual(len(rows[0]['assertion_sites']),1)
        self.assertEqual(rows[0]['id'],'calibre/source-method/fixture.py/Base.test_a/occurrence-1')

    def test_utf8_ast_spans_hash_original_bytes(self):
        text='x = "é"; y = "日本"\n'
        node=ast.parse(text).body[1]
        got=c.fragment('fixture.py',text,node)
        self.assertEqual(got['sha256'],hashlib.sha256(ast.get_source_segment(text,node).encode()).hexdigest())

    def test_directional_names_aliases_containers_and_pdf_not_conflated(self):
        f=self.data['directional_formats']
        self.assertEqual(Counter(p['direction'] for p in f['plugins']),{'input':22,'output':20})
        self.assertEqual((len(f['pandoc_readers']),len(f['pandoc_writers'])),(52,76))
        index={(r['direction'],r['format']):r for r in f['rows']}
        self.assertEqual(index['input','pdf']['relation'],'no-matching-pinned-direction-registry-route')
        self.assertEqual(index['output','pdf']['relation'],'separate-Pandoc-PDF-engine-output-route')
        self.assertEqual(index['input','txt']['relation'],'extension-heuristic-target-in-direction-registry')
        self.assertEqual(index['input','txt']['pandoc_extension_inference']['target'],'markdown')
        self.assertEqual(index['input','zip']['plugin_refs'],[])
        self.assertTrue(index['input','zip']['ui_extra'])
        self.assertTrue(index['output','zip']['plugin_refs'])
        self.assertIsNone(index['input','djvu']['pandoc_heuristic_registry_target'])
        self.assertEqual(index['input','oebzip']['plugin_refs'],[])
        self.assertEqual(index['input','oebzip']['ui_extra'],[])
        self.assertTrue(index['input','oebzip']['pipeline_archive'])
        self.assertTrue(all(r['status']=='unmapped' and r['kotlin'] is None for r in f['rows']))

    def test_dynamic_format_values_rejected(self):
        with self.assertRaises((ValueError,TypeError)):c.static_value(ast.parse('formats()',mode='eval').body)

    def test_rapydscript_is_separate_dialect_and_runner(self):
        self.assertEqual(len(self.data['separate_dialect_sources']),134)
        self.assertEqual(self.data['separate_runner']['entry_resource']['path'],'upstream/calibre/src/pyj/test.pyj')
        self.assertFalse(self.data['runtime_profile']['imports_executed'])
        self.assertFalse(self.data['runtime_profile']['test_assertions_executed'])


if __name__=='__main__':unittest.main()
