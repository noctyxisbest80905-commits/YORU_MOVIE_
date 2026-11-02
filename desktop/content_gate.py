"""Always-on metadata gate before provider results or player launches reach the UI."""
from __future__ import annotations
import concurrent.futures
import copy
import json
import threading
import time
from pathlib import Path
from content_safety import Blocked, POLICY, is_blocked, blocked_text, media_key, normalize, contains

class ContentGate:
    def __init__(self, backend, storage: Path):
        self.backend, self.storage = backend, storage
        self.lock = threading.RLock()
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=3, thread_name_prefix='yoru-screen')
        self.cache = {}
        try:
            saved = json.loads(storage.read_text()) if storage.exists() else []
            self.denied = set(x for x in saved if isinstance(x, str)) if isinstance(saved,list) else set()
        except (OSError, ValueError): self.denied = set()

    def close(self): self.pool.shutdown(wait=False, cancel_futures=True)
    def guard(self, value, anime=False):
        key = media_key(value)
        ctx = value.get('context', {}) if isinstance(value, dict) else {}
        context_key = str(ctx.get('provider',''))+'\n'+str(ctx.get('id','')) if isinstance(ctx,dict) and ctx.get('id') else ''
        with self.lock:
            bad = is_blocked(value, anime) or bool(key and key in self.denied) or bool(context_key and context_key in self.denied)
            if bad:
                if key:
                    self.denied.add(key)
                    self.cache.pop(key, None)
                    try:
                        self.storage.parent.mkdir(parents=True,exist_ok=True)
                        temporary=self.storage.with_suffix('.tmp')
                        temporary.write_text(json.dumps(sorted(self.denied)[-5000:]))
                        temporary.replace(self.storage)
                    except OSError: pass # The in-memory gate still denies it.
                raise Blocked()

    def local(self, values):
        clean=[]
        for value in values:
            try: self.guard(value)
            except Blocked: pass
        for value in values:
            try: self.guard(value);clean.append(value)
            except Blocked: pass
        return clean

    @staticmethod
    def context(value):
        id=value.get('id') if isinstance(value,dict) else None
        if not isinstance(id,dict) or not id.get('provider') or not id.get('value'): raise ValueError(POLICY['unverified'])
        return {'provider':id['provider'],'id':id['value']}

    def details(self, p):
        key=str(p.get('provider',''))+'\n'+str(p.get('id',''))
        with self.lock:
            if key in self.denied: raise Blocked()
            cached=self.cache.get(key)
            if cached and time.monotonic()-cached[0]<300: return copy.deepcopy(cached[1])
        response=self.backend('details',{'provider':p.get('provider'),'id':p.get('id')})
        value=response.get('data') if isinstance(response,dict) and response.get('ok') else None
        if not isinstance(value,dict) or media_key(value)!=key or not value.get('title'): raise ValueError(POLICY['unverified'])
        self.guard(value)
        if not any(value.get(k) for k in ('description','synopsis','summary','tagline','genres','genre','tags')): raise ValueError(POLICY['unverified'])
        # Dubs/related items must not reintroduce an explicitly labelled edition.
        value=copy.deepcopy(value)
        for field in ('dubs','related','recommendations'):
            if isinstance(value.get(field),list): value[field]=self.local(value[field])
        with self.lock:
            if len(self.cache)>=500:self.cache.pop(next(iter(self.cache)))
            self.cache[key]=(time.monotonic(),value)
        return copy.deepcopy(value)

    def catalog(self, values, anime=False):
        values=self.local(values)
        def verify(value):
            try: self.guard(value,anime);self.details(self.context(value));self.guard(value,anime);return True
            except (ValueError,OSError,TypeError,KeyError): return False
        jobs={self.pool.submit(verify,item):i for i,item in enumerate(values[:100])}
        permitted=set()
        try:
            for future in concurrent.futures.as_completed(jobs,timeout=30):
                if future.result():permitted.add(jobs[future])
        except concurrent.futures.TimeoutError: pass
        finally:
            for job in jobs:job.cancel()
        return self.local([item for i,item in enumerate(values) if i in permitted])

    @staticmethod
    def release_key(value):
        return tuple(str(value.get(k,'')) for k in ('provider','resource_id','filename','quality'))

    @staticmethod
    def featured_subset(values, query):
        names={'bleach','attack on titan','sword art online','dragon ball z','one piece','naruto','demon slayer','jujutsu kaisen'}
        q=normalize(query)
        if q not in {normalize(x) for x in names}: return []
        result=[]
        for item in values:
            if not isinstance(item,dict): continue
            title=normalize(item.get('title',''))
            # The provider labels its live-action One Piece series as 2023/Netflix.
            # Exclude these obvious adaptations; other entries may remain ambiguous.
            if q==normalize('One Piece') and (str(item.get('year',''))=='2023' or 'netflix' in title): continue
            if title==q or title.startswith(q+' '): result.append(item)
            if len(result)>=4:break
        return result

    def request(self, action, payload):
        p=copy.deepcopy(payload)
        try:
            if action=='search' and (blocked_text(p.get('query','')) or contains(p.get('query',''),'harem')):raise Blocked()
            if action=='details':return {'ok':True,'data':self.details(p)}
            if action in {'play','toggle_favorite'}:
                self.guard(p.get('item',{}));self.guard(p.get('release',{}))
            if action in {'releases','subtitles'}:self.details(p)
            if action=='play':
                context=p.get('context')
                if not isinstance(context,dict):raise ValueError(POLICY['unverified'])
                self.details(context)
                releases=self.request('releases',context)
                if not releases.get('ok'):raise ValueError(POLICY['unverified'])
                incoming=p.get('release')
                if not isinstance(incoming,dict):raise ValueError(POLICY['unverified'])
                original=next((r for r in releases['data'] if self.release_key(r)==self.release_key(incoming)),None)
                if original is None:raise ValueError(POLICY['unverified'])
                # Replace the browser's release with the provider's checked release.
                p['release']=original
            response=self.backend(action,p)
            if not isinstance(response,dict) or not response.get('ok'):return response
            if isinstance(response.get('data'),list):
                values=response['data']
                if action in {'home','search'}:
                    if action=='search' and p.get('featured_anime') is True: values=self.featured_subset(values,p.get('query',''))
                    anime=action=='search' and (p.get('anime_context') is True or any(w in str(p.get('query','')).lower() for w in ('anime','animation')))
                    response={**response,'data':self.catalog(values,anime)}
                elif action in {'favorites','toggle_favorite','history','remove_history','clear_history','releases'}:response={**response,'data':self.local(values)}
            return response
        except (ValueError,OSError,TypeError,KeyError) as error:return {'ok':False,'error':str(error)}
