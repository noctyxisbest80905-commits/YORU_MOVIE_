const {chromium}=require('playwright'),path=require('path'),assert=require('assert');const root=path.resolve(__dirname,'..');
(async()=>{const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/local/bin/chromium',headless:true,args:['--no-sandbox']});
for(const platform of ['pc','mobile','tv']){
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.addInitScript(()=>{
  window.requests=[];function reply(action,p){window.requests.push({action,p});let data=[];
   if(action==='settings')data={player:'android',audio_language:'',quality:'auto'};if(action==='players')data=[{id:'android',label:'Player'}];
   if(action==='search')data=[{id:{provider:'moviebox',value:p.query},title:p.query,genres:['Animation'],media_type:'series',year:'2020'},{id:{provider:'moviebox',value:'bad'},title:'High School DxD',genres:['Animation'],media_type:'series'}];
   return {ok:true,data};}
  window.TVNative={request(id,action,raw){queueMicrotask(()=>TVPlatform.receive(id,reply(action,JSON.parse(raw))))}};window.pywebview={api:{dispatch:async(action,p={})=>reply(action,p)}};
 });
 const file=platform==='pc'?'desktop/web/index.html':`android-${platform}/app/src/main/assets/index.html`;await page.goto('file://'+path.join(root,file));await page.waitForFunction(()=>state.ready);
 await page.evaluate(async()=>{document.getElementById('query').value='anime';await search();});const values=await page.evaluate(()=>({titles:state.items.map(x=>x.title),calls:requests.filter(x=>x.action==='search'),pagination:document.querySelector('#pagination').hidden}));
 assert.deepStrictEqual(values.titles,['Bleach','Attack on Titan','Sword Art Online','Dragon Ball Z','One Piece','Naruto','Demon Slayer','Jujutsu Kaisen']);assert(values.pagination);assert(values.calls.every(x=>x.p.featured_anime&&x.p.anime_context));
 await page.evaluate(async()=>{document.getElementById('query').value='sao';await search();});assert.equal((await page.evaluate(()=>requests.filter(x=>x.action==='search').at(-1).p.query)),'Sword Art Online');assert.deepStrictEqual(errors,[]);await page.close();console.log('PASS '+platform+': featured anime, explicit/harem exclusion, aliases and pagination (mock provider).');
}await browser.close();process.exit(0)})().catch(e=>{console.error(e);process.exit(1)});
