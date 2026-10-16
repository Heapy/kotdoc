#!/usr/bin/env python3
"""Source-only regression checks for bounded Command/Old/Lua inventory extraction."""
import importlib.util
from pathlib import Path
import sys
import unittest
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('extract_inventory', Path(__file__).with_name('extract-test-inventory.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class InventoryExtractionTest(unittest.TestCase):
    def test_all_original_command_sources_match_independent_ast(self):
        rows = m.command_inventory()
        self.assertEqual(1870, len(rows))
        self.assertEqual(1116, len({r['source']['path'] for r in rows}))
        self.assertEqual(len(rows), len({r['id'] for r in rows}))
        self.assertTrue(all(r['status'] == 'unmapped' and r['kotlin'] is None for r in rows))

    def test_fences_comments_indent_tabs_and_nested_markers(self):
        text = '<!--\n```\nignored\n```\n-->\n ````\n % cmd\n\tinside\n```\n ````\n'
        self.assertEqual(['% cmd\n\tinside\n```'], m.fenced_blocks(text))
        with self.assertRaisesRegex(ValueError, 'Raw CR'):
            m.fenced_blocks('```\r\n')
        with self.assertRaisesRegex(ValueError, 'Unclosed'):
            m.fenced_blocks('```\na')

    def test_command_continuation_channels_status_stop_and_unicode_lines(self):
        x = m.command_parts('% pandoc \\\n-t native\nx\u2028y\n^D\n2> warning\nout\n=> 2\n.\nignored')
        self.assertEqual('pandoc  -t native', x['command'])
        self.assertEqual('x\u2028y\n', x['stdin'])
        self.assertEqual('warning\n', x['expected_stderr'])
        self.assertEqual('out\n', x['expected_stdout'])
        self.assertEqual(2, x['expected_exit_status'])
        self.assertEqual('2> warning\nout\n=> 2\n', x['expected_combined'])
        self.assertEqual('all\n', m.command_parts('%cmd\nall')['stdin'])
        self.assertEqual('', m.command_parts('%cmd\nall')['expected_combined'])
        with self.assertRaisesRegex(ValueError, 'does not begin'):
            m.command_parts('not a command\n^D')

    def test_continuation_only_command_has_empty_input_and_golden(self):
        x = m.command_parts('% cmd' + chr(92))
        self.assertEqual('cmd', x['command'])
        self.assertEqual('', x['stdin'])
        self.assertEqual('', x['expected_combined'])
        self.assertEqual('', x['expected_stdout'])
        self.assertEqual('', x['expected_stderr'])
        self.assertEqual(0, x['expected_exit_status'])
        with self.assertRaisesRegex(ValueError, 'does not begin'):
            m.command_parts('')

    def test_old_expected_and_actual_processing_are_asymmetric(self):
        rows = m.old_inventory()
        for row in rows:
            normalizer = row['normalizer']
            self.assertEqual(["UTF8.readFile via strict readFile'", normalizer], row['expected_processing'])
            self.assertEqual(['UTF8.toStringLazy stdout', normalizer, "filter (/='\\r')"], row['actual_processing'])
        self.assertTrue(any(r['normalizer'] == 'identity' for r in rows))
        self.assertTrue(any(r['normalizer'] == 'Tests.Old.ignoreBinary . formatXML' for r in rows))

    def test_computed_old_matrix_and_duplicate_registrations(self):
        rows = m.old_inventory()
        self.assertEqual(174, len(rows))
        self.assertEqual(len(rows), len({r['id'] for r in rows}))
        media = [r for r in rows if r['groups'] == ['mediawiki','writer'] and r['name'] == 'basic']
        self.assertEqual([1, 2], [r['registration_occurrence'] for r in media])
        self.assertEqual(media[0]['upstream_execution'], media[1]['upstream_execution'])
        nordics = next(r for r in rows if r['groups'] == ['latex','writer'] and r['name'] == 'nordics table')
        self.assertEqual('upstream/pandoc/test/tables/nordics.latex', nordics['expectation_origin']['path'])
        fb2 = [r for r in rows if r['groups'] == ['fb2']]
        self.assertEqual(8, len(fb2))
        self.assertTrue(all(r['normalizer'] == 'Tests.Old.ignoreBinary . formatXML' for r in fb2))
        self.assertEqual(['test-pandoc','--emulate','--quiet'], rows[0]['upstream_execution']['argv'][:3])
        self.assertTrue(all(r['status'] == 'unmapped' for r in rows))

    def test_old_parser_fails_closed(self):
        for invalid in ['[testGroup "x" []] @ extra', '[testGroup "x" []] trailing ]', '[testGroup "x" []']:
            with self.assertRaises((ValueError, IndexError)):
                m.RegistrationParser(invalid).parse()
        with self.assertRaises(KeyError):
            m.evaluate(m.RegistrationParser('unknownHelper "x"').parse(), {})

    def test_lua_registrations_distinct_from_nested_script_leaves(self):
        x = m.lua_inventory()
        self.assertEqual(4, len(x['groups']))
        self.assertEqual(48, len(x['registrations']))
        self.assertEqual(14, sum(r['kind'] == 'lua-script-suite' for r in x['registrations']))
        self.assertTrue(all(r['status'] == 'unmapped' for r in x['registrations']))


if __name__ == '__main__':
    unittest.main()
