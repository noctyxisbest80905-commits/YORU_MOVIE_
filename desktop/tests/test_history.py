import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parents[1]))
import json
import sqlite3
import tempfile
import time
import unittest
from unittest.mock import patch
from watch_history import History, atomic_json, bounded_number, current_uri, media_hash, track_vlc, MPV_LUA
from test_api import ITEM

class HistoryTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.h=History(Path(self.temp.name));self.p={'item':ITEM,'context':{'provider':'moviebox','id':'test-hi','season':1,'episode':2},'player':'mpv','audio_language':'hin','resume_seconds':0,'release':{'quality':'1080p','filename':'Episode 2','resource_id':'r2','mirrors':[{'url':'SECRET'}]}}
    def launch(self,**changes):
        entry,tracking,resume=self.h.prepare({**self.p,**changes});self.h.register(entry);return entry,tracking,resume
    def sample(self,entry,position,duration=3600,**extra):
        atomic_json(self.h.sessions/(entry['session']+'.json'),{'position':position,'duration':duration,'observed':True,'timestamp':int(time.time()),**extra})
    def test_empty(self):self.assertEqual(self.h.read(),[])
    def test_position_survives_restart(self):
        e,_,_=self.launch();self.sample(e,1250)
        other=History(self.h.folder);r=other.read()[0]
        self.assertEqual(r['position'],1250);self.assertEqual(r['context']['id'],'test-hi');self.assertEqual(r['context']['episode'],2)
        self.assertNotIn('session',r);self.assertNotIn('SECRET',json.dumps(r))
    def test_seek_backwards_is_not_monotonic(self):
        e,_,_=self.launch();self.sample(e,1200);self.h.read();self.sample(e,200);self.assertEqual(self.h.read()[0]['position'],200)
    def test_start_over_can_record_zero(self):
        e,_,_=self.launch();self.sample(e,1200);self.h.read();e2,_,_=self.launch(resume_seconds=0);self.sample(e2,0);self.assertEqual(self.h.read()[0]['position'],0)
    def test_relaunch_deduplicates(self):
        e,_,_=self.launch();self.sample(e,500);self.h.read();e2,_,_=self.launch(resume_seconds=500);self.assertEqual(len(self.h.read()),1);self.assertEqual(self.h.read()[0]['position'],500)
    def test_old_session_cannot_override_new(self):
        e,_,_=self.launch();e2,_,_=self.launch();self.sample(e2,100);self.h.read();self.sample(e,2000);self.assertEqual(self.h.read()[0]['position'],100)
    def test_different_episodes_kept(self):
        self.launch();self.launch(context={**self.p['context'],'episode':3});self.assertEqual(len(self.h.read()),2)
    def test_finished_threshold_and_eof(self):
        e,_,_=self.launch();self.sample(e,3420);self.assertTrue(self.h.read()[0]['completed']);self.sample(e,50);self.assertFalse(self.h.read()[0]['completed']);self.sample(e,3000,completed=True);self.assertTrue(self.h.read()[0]['completed'])
    def test_unknown_duration_keeps_real_position(self):
        e,_,_=self.launch();self.sample(e,200,duration=0);r=self.h.read()[0];self.assertEqual(r['position'],200);self.assertFalse(r['completed'])
    def test_missing_and_partial_status_preserve_history(self):
        e,_,_=self.launch();self.sample(e,300);self.h.read();(self.h.sessions/(e['session']+'.json')).write_text('{');self.assertEqual(self.h.read()[0]['position'],300)
    def test_invalid_status_cannot_fake_progress(self):
        e,_,_=self.launch();self.sample(e,-20);self.assertEqual(self.h.read()[0]['position'],0);self.sample(e,float('nan'));self.assertEqual(self.h.read()[0]['position'],0)
    def test_unavailable_tracking_is_explicit(self):
        e,_,_=self.launch();atomic_json(self.h.sessions/(e['session']+'.json'),{'error':'no player'});self.assertEqual(self.h.read()[0]['tracking'],'unavailable')
    def test_remove_is_not_resurrected_by_running_player(self):
        e,_,_=self.launch();self.h.remove(e['key']);self.sample(e,400);self.assertEqual(self.h.read(),[])
    def test_clear_does_not_touch_favorites(self):
        f=self.h.folder/'favorites.json';f.write_text('[1]');self.launch();self.h.remove();self.assertEqual(f.read_text(),'[1]');self.assertEqual(self.h.read(),[])
    def test_bad_context_and_resume_rejected(self):
        for p in [{'resume_seconds':float('inf')},{'resume_seconds':-1},{'resume_seconds':True},{'context':{'provider':'wrong','id':'x'}},{'audio_language':'--arbitrary'},{'item':{'title':'x'}}]:
            with self.subTest(p=p),self.assertRaises(ValueError):self.h.prepare({**self.p,**p})
    def test_sessions_are_unique_and_paths_private(self):
        a,t,_=self.launch();b,_,_=self.launch();self.assertNotEqual(a['session'],b['session']);path=Path(t['mpv_script']);self.assertEqual(path.parent,self.h.sessions);self.assertIn('utils.format_json',path.read_text())
    def test_vlc_endpoint_uses_random_password_and_port(self):
        _,t,_=self.launch(player='vlc');self.assertGreaterEqual(t['vlc_port'],1024);self.assertGreaterEqual(len(t['vlc_password']),32)
    def test_helper_refuses_switched_media(self):
        e,t,_=self.launch(player='vlc');cfg=self.h.sessions/(e['session']+'.vlc');atomic_json(cfg,{'session':e['session'],'port':t['vlc_port'],'password':t['vlc_password'],'media_hash':media_hash('https://example.invalid/movie')})
        with patch('watch_history.vlc_get',side_effect=[{'state':'playing','time':500,'length':3600},{'children':[{'current':'current','uri':'https://example.invalid/other'}]}]):track_vlc(cfg,interval=0)
        self.assertFalse((self.h.sessions/(e['session']+'.json')).exists());self.assertFalse(cfg.exists())
    def test_helper_records_matching_movie_then_stops(self):
        e,t,_=self.launch(player='vlc');url='https://example.invalid/a%20movie';cfg=self.h.sessions/(e['session']+'.vlc');atomic_json(cfg,{'session':e['session'],'port':t['vlc_port'],'password':t['vlc_password'],'media_hash':media_hash(url)})
        playlist={'children':[{'current':'current','uri':url}]}
        with patch('watch_history.vlc_get',side_effect=[{'state':'playing','time':864,'length':4000},playlist,{'state':'stopped'},playlist]):track_vlc(cfg,interval=0)
        self.assertEqual(self.h.read()[0]['position'],864)
    def test_lua_has_pause_exit_and_atomic_json_hooks(self):
        for key in ['utils.format_json','shutdown','end-file','pause','mp.add_periodic_timer(3','os.rename']:self.assertIn(key,MPV_LUA)
    def test_helpers_do_not_follow_redirects_or_proxy(self):
        from watch_history import NoRedirect
        self.assertIsNone(NoRedirect().redirect_request(None,None,302,None,None,None))

if __name__=='__main__':unittest.main()
