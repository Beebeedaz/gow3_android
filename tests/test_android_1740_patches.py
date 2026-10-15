"""Android 1740 的真实 XML 配合人工 SELF，覆盖 720p 及拒绝边界。"""
import json
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest import mock

from paths import ROOT
import patches
import verify_game_patches as verify
from launch_env import derive
from patch_fixtures import make_game, RES720, TEXTURE, STARTUP, FPS120
from prepare import parse_self
from test_prepare import fixture
from texture_diagnostics import record


class Android1740Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.game, self.out = make_game(self.root, 'CUSA01740', '01.00')
        self.xml = patches.game_profile('CUSA01740')[1]
        self.patterns = list(dict.fromkeys(
            line.get('Pattern') for line in ET.parse(self.xml).getroot().iter('Line')))
        self.payloads = [bytes(0xcc if x == '??' else int(x, 16) for x in p.split())
                         for p in self.patterns]
        self.image = b'\xcc' * 32 + b''.join(p + b'\xcc' * 32 for p in self.payloads)
        self.write_image(self.image)
        self.config = self.root / 'gow3.ini'

    def write_image(self, image):
        source = fixture()[:0x200] + image
        struct.pack_into('<QQQQ', source, 32, 0x800, 0x200, len(image), len(image))
        struct.pack_into('<IIQQQQQQ', source, 128, 1, 5, 0x1000, 0, 0,
                         len(image), len(image), 0x1000)
        (self.game / 'eboot.bin').write_bytes(source)
        (self.out / 'eboot.elf').write_bytes(parse_self(source)[0])

    def launch(self, resolution='720p', fps='60', env=None):
        self.config.write_text(f'render_resolution={resolution}\nengine_fps={fps}\n')
        return derive(self.game, self.config, self.out / 'state.json', env or {},
                      out=self.out, base_dmem=7104)

    def test_720p_has_nine_writes_and_no_other_defaults(self):
        before = verify.input_digests(self.game)
        result = self.launch(env={'GOW3_DEVICE_MEM_MB': '15146'})
        report = result['patch_report']
        self.assertEqual(patches.available_patches(self.xml, '01.00'), {RES720})
        self.assertEqual(report['applied'], [RES720])
        self.assertEqual(report['skipped'], {})
        self.assertEqual((report['write_count'], report['write_bytes']), (9, 32))
        self.assertEqual(report['effective']['render_resolution'], '720p')
        self.assertEqual((result['dmem'], result['vblank']), (7104, 60))
        self.assertEqual(result['updates']['GOW3_ENGINE_FPS'], '60')
        self.assertEqual(before, verify.input_digests(self.game))
        data = record(self.root / 'diagnostics.json', report, env={})
        self.assertEqual(data['render_resolution'], '720p')

    def test_native_restart_replaces_previous_720p(self):
        first = self.launch()
        second = self.launch('native', env=first['updates'])
        self.assertEqual(second['patch_names'], [])
        self.assertEqual(second['patch_report']['reason'], 'no-patches')
        self.assertEqual(second['patch_report']['effective']['render_resolution'], 'native')
        self.assertEqual(len((self.out / 'patches.bin').read_bytes()), 24)
        self.assertEqual(json.loads((self.out / 'patch_status.json').read_text())['write_count'], 0)

    def test_unavailable_options_do_not_become_applied_or_raise_fps(self):
        result = self.launch(fps='120', env={'GOW3_PATCHES': ';'.join([TEXTURE, STARTUP])})
        self.assertEqual(result['patch_names'], [RES720])
        self.assertIn(FPS120, result['patch_report']['skipped'])
        self.assertIn(STARTUP, result['patch_report']['skipped'])
        self.assertEqual(result['updates']['GOW3_ENGINE_FPS'], '60')
        self.assertIn('engine_fps=120', self.config.read_text())
        self.assertEqual(self.launch('4K')['patch_names'], [])

    def test_budget_and_bad_last_site_leave_zero_writes(self):
        self.launch()
        result = self.launch(env={'GOW3_DEVICE_MEM_MB': '4096'})
        self.assertEqual(result['patch_report']['reason'], 'insufficient-memory-budget')
        self.assertEqual(result['patch_report']['write_count'], 0)
        for image, error in [
            (self.image.replace(self.payloads[-1], b'\xdd' * len(self.payloads[-1])),
             'signature not found'),
            (self.image + self.payloads[-1], 'signature found more than once'),
        ]:
            with self.subTest(error=error):
                self.write_image(image)
                result = self.launch()
                self.assertEqual(result['patch_report']['write_count'], 0)
                self.assertEqual(result['patch_report']['skipped'][RES720], error)
                self.assertEqual(result['patch_report']['effective']['render_resolution'], 'native')

    def test_other_versions_and_titles_are_rejected(self):
        for title, version, reason in [('CUSA01740', '01.02', 'unsupported-app-version'),
                                       ('CUSA99999', '01.00', 'unsupported-title')]:
            make_game(self.root, title, version)
            result = self.launch()
            self.assertEqual(result['patch_report']['reason'], reason)
            self.assertEqual(result['patch_report']['write_count'], 0)

    def test_external_patches_stay_disabled_even_for_reference_hash(self):
        with mock.patch.object(patches, 'REFERENCE_SHA256', patches.eboot_sha256(self.game)), \
             mock.patch.object(patches, 'external_patches') as external:
            report, writes = patches.compile_plan(self.game, self.out, [RES720],
                                                  patches_dir=self.root)
            external.assert_not_called()
        self.assertEqual(len(writes), 9)
        self.assertEqual(report['external']['reason'], 'profile-disallows-external-patches')

    def test_offline_verifier_only_audits_available_options(self):
        with mock.patch.object(verify.prepare, 'prepare'), \
             mock.patch.object(verify.link_libc, 'link'), \
             mock.patch.object(verify.link_modules, 'link'), \
             mock.patch.object(verify.content_profile, 'prepare'):
            result = verify.verify(self.game, self.out)
        self.assertEqual(result['status'], 'verified')
        self.assertEqual([p['name'] for p in result['patches']], [RES720])
        self.assertEqual(set(result['combinations']), {'native-60', '720p-60'})

    def test_android_manifests_require_the_1740_xml(self):
        expected = 'gow3port/patches/' + self.xml.name
        for profile in ('runtime', 'debug'):
            manifest = json.loads((ROOT / 'tools/android' / profile / 'runtime-manifest.json').read_text())
            self.assertIn(expected, [item['path'] for item in manifest['required_paths']])


if __name__ == '__main__':
    unittest.main()
