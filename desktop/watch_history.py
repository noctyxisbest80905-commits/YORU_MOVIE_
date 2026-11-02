"""Private desktop history and player progress; no web server or remote telemetry.

mpv writes per-session JSON using its Lua lifecycle hooks. A detached helper polls
only the loopback, password-protected HTTP interface of the VLC instance we start.
The helper never writes the SQLite database, so it can outlive the main window.
"""
from __future__ import annotations
import base64
import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import socket
import sqlite3
import time
import urllib.parse
import urllib.request

PROVIDERS = {'moviebox','fourkhdhub','dramachi','bdix_circleftp','bdix_dhakaflix'}
MAX_SECONDS = 7 * 24 * 3600
MPV_LUA = r'''local utils = require 'mp.utils'
local out = mp.get_script_file():gsub('%.lua$', '.json')
local pos, dur, seen, ended = 0, 0, false, false
local function save()
    if not seen then return end
    local data = utils.format_json({position=pos,duration=dur,completed=ended,timestamp=os.time(),observed=true})
    if not data then return end
    local tmp=out..'.tmp'
    local f=io.open(tmp,'wb')
    if f then f:write(data);f:close();os.remove(out);os.rename(tmp,out) end
end
mp.observe_property('time-pos','number',function(_,v) if v then pos=math.max(0,v);seen=true end end)
mp.observe_property('duration','number',function(_,v) if v then dur=math.max(0,v) end end)
mp.observe_property('pause','bool',function(_,v) if v then save() end end)
mp.register_event('end-file',function(e) if e.reason=='eof' then ended=true end;save() end)
mp.register_event('shutdown',save)
mp.add_periodic_timer(3,save)
'''

def bounded_number(value, maximum=MAX_SECONDS):
    if isinstance(value, bool) or not isinstance(value, (int,float)) or not math.isfinite(value) or not 0 <= value <= maximum:
        raise ValueError('Invalid playback position')
    return int(value)

def media_hash(url):
    return hashlib.sha256(urllib.parse.unquote(url).encode('utf-8')).hexdigest()

def atomic_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp=path.with_suffix(path.suffix+'.tmp')
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(data,f,ensure_ascii=False)
    try: os.chmod(tmp,0o600)
    except OSError: pass
    os.replace(tmp,path)

class History:
    def __init__(self, folder: Path):
        self.folder=folder
        self.sessions=folder/'playback'
        self.database=folder/'watch-history.sqlite3'

    def _db(self):
        self.folder.mkdir(parents=True,exist_ok=True)
        conn=sqlite3.connect(self.database,timeout=5)
        conn.execute('CREATE TABLE IF NOT EXISTS history (key TEXT PRIMARY KEY, session TEXT NOT NULL, data TEXT NOT NULL)')
        return conn

    def prepare(self,payload):
        item=payload.get('item');ctx=payload.get('context')
        if not isinstance(item,dict) or not isinstance(item.get('id'),dict) or not isinstance(ctx,dict):
            raise ValueError('Playback title and episode context are required. Update the full desktop package.')
        ident=item['id']; provider=ident.get('provider'); value=ident.get('value')
        if provider not in PROVIDERS or not isinstance(value,str) or not 0 < len(value) <= 512 or not isinstance(item.get('title'),str) or not 0<len(item['title'])<=1000:
            raise ValueError('Invalid history title')
        if ctx.get('provider')!=provider or not isinstance(ctx.get('id'),str) or not 0<len(ctx['id'])<=512:
            raise ValueError('Invalid playback edition')
        context={'provider':provider,'id':ctx['id'],'season':bounded_number(ctx.get('season',0),10000),'episode':bounded_number(ctx.get('episode',0),10000)}
        position=bounded_number(payload.get('resume_seconds',0))
        player=payload.get('player')
        if player not in {'mpv','vlc','iina'}: raise ValueError('Unsupported player')
        key=hashlib.sha256(json.dumps([provider,value,context['season'],context['episode']],ensure_ascii=False).encode()).hexdigest()
        session=secrets.token_hex(16)
        clean={k:item.get(k) for k in ['id','title','media_type','year','poster_url','season_count']}
        clean['id']={'provider':provider,'value':value}
        release=payload.get('release',{})
        if not isinstance(release,dict):raise ValueError('Invalid release')
        audio=payload.get('audio_language','')
        if audio not in {'','hin','eng','tam','tel','ben','mal','kan','mar','pan','urd'}:raise ValueError('Invalid audio language')
        selection={k:str(release.get(k) or '')[:512] for k in ['filename','quality','language','resource_id']}
        entry={'key':key,'item':clean,'context':context,'audio_language':audio,'player':player,'selection':selection,'position':0,'duration':0,'completed':False,'updated_at':int(time.time()),'tracking':'waiting','session':session}
        self.sessions.mkdir(parents=True,exist_ok=True)
        tracking={}
        if player=='mpv':
            script=self.sessions/(session+'.lua');script.write_text(MPV_LUA,encoding='utf-8');tracking={'mpv_script':str(script)}
        elif player=='vlc':
            with socket.socket() as sock:
                sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
            tracking={'vlc_port':port,'vlc_password':secrets.token_urlsafe(32)}
        return entry,tracking,position

    def register(self,entry):
        conn=self._db()
        try:
            with conn:
                old=conn.execute('SELECT data FROM history WHERE key=?',(entry['key'],)).fetchone()
                if old:
                    prev=json.loads(old[0])
                    if prev['context']==entry['context']:
                        for key in ['position','duration','completed']:entry[key]=prev.get(key,entry[key])
                conn.execute('INSERT OR REPLACE INTO history VALUES (?,?,?)',(entry['key'],entry['session'],json.dumps(entry,ensure_ascii=False)))
                conn.execute('DELETE FROM history WHERE key IN (SELECT key FROM history ORDER BY json_extract(data,\'$.updated_at\') DESC LIMIT -1 OFFSET 200)')
        finally:conn.close()

    def read(self):
        conn=self._db();changed=[];result=[]
        try:
            for key,session,raw in conn.execute('SELECT key,session,data FROM history').fetchall():
                entry=json.loads(raw)
                path=self.sessions/(session+'.json')
                try:
                    if path.stat().st_size>16384:raise ValueError('Oversized status')
                    report=json.loads(path.read_text(encoding='utf-8'))
                    if report.get('error'):
                        entry['tracking']='unavailable'
                    elif report.get('observed') is True:
                        pos=bounded_number(report.get('position'));dur=bounded_number(report.get('duration',0))
                        stamp=bounded_number(report.get('timestamp',0),int(time.time())+60)
                        # Latest position wins, even after seeking backwards or Start over.
                        if stamp>=entry.get('last_sample',0):
                            entry.update(position=pos,duration=dur,completed=report.get('completed') is True or (dur>0 and pos>=dur*.95),updated_at=stamp,last_sample=stamp,tracking='active')
                    changed.append((json.dumps(entry,ensure_ascii=False),key,session))
                except (OSError,ValueError,TypeError,AttributeError):pass
                public={k:v for k,v in entry.items() if k not in {'session','last_sample'}}
                result.append(public)
            with conn:
                conn.executemany('UPDATE history SET data=? WHERE key=? AND session=?',changed)
        finally:conn.close()
        return sorted(result,key=lambda x:x['updated_at'],reverse=True)

    def remove(self,key=None):
        if key is not None and (not isinstance(key,str) or not re.fullmatch('[0-9a-f]{64}',key)):raise ValueError('Invalid history key')
        conn=self._db()
        try:
            with conn:
                if key is None:conn.execute('DELETE FROM history')
                else:conn.execute('DELETE FROM history WHERE key=?',(key,))
        finally:conn.close()
        return self.read()

    def cleanup(self):
        # Helpers/configs may be active; remove only files older than 30 days.
        if not self.sessions.exists():return
        for path in self.sessions.iterdir():
            try:
                if path.is_file() and path.stat().st_mtime<time.time()-30*86400:path.unlink()
            except OSError:pass

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None

