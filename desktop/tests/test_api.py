import importlib.util
import sys
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parents[1]))

SPEC = importlib.util.spec_from_file_location('desktop_app', Path(__file__).parents[1] / 'app.py')
app = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(app)
ITEM = {'id': {'provider': 'moviebox', 'value': 'test-001'}, 'title': 'Test title',
        'media_type': 'movie', 'year': '2026', 'poster_url': None, 'season_count': None}

class ApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.backend = self.root / 'backend.exe'
        self.backend.touch()
        self.api = app.Api(self.backend, self.root / 'storage' / 'favorites.json')

    def test_favorites_empty(self):
        self.assertEqual(self.api.dispatch('favorites'), {'ok': True, 'data': []})

    def test_favorites_toggle_and_persist(self):
        result = self.api.dispatch('toggle_favorite', {'item': ITEM})
        self.assertEqual(result['data'][0]['title'], 'Test title')
        other = app.Api(self.backend, self.api.storage)
        self.assertEqual(other.dispatch('favorites')['data'], result['data'])
        self.assertEqual(other.dispatch('toggle_favorite', {'item': ITEM})['data'], [])

    def test_invalid_favorite_cannot_be_saved(self):
        self.assertFalse(self.api.dispatch('toggle_favorite', {'item': {'title':'bad'}})['ok'])
        self.assertFalse(self.api.storage.exists())

    def test_corrupt_favorites_preserved(self):
        self.api.storage.parent.mkdir()
        self.api.storage.write_text('not json')
        self.assertFalse(self.api.dispatch('toggle_favorite', {'item': ITEM})['ok'])
        self.assertEqual(self.api.storage.read_text(), 'not json')

    def test_settings_persist(self):
        self.assertEqual(self.api.dispatch('settings')['data'], {'player': '', 'audio_language': '', 'quality': 'auto', 'hide_cam': False})
        self.assertTrue(self.api.dispatch('save_settings', {'player': 'mpv'})['ok'])
        self.assertEqual(self.api.dispatch('settings')['data']['player'], 'mpv')

    def test_settings_reject_unknown_player(self):
        self.assertFalse(self.api.dispatch('save_settings', {'player': 'cmd.exe'})['ok'])

    def test_audio_and_quality_settings_persist(self):
        self.assertTrue(self.api.dispatch('save_settings', {'audio_language': 'hin', 'quality': '1080'})['ok'])
        self.assertTrue(self.api.dispatch('save_settings', {'player': 'vlc'})['ok'])
        self.assertEqual(self.api.dispatch('settings')['data'], {'player': 'vlc', 'audio_language': 'hin', 'quality': '1080', 'hide_cam': False})

    def test_bad_audio_and_quality_rejected(self):
        self.assertFalse(self.api.dispatch('save_settings', {'audio_language': '--bad'})['ok'])
        self.assertFalse(self.api.dispatch('save_settings', {'quality': 'invalid'})['ok'])

    def test_old_settings_migrate_on_read(self):
        self.api.storage.parent.mkdir()
        self.api.storage.with_name('settings.json').write_text('{"player":"mpv"}')
        self.assertEqual(self.api.dispatch('settings')['data'], {'player': 'mpv', 'audio_language': '', 'quality': 'auto', 'hide_cam': False})

    def test_hide_cam_persists_and_is_validated(self):
        self.assertTrue(self.api.dispatch('save_settings', {'hide_cam': True})['ok'])
        self.assertTrue(self.api.dispatch('settings')['data']['hide_cam'])
        self.assertFalse(self.api.dispatch('save_settings', {'hide_cam': 'yes'})['ok'])
        self.assertTrue(self.api.dispatch('settings')['data']['hide_cam'])

    def test_unknown_action(self):
        with patch.object(app.subprocess, 'Popen') as popen:
            self.assertFalse(self.api.dispatch('run_command')['ok'])
            popen.assert_not_called()

    def test_missing_backend(self):
        self.backend.unlink()
        self.assertIn('backend is missing', self.api.dispatch('players')['error'])

    def test_wrong_payload(self):
        self.assertFalse(self.api.dispatch('search', ['not a dict'])['ok'])

    def test_no_shell_and_json_protocol(self):
        with patch.object(app.subprocess, 'Popen') as popen:
            child = popen.return_value
            child.communicate.return_value = (b'{"ok":true,"data":[]}', b'')
            child.returncode = 0
            result = self.api.dispatch('search', {'query': 'hello & echo bad', 'provider':'moviebox'})
            self.assertTrue(result['ok'])
            args, kwargs = popen.call_args
            self.assertEqual(args[0], [str(self.backend)])
            self.assertNotIn('shell', kwargs)
            request = json.loads(child.communicate.call_args.args[0])
            self.assertEqual(request['payload']['query'], 'hello & echo bad')

    def test_timeout_kills_backend(self):
        with patch.object(app.subprocess, 'Popen') as popen:
            child = popen.return_value
            child.communicate.side_effect = [subprocess.TimeoutExpired('test', 115), (b'', b'')]
            self.assertIn('timed out', self.api.dispatch('players')['error'])
            child.kill.assert_called_once()
            self.assertFalse(self.api._children)

    def test_backend_crash(self):
        with patch.object(app.subprocess, 'Popen') as popen:
            child = popen.return_value
            child.communicate.return_value = (b'', b'private details')
            child.returncode = 1
            self.assertIn('exited unexpectedly', self.api.dispatch('players')['error'])

    def test_invalid_json_response(self):
        with patch.object(app.subprocess, 'Popen') as popen:
            child = popen.return_value
            child.communicate.return_value = (b'not json', b'')
            child.returncode = 0
            self.assertFalse(self.api.dispatch('players')['ok'])

    def test_closing_kills_requests(self):
        with patch.object(app.subprocess, 'Popen') as popen:
            self.api._children.add(popen.return_value)
            self.api._close()
            popen.return_value.kill.assert_called_once()
            self.assertFalse(self.api.dispatch('players')['ok'])

if __name__ == '__main__':
    unittest.main()
