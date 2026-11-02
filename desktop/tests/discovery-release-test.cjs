const {chromium}=require('playwright'), path=require('path'), assert=require('assert');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/local/bin/chromium',headless:true,args:['--no-sandbox']});
 for(const platform of ['desktop','mobile']){
  const page=await browser.newPage({viewport:platform==='mobile'?{width:390,height:844}:{width:1280,height:900}});const errors=[];page.on('pageerror',e=>errors.push(String(e)));
  await page.addInitScript(({mobile})=>{
   let settings={player:mobile?'android':'mpv',audio_language:'',quality:'auto',hide_cam:false};window.homeCalls=0;window.failHome=false;
   const item={id:{provider:'moviebox',value:'fixture'},title:'Fixture Movie',year:'2026',media_type:'movie',poster_url:null};
   function dispatch(action,p={}){
    let data=[];if(action==='home'){window.homeCalls++;if(window.failHome)return {ok:false,error:'offline'};data=[item];}
    if(action==='players')data=[{id:mobile?'android':'mpv',label:'Player'}];
    if(action==='settings')data=settings;if(action==='save_settings'){settings={...settings,...p};data=settings;}
    if(action==='details')data={...item,description:'Fixture',genres:[],dubs:[],seasons:[]};
    if(action==='releases')data=[{filename:'Movie.CAM.2026',quality:'720p',language:'English',size_bytes:1073741824},{filename:'Movie.HD.2026',quality:'1080p',language:'English',size_bytes:2147483648},{filename:'Movie.Unknown',quality:null,language:null,size_bytes:null}];
    if(action==='play')data={message:'Mock'};return {ok:true,data};
   }
   if(mobile)window.TVNative={request(id,action,raw){const reply=dispatch(action,JSON.parse(raw));queueMicrotask(()=>window.TVPlatform.receive(id,reply));}};
   else window.pywebview={api:{dispatch:async(a,p)=>dispatch(a,p)}};
  },{mobile:platform==='mobile'});
  const html=platform==='mobile'?'../../android-mobile/app/src/main/assets/index.html':'../web/index.html';await page.goto('file://'+path.resolve(__dirname,html));
  await page.waitForFunction(()=>window.homeCalls===1&&!document.querySelector('#refresh-home').disabled);assert.match(await page.locator('#home-checked').textContent(),/Checked on this device/);
  await page.locator('#refresh-home').click();await page.waitForFunction(()=>window.homeCalls===2&&!document.querySelector('#refresh-home').disabled);
  await page.evaluate(()=>window.failHome=true);await page.locator('#refresh-home').click();await page.waitForFunction(()=>window.homeCalls===3&&!document.querySelector('#refresh-home').disabled);assert.match(await page.locator('#home-checked').textContent(),/Refresh failed/);
  await page.locator('#settings-button').click();await page.locator('#hide-cam').check();await page.locator('#save-settings').click();await page.locator('#settings-status').filter({hasText:'Preferences saved'}).waitFor();await page.locator('#close-settings').click();
  await page.locator('#hero-play').click();await page.waitForFunction(()=>document.getElementById('release').options.length===2&&!document.getElementById('play').disabled);
  const labels=await page.locator('#release option').allTextContents();assert(!labels.some(x=>x.includes('CAM')));assert(labels.some(x=>x.includes('2.00 GiB')));assert(labels.some(x=>x.includes('Unknown')));
  await page.locator('#close-details').click();await page.locator('#settings-button').click();assert(await page.locator('#hide-cam').isChecked());await page.locator('#hide-cam').uncheck();await page.locator('#save-settings').click();await page.locator('#settings-status').filter({hasText:'Preferences saved'}).waitFor();await page.locator('#close-settings').click();await page.locator('#hero-play').click();await page.waitForFunction(()=>document.getElementById('release').options.length===3);assert.match((await page.locator('#release option').allTextContents()).join(),/CAM/);
  assert.deepStrictEqual(errors,[]);await page.close();console.log(`PASS ${platform}: refresh, retained feed on failure, CAM preference, metadata labels (mock transport)`);
 }
 await browser.close();process.exit(0);
})().catch(e=>{console.error(e);process.exit(1)});
