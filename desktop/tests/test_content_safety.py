import json
import sys
import tempfile
import threading
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).parents[1]))
from content_safety import is_blocked, Blocked, POLICY
from content_gate import ContentGate
from test_api import app

class RulesTests(unittest.TestCase):
    def test_shared_corpus(self):
        for case in json.loads((Path(__file__).parents[2]/'safety/cases.json').read_text(encoding='utf-8')):
            with self.subTest(case=case['name']):self.assertEqual(is_blocked(case['value']),case['blocked'])

class GateTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.calls=[];self.lock=threading.Lock();self.folder=Path(self.temp.name)
        self.safe={'id':{'provider':'moviebox','value':'safe'},'title':'Naruto','genres':['Animation','Adventure'],'description':'A ninja adventure.'}
        self.bad={'id':{'provider':'moviebox','value':'bad'},'title':'Ordinary listing title','genres':['Hentai'],'description':'Provider-labelled adult animation.'}
        self.unknown={'id':{'provider':'moviebox','value':'unknown'},'title':'Missing metadata'}
        self.mismatch={'id':{'provider':'moviebox','value':'other'},'title':'Other title','description':'An adventure.'}
        self.catalog=[{k:v for k,v in self.safe.items() if k in {'id','title'}},{k:v for k,v in self.bad.items() if k in {'id','title'}},self.unknown,{'id':{'provider':'moviebox','value':'mismatch'},'title':'Wrong identity'},{'id':{'provider':'moviebox','value':'flag'},'title':'Hentai listing'}]
        self.release={'provider':'moviebox','resource_id':'r1','filename':'safe release','quality':'1080p','mirrors':[{'resolver_url':'https://example.invalid/original'}]}
        self.gate=ContentGate(self.backend,self.folder/'blocked.json');self.addCleanup(self.gate.close)
    def backend(self,action,p):
        with self.lock:self.calls.append((action,p))
        data=[]
        if action in {'home','search'}:data=self.catalog
        if action=='details':data={'safe':self.safe,'bad':self.bad,'unknown':self.unknown,'mismatch':self.mismatch}.get(p.get('id'))
        if action=='releases':data=[self.release,{**self.release,'filename':'Hentai release'}]
        if action=='play':data={'message':'stub player launched'}
        if action in {'favorites','history','toggle_favorite'}:data=self.catalog
        return {'ok':True,'data':data}
    def payload(self,id='safe'):
        return {'item':{'id':{'provider':'moviebox','value':id},'title':'Innocent display name'},'context':{'provider':'moviebox','id':id,'season':0,'episode':0},'release':dict(self.release),'player':'vlc'}
    def test_search_screens_details_and_unknowns_fail_closed(self):
        r=self.gate.request('search',{'provider':'moviebox','query':'anime'});self.assertTrue(r['ok']);self.assertEqual([x['id']['value'] for x in r['data']],['safe'])
    def test_featured_search_limits_candidates_and_excludes_obvious_live_action(self):
        values=[{'title':'One Piece','year':'1999'}, {'title':'One Piece [netflix]','year':'2023'}, {'title':'One Piece [Hindi] S1-S2','year':'2023'}, {'title':'One Piece [Hindi]','year':'2024'}, {'title':'Unrelated'}]
        result=self.gate.featured_subset(values,'One Piece')
        self.assertEqual([x['title'] for x in result],['One Piece','One Piece [Hindi]'])
        self.assertEqual(self.gate.featured_subset(values,'Unknown'),[])

    def test_home_uses_same_gate(self):self.assertEqual([x['id']['value'] for x in self.gate.request('home',{})['data']],['safe'])
    def test_explicit_query_never_calls_provider(self):self.assertFalse(self.gate.request('search',{'query':'hentai'})['ok']);self.assertEqual(self.calls,[])
    def test_harem_query_never_calls_provider(self):
        self.assertFalse(self.gate.request('search',{'query':'reverse harem anime'})['ok']);self.assertEqual(self.calls,[])
    def test_ordinary_anime_details_allowed(self):self.assertTrue(self.gate.request('details',{'provider':'moviebox','id':'safe'})['ok'])
    def test_blocked_details_and_known_ids_cannot_be_renamed(self):
        self.assertFalse(self.gate.request('details',{'provider':'moviebox','id':'bad'})['ok'])
        self.assertEqual(self.gate.local([{'id':self.bad['id'],'title':'New harmless display name'}]),[])
    def test_playback_rechecks_context_and_never_launches_bad_title(self):
        self.assertFalse(self.gate.request('play',self.payload('bad'))['ok']);self.assertNotIn('play',[a for a,p in self.calls]);self.assertNotIn('releases',[a for a,p in self.calls])
    def test_unverified_metadata_cannot_play(self):self.assertFalse(self.gate.request('play',self.payload('unknown'))['ok']);self.assertNotIn('play',[a for a,p in self.calls])
    def test_unsafe_release_cannot_launch(self):
        p=self.payload();p['release']['filename']='hentai clip';self.assertFalse(self.gate.request('play',p)['ok']);self.assertNotIn('play',[a for a,p in self.calls])
    def test_safe_release_launch_uses_original_provider_links(self):
        p=self.payload();p['release']['mirrors']=[{'resolver_url':'https://example.invalid/spoof'}]
        self.assertTrue(self.gate.request('play',p)['ok']);played=next(p for a,p in self.calls if a=='play');self.assertEqual(played['release']['mirrors'],self.release['mirrors'])
    def test_unoffered_release_cannot_launch(self):
        p=self.payload();p['release']['resource_id']='not-offered';self.assertFalse(self.gate.request('play',p)['ok']);self.assertNotIn('play',[a for a,p in self.calls])
    def test_releases_and_subtitles_cannot_bypass_details(self):
        for action in ['releases','subtitles']:self.assertFalse(self.gate.request(action,{'provider':'moviebox','id':'bad'})['ok'])
        self.assertNotIn('releases',[a for a,p in self.calls]);self.assertNotIn('subtitles',[a for a,p in self.calls])
    def test_blocked_ids_persist_across_restart(self):
        self.gate.request('details',{'provider':'moviebox','id':'bad'});other=ContentGate(self.backend,self.folder/'blocked.json');self.addCleanup(other.close)
        self.assertEqual(other.local([{'id':self.bad['id'],'title':'Renamed'}]),[])
    def test_legacy_lists_hide_flags_without_deleting_favorites(self):
        before=json.dumps(self.catalog);self.assertNotIn('flag',[x['id']['value'] for x in self.gate.request('favorites',{})['data']]);self.assertEqual(json.dumps(self.catalog),before)
    def test_saved_dub_context_cannot_bypass_known_block(self):
        self.gate.request('details',{'provider':'moviebox','id':'bad'});record={'item':self.safe,'context':{'provider':'moviebox','id':'bad'}};self.assertEqual(self.gate.local([record]),[])
    def test_cache_avoids_repeated_metadata_calls(self):
        for _ in range(2):self.gate.request('details',{'provider':'moviebox','id':'safe'})
        self.assertEqual(sum(a=='details' for a,p in self.calls),1)
    def test_pc_dispatch_cannot_bypass_native_gate(self):
        api=app.Api(self.folder/'absent.exe',self.folder/'favorites.json');self.addCleanup(api._close)
        r=api.dispatch('play',{**self.payload(),'item':{'title':'Hentai','id':self.safe['id']}});self.assertFalse(r['ok']);self.assertEqual(r['error'],POLICY['message'])
