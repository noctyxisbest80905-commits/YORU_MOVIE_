"""Yoru by Obscura: local WebView UI + existing Rust provider library.

Local UI and history. VLC tracking uses an authenticated loopback interface only.
"""
from __future__ import annotations
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import sqlite3
from watch_history import History, atomic_json, media_hash, track_vlc
from content_gate import ContentGate

ROOT = Path(getattr(sys, '_MEIPASS', Path(__file__).resolve().parent))
ALLOWED = {'players', 'home', 'search', 'details', 'releases', 'subtitles', 'play'}


def data_directory() -> Path:
    # Keep the legacy storage location to preserve existing favorites/settings.
    if sys.platform == 'win32':
        return Path(os.environ.get('LOCALAPPDATA', Path.home())) / 'MovieBoxDesktop'
    return Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'moviebox-desktop'


class Api:
    def __init__(self, backend: Path | None = None, storage: Path | None = None):
        name = 'moviebox-desktop-bridge' + ('.exe' if sys.platform == 'win32' else '')
        self.backend = backend or ROOT / 'bin' / name
        self.storage = storage or data_directory() / 'favorites.json'
        self._lock = threading.Lock()
        self._slots = threading.BoundedSemaphore(4)
        self._play_lock = threading.Lock()
        self._children: set[subprocess.Popen] = set()
        self._closed = False
        self._history = History(self.storage.parent)
        self._history.cleanup()
        self._content = ContentGate(self._dispatch, self.storage.with_name('blocked-content-ids.json'))

    def _favorites(self) -> list:
        if not self.storage.exists():
            return []
        try:
            data = json.loads(self.storage.read_text(encoding='utf-8'))
            if not isinstance(data, list):
                raise ValueError('Expected a list')
            return [self._item(item) for item in data]
        except (ValueError, KeyError, TypeError) as exc:
            raise ValueError('Saved favorites could not be read. The file has been left unchanged.') from exc

    @staticmethod
    def _item(item: dict) -> dict:
        if not isinstance(item, dict) or not isinstance(item.get('id'), dict):
            raise ValueError('Invalid favorite')
        if not all(isinstance(item['id'].get(k), str) for k in ('provider', 'value')):
            raise ValueError('Invalid favorite ID')
        if not isinstance(item.get('title'), str) or not item['title'].strip():
            raise ValueError('Invalid title')
        if item['id']['provider'] not in {'moviebox', 'fourkhdhub', 'dramachi', 'bdix_circleftp', 'bdix_dhakaflix'}:
            raise ValueError('Unsupported provider')
        return {k: item.get(k) for k in ('id', 'title', 'media_type', 'year', 'poster_url', 'season_count')}

    def dispatch(self, action: str, payload: dict | None = None) -> dict:
        if payload is not None and not isinstance(payload, dict):
            return {'ok': False, 'error': 'Invalid request'}
        return self._content.request(action, payload or {})

    def _dispatch(self, action: str, payload: dict | None = None) -> dict:
        """Only public WebView entry point; never execute a shell or arbitrary path."""
        try:
            if payload is None:
                payload = {}
            if not isinstance(payload, dict):
                raise ValueError('Invalid request')
            if action in {'history', 'remove_history', 'clear_history'}:
                if action == 'remove_history':
                    return {'ok': True, 'data': self._history.remove(payload.get('key', ''))}
                if action == 'clear_history':
                    if payload.get('confirm') is not True:
                        raise ValueError('Confirm clearing watch history first')
                    return {'ok': True, 'data': self._history.remove()}
                return {'ok': True, 'data': self._history.read()}
            if action in {'settings', 'save_settings'}:
                with self._lock:
                    path = self.storage.with_name('settings.json')
                    data = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
                    if not isinstance(data, dict):
                        raise ValueError('Invalid settings file')
                    defaults = {'player': '', 'audio_language': '', 'quality': 'auto', 'hide_cam': False}
                    settings = {key: data.get(key, value) for key, value in defaults.items()}
                    if action == 'settings':
                        return {'ok': True, 'data': settings}
                    settings.update({key: payload[key] for key in defaults if key in payload})
                    if settings['player'] not in {'', 'mpv', 'vlc', 'iina'}:
                        raise ValueError('Unsupported player')
                    if settings['audio_language'] not in {'', 'hin', 'eng', 'tam', 'tel', 'ben', 'mal', 'kan', 'mar', 'pan', 'urd'}:
                        raise ValueError('Unsupported audio language')
                    if not isinstance(settings['hide_cam'], bool):
                        raise ValueError('Invalid CAM preference')
                    if settings['quality'] not in {'auto', '2160', '1080', '720', '480'}:
                        raise ValueError('Unsupported quality')
                    path.parent.mkdir(parents=True, exist_ok=True)
                    temp = path.with_suffix('.tmp')
                    temp.write_text(json.dumps(settings), encoding='utf-8')
                    temp.replace(path)
                    return {'ok': True, 'data': settings}
            if action in {'favorites', 'toggle_favorite'}:
                with self._lock:
                    items = self._favorites()
                    if action == 'toggle_favorite':
                        item = self._item(payload.get('item'))
                        found = any(x['id'] == item['id'] for x in items)
                        items = [x for x in items if x['id'] != item['id']]
                        if not found:
                            items.append(item)
                        self.storage.parent.mkdir(parents=True, exist_ok=True)
                        temp = self.storage.with_suffix('.tmp')
                        temp.write_text(json.dumps(items, ensure_ascii=False), encoding='utf-8')
                        temp.replace(self.storage)
                    return {'ok': True, 'data': items}
            if action not in ALLOWED:
                raise ValueError('Unknown request')
            if not self.backend.is_file():
                raise ValueError('Desktop backend is missing. Build it using BUILD-WINDOWS.cmd, or download the complete Windows build.')
            if not self._slots.acquire(blocking=False):
                raise ValueError('Please wait for the current requests to finish.')
            play_locked = False
            try:
                if action == 'play':
                    play_locked = self._play_lock.acquire(blocking=False)
                    if not play_locked:
                        raise ValueError('A player launch is already in progress.')
                history_entry = tracking = None
                if action == 'play':
                    payload = dict(payload)
                    history_entry, tracking, resume = self._history.prepare(payload)
                    # Internal tracker options come ONLY from this controller,
                    # never from JavaScript or saved history data.
                    payload = {key: payload.get(key) for key in ('release', 'player', 'subtitle', 'audio_language')}
                    payload['resume_seconds'] = resume
                    payload['_tracking'] = tracking
                body = json.dumps({'action': action, 'payload': payload})
                if len(body.encode('utf-8')) > 1024 * 1024:
                    raise ValueError('Request too large')
                flags = subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0
                with self._lock:
                    if self._closed:
                        raise ValueError('Application is closing')
                    proc = subprocess.Popen([str(self.backend)], stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=flags)
                    self._children.add(proc)
                try:
                    out, err = proc.communicate(body.encode('utf-8'), timeout=115)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.communicate()
                    raise ValueError('Request timed out. Try another provider.')
                finally:
                    with self._lock:
                        self._children.discard(proc)
                if proc.returncode:
                    raise ValueError(f'Backend exited unexpectedly (code {proc.returncode}). Try another provider.')
                result = json.loads(out)
                if not isinstance(result, dict) or not isinstance(result.get('ok'), bool):
                    raise ValueError('Invalid backend response')
                if action == 'play' and result.get('ok'):
                    info = result.get('data', {})
                    tracked = payload['player'] in {'mpv', 'vlc'} and info.get('tracking') is True
                    if tracked:
                        try:
                            self._history.register(history_entry)
                            if payload['player'] == 'vlc':
                                session = history_entry['session']
                                path = self._history.sessions / (session + '.vlc')
                                atomic_json(path, {'session': session, 'port': tracking['vlc_port'],
                                    'password': tracking['vlc_password'], 'media_hash': media_hash(info['_media_url'])})
                                args = [sys.executable]
                                if not getattr(sys, 'frozen', False):
                                    args.append(str(Path(__file__).resolve()))
                                args += ['--track-vlc', str(path)]
                                helper_flags = (subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP) if sys.platform == 'win32' else 0
                                subprocess.Popen(args, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                    creationflags=helper_flags, start_new_session=sys.platform != 'win32')
                            message = info.get('message', 'Opened in player') + '. Watch position will be saved when the player reports it.'
                        except (OSError, ValueError, TypeError, KeyError, sqlite3.Error):
                            tracked = False
                            try:
                                atomic_json(self._history.sessions / (history_entry['session'] + '.json'), {'error': 'History tracking could not start'})
                            except OSError:
                                pass
                            message = 'Player opened, but history tracking could not start. Playback can continue; resume may not be saved.'
                    else:
                        message = info.get('message', 'Opened in player') + '. Automatic history is supported with VLC and mpv only.'
                    # Do not expose the launch URL or loopback credentials to UI.
                    return {'ok': True, 'data': {'message': message, 'tracking': tracked, 'source_host': info.get('source_host', '')}}
                return result
            finally:
                if play_locked:
                    self._play_lock.release()
                self._slots.release()
        except (OSError, ValueError, TypeError, sqlite3.Error) as exc:
            return {'ok': False, 'error': str(exc)}

    def _close(self):
        self._content.close()
        with self._lock:
            self._closed = True
            children = list(self._children)
        for child in children:
            try:
                child.kill()
            except OSError:
                pass


def main():
    import webview
    api = Api()
    window = webview.create_window('Yoru — by Obscura', str(ROOT / 'web' / 'index.html'),
        js_api=api, width=1180, height=800, min_size=(720, 560), background_color='#191919', text_select=True)
    window.events.closed += api._close
    webview.start(debug=False, private_mode=True)


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--track-vlc':
        config = Path(sys.argv[2]).resolve()
        # A helper only consumes the app's own per-session configs.
        if config.parent == (data_directory() / 'playback').resolve() and config.suffix == '.vlc':
            try:
                track_vlc(config)
            except Exception:
                pass
    else:
        main()
