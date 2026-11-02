// D-pad/browser bridge tests only. Native Media3 playback requires a real TV/emulator.
const {chromium}=require('playwright');const fs=require('fs');const path=require('path');const assert=require('assert');
const out=process.env.QA_DIR||'/data/moviebox-tv-qa';fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/local/bin/chromium',headless:true,args:['--no-sandbox']});const page=await browser.newPage({viewport:{width:1280,height:720},colorScheme:'dark'});const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.addInitScript(()=>{
  const items=Array.from({length:9},(_,i)=>({id:{provider:'moviebox',value:'tv'+i},title:i===0?'Sample TV Series':'Sample Title '+(i+1),media_type:i%3===0?'series':'movie',year:'2026',poster_url:null}));let favorites=[],settings={player:'android',audio_language:'',quality:'auto'};window.tvCalls=[];
  window.TVNative={request(id,action,raw){const p=JSON.parse(raw);window.tvCalls.push({action,p});let data=[];
   if(action==='home'||action==='search')data=items;
   if(action==='players')data=[{id:'android',label:'Built-in TV player'}];if(action==='settings')data=settings;if(action==='save_settings'){settings={...settings,...p};data=settings;}
   if(action==='favorites')data=favorites;if(action==='toggle_favorite'){favorites=favorites.length?[]:[p.item];data=favorites;}
   if(action==='details'){const base=items.find(x=>x.id.value===p.id)||{...items[0],id:{provider:'moviebox',value:p.id}};data={...base,genres:['Test catalog'],description:'Synthetic title for TV remote testing. No actual media is provided.',dubs:[{subject_id:'tv0',language:'English',label:'English'},{subject_id:'tv0-hi',language:'Hindi',label:'Hindi'}],seasons:[{number:1,episodes:[{number:1,title:'First episode'},{number:2,title:'Second episode'}]}]};}
   if(action==='releases')data=[{provider:'moviebox',quality:'1080p',language:p.id.endsWith('-hi')?'Hindi':'English',filename:'Synthetic TV release — not playable',resource_id:'qa',mirrors:[]}];
   if(action==='subtitles')data=[];if(action==='play')data={message:'Mock native TV playback request received'};
   queueMicrotask(()=>window.TVPlatform.receive(id,{ok:true,data}));
  }};
 });
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));await page.waitForFunction(()=>document.querySelectorAll('#home-rows .card').length===9);await page.evaluate(()=>window.tvInitialFocus());assert.strictEqual(await page.evaluate(()=>document.activeElement.id),'nav-search');
 await page.screenshot({path:out+'/01-home.png'});
 await page.keyboard.press('ArrowRight');assert.strictEqual(await page.evaluate(()=>document.activeElement.id),'nav-movies');await page.keyboard.press('ArrowRight');assert.strictEqual(await page.evaluate(()=>document.activeElement.id),'nav-series');
 await page.locator('#settings-button').focus();await page.keyboard.press('Enter');await page.locator('#settings-dialog').waitFor();await page.keyboard.press('ArrowDown');assert.strictEqual(await page.evaluate(()=>document.activeElement.id),'preferred-player');
 await page.locator('#preferred-audio').selectOption('hin');await page.locator('#preferred-quality').selectOption('1080');await page.locator('#save-settings').focus();await page.keyboard.press('Enter');await page.locator('#settings-status').filter({hasText:'Preferences saved'}).waitFor();await page.screenshot({path:out+'/02-settings.png'});assert(await page.evaluate(()=>window.tvBack()));
 await page.locator('#tv-search').focus();await page.keyboard.press('Enter');assert(await page.locator('#query').isVisible());await page.locator('#query').fill('sample');await page.keyboard.press('Enter');await page.waitForFunction(()=>document.querySelectorAll('#results .card').length===9);await page.screenshot({path:out+'/03-search.png'});
 await page.locator('#results .poster-button').first().focus();await page.keyboard.press('ArrowRight');assert.strictEqual(await page.locator('#results .poster-button').nth(1).evaluate(el=>el===document.activeElement),true);await page.keyboard.press('ArrowLeft');await page.keyboard.press('Enter');await page.waitForFunction(()=>document.getElementById('audio-language').value==='dub:tv0-hi'&&!document.getElementById('play').disabled);await page.screenshot({path:out+'/04-hindi-details.png'});
 await page.locator('#play').focus();await page.keyboard.press('Enter');await page.waitForFunction(()=>window.tvCalls.some(x=>x.action==='play'));assert.strictEqual((await page.evaluate(()=>tvCalls.find(x=>x.action==='play'))).p.audio_language,'hin');assert.strictEqual((await page.evaluate(()=>tvCalls.find(x=>x.action==='play'))).p.player,'android');
 assert(await page.evaluate(()=>window.tvBack()));await page.evaluate(()=>document.getElementById('nav-search').click());await page.locator('#home-rows .poster-button').first().focus();await page.keyboard.press('ArrowRight');assert(await page.locator('#home-rows .poster-button').nth(1).evaluate(el=>el===document.activeElement));
 for(let i=0;i<5;i++)await page.keyboard.press('ArrowRight');assert(await page.evaluate(()=>document.activeElement.classList.contains('poster-button')));await page.screenshot({path:out+'/05-row-focus.png'});
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Horizontal page overflow');
 await page.setViewportSize({width:1920,height:1080});await page.locator('#brand-home').click();await page.screenshot({path:out+'/06-1080p.png'});
 assert.deepStrictEqual(errors,[]);await browser.close();console.log('PASS: TV bridge promises, D-pad left/right card and nav focus, settings Down navigation, Enter activation, Hindi preference and correct dubbed ID, built-in player dispatch, Back modal handling, horizontal row scrolling, 720p/1080p layout. Playback is mocked, not a native TV test.');process.exit(0);
})().catch(e=>{console.error(e);process.exit(1)});