def vlc_get(port,password,endpoint):
    if endpoint not in {'status.json','playlist.json'}:raise ValueError('Invalid VLC endpoint')
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    auth=base64.b64encode((':'+password).encode()).decode()
    request=urllib.request.Request(f'http://127.0.0.1:{port}/requests/{endpoint}',headers={'Authorization':'Basic '+auth})
    with opener.open(request,timeout=2) as response:
        body=response.read(1024*1024+1)
        if len(body)>1024*1024:raise ValueError('Oversized VLC response')
        return json.loads(body)

def current_uri(tree):
    if isinstance(tree,dict):
        if tree.get('current')=='current' and isinstance(tree.get('uri'),str):return tree['uri']
        for child in tree.get('children',[]):
            found=current_uri(child)
            if found:return found
    return None

def track_vlc(config_path: Path, max_wait=90, interval=3):
    """Runs as a detached, hidden helper; exits when VLC becomes unreachable."""
    cfg=json.loads(config_path.read_text(encoding='utf-8'))
    session=cfg.get('session','');port=cfg.get('port');password=cfg.get('password');expected=cfg.get('media_hash')
    if not re.fullmatch('[0-9a-f]{32}',session) or type(port)!=int or not 1024<=port<=65535 or not isinstance(password,str) or not 16<=len(password)<=128 or not isinstance(expected,str):
        raise ValueError('Invalid VLC session')
    output=config_path.parent/(session+'.json')
    last=None;first=time.monotonic();failed_since=None
    try:
        while True:
            try:
                state=vlc_get(port,password,'status.json')
                uri=current_uri(vlc_get(port,password,'playlist.json'))
                if uri and media_hash(uri)!=expected:
                    # A different file was opened in this dedicated player. Never
                    # attribute that file's progress to the original movie.
                    break
                if uri and state.get('state') in {'playing','paused'}:
                    pos=bounded_number(state.get('time'));dur=bounded_number(state.get('length',0))
                    last={'position':pos,'duration':dur,'completed':dur>0 and pos>=dur*.95,'observed':True,'timestamp':int(time.time())}
                    atomic_json(output,last);failed_since=None
                elif last is not None and state.get('state') in {'stopped','ended'}:
                    break
            except Exception:
                if failed_since is None:failed_since=time.monotonic()
                if last is not None and time.monotonic()-failed_since>=10:break
            if last is None and time.monotonic()-first>=max_wait:
                atomic_json(output,{'error':'Player progress could not be read','timestamp':int(time.time())});break
            time.sleep(interval)
    finally:
        try:config_path.unlink()
        except OSError:pass
