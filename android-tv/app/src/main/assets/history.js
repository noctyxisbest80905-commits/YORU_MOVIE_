'use strict';
const watchState={items:[],busy:false,token:0,startOver:false,contextKey:'',opening:false};
function formatWatchTime(seconds){const n=Math.max(0,Math.floor(Number(seconds)||0));const h=Math.floor(n/3600),m=Math.floor(n%3600/60),s=n%60;return (h?h+':':'')+String(m).padStart(2,'0')+':'+String(s).padStart(2,'0');}
function watchEpisode(entry){return entry.context.season?`S${entry.context.season} · E${entry.context.episode}`:'Movie';}
function watchIdentity(item){return JSON.stringify(item.id);}
function matchingWatchEntry(){if(!state.item||!state.streamDetails)return null;const ctx=requestContext();return watchState.items.find(x=>watchIdentity(x.item)===watchIdentity(state.item)&&x.context.provider===ctx.provider&&x.context.id===ctx.id&&x.context.season===ctx.season&&x.context.episode===ctx.episode&&!x.completed&&x.position>=15)||null;}
function updateResume(){const entry=matchingWatchEntry();const key=entry?.key||'';if(key!==watchState.contextKey){watchState.contextKey=key;watchState.startOver=false;}$('resume-panel').hidden=!entry;if(entry){$('resume-label').textContent=watchState.startOver?'Start from the beginning':`Resume at ${formatWatchTime(entry.position)} · ${watchEpisode(entry)}`;$('start-over').textContent=watchState.startOver?'Use saved position':'Start over';}$('play').textContent=entry&&!watchState.startOver?`▶ Resume at ${formatWatchTime(entry.position)}`:'▶ Play now';}
function watchCard(entry,full=false){
 const el=document.createElement('article');el.className='card watch-card';
 const art=document.createElement('button');art.className='poster-button';art.setAttribute('aria-label',`Open ${entry.item.title}, ${watchEpisode(entry)}, saved at ${formatWatchTime(entry.position)}`);art.append(poster(entry.item));art.onclick=()=>resumeWatch(entry);el.append(art);
 const title=document.createElement('button');title.className='card-title';title.textContent=entry.item.title;title.onclick=()=>resumeWatch(entry);el.append(title);
 const context=document.createElement('p');context.className='card-meta';context.textContent=[watchEpisode(entry),languageName(entry.audio_language)].filter(Boolean).join(' · ');el.append(context);
 if(entry.duration>0){const bar=document.createElement('progress');bar.max=entry.duration;bar.value=entry.completed?entry.duration:Math.min(entry.position,entry.duration);bar.setAttribute('aria-label',`${formatWatchTime(entry.position)} of ${formatWatchTime(entry.duration)} watched`);el.append(bar);}
 const saved=document.createElement('p');saved.className='watch-position';saved.textContent=entry.completed?'Finished':entry.tracking==='unavailable'?'Tracking unavailable':entry.position>0?`Saved at ${formatWatchTime(entry.position)}`:'Waiting for player progress';el.append(saved);
 if(full){const button=document.createElement('button');button.className='text-button remove-watch';button.textContent='Remove';button.setAttribute('aria-label',`Remove ${entry.item.title}, ${watchEpisode(entry)} from history`);button.onclick=()=>removeWatch(entry);el.append(button);}
 return el;
}
function renderWatchHistory(){
 const latest=new Map();for(const entry of watchState.items){const key=watchIdentity(entry.item);if(!latest.has(key))latest.set(key,entry);}
 const ongoing=filtered([...latest.values()].filter(x=>!x.completed&&x.position>=15).map(x=>({...x.item,_entry:x}))).map(x=>x._entry);
 const visible=state.mode==='search'&&!state.query&&ongoing.length>0;
 $('continue-section').hidden=!visible;$('continue-strip').replaceChildren(...ongoing.slice(0,12).map(x=>watchCard(x)));
 $('history-grid').replaceChildren(...watchState.items.map(x=>watchCard(x,true)));$('history-empty').hidden=watchState.items.length>0;$('clear-history').disabled=!watchState.items.length;
}
async function refreshWatchHistory(force=false){if(watchState.busy||!state.ready)return;if(!force&&['details-dialog','settings-dialog','history-dialog'].some(id=>$(id).open))return;const token=++watchState.token;watchState.busy=true;try{const data=await api('history');if(token!==watchState.token)return;const changed=JSON.stringify(data)!==JSON.stringify(watchState.items);watchState.items=data;if(changed||force)renderWatchHistory();}catch(e){if(force)status(e.message,true,'history-status');}finally{watchState.busy=false;}}
async function showWatchHistory(){if($('settings-dialog').open)$('settings-dialog').close();if(!$('history-dialog').open)$('history-dialog').showModal();status('',false,'history-status');await refreshWatchHistory(true);renderWatchHistory();}
async function removeWatch(entry){++watchState.token;try{watchState.items=await api('remove_history',{key:entry.key});renderWatchHistory();updateResume();}catch(e){status(e.message,true,'history-status');}}
async function clearWatchHistory(){if(!window.confirm('Clear all watch history and saved resume positions? Favorites will be kept.'))return;++watchState.token;try{watchState.items=await api('clear_history',{confirm:true});renderWatchHistory();updateResume();status('Watch history cleared.',false,'history-status');}catch(e){status(e.message,true,'history-status');}}
const historyBaseRender=renderCatalog;renderCatalog=function(){historyBaseRender();renderWatchHistory();};
const historyBaseUpdatePlay=updatePlay;updatePlay=function(){historyBaseUpdatePlay();if(watchState.opening)$('play').disabled=true;updateResume();};
const historyBaseOpen=openDetails;
openDetails=async function(item,requested=null){
 watchState.startOver=false;watchState.contextKey='';watchState.opening=true;document.querySelector('.detail-controls').inert=true;
 const candidate=requested||watchState.items.find(x=>watchIdentity(x.item)===watchIdentity(item)&&!x.completed&&x.position>=15);
 const work=historyBaseOpen(item);const token=state.detailToken;
 try{
  await work;if(token!==state.detailToken||!state.details)return;
  if(candidate){
   const ctx=candidate.context;const audioValue=ctx.id===state.details.id.value?'':`dub:${ctx.id}`;
   const audioOption=[...$('audio-language').options].find(o=>o.value===audioValue);
   if(!audioOption){status('The saved audio edition is unavailable. Choose an available edition to start a new session.',true,'play-status');return;}
   if($('audio-language').value!==audioValue){$('audio-language').value=audioValue;await switchAudio();}
   if(token!==state.detailToken||!state.streamDetails)return;
   if(ctx.season&&!([...$('season').options].some(o=>Number(o.value)===ctx.season))){status('The saved season is unavailable. Choose another season to start a new session.',true,'play-status');return;}
   populateSeasons(String(ctx.season),String(ctx.episode));
   if(ctx.season&&(Number($('season').value)!==ctx.season||Number($('episode').value)!==ctx.episode)){status('The saved episode is unavailable. Choose an available episode to start a new session.',true,'play-status');return;}
   await loadStreams();if(token!==state.detailToken)return;
   if(candidate.audio_language&&$('audio-language').options.length>1&&!$('audio-language').value.startsWith('dub:')){const language=[...$('audio-language').options].find(o=>o.dataset.code===candidate.audio_language);if(language){$('audio-language').value=language.value;applyReleaseFilter();}}
   const old=candidate.selection||{};const index=state.releases.findIndex(r=>((old.resource_id&&r.resource_id===old.resource_id)||(old.filename&&r.filename===old.filename))&&(!old.quality||r.quality===old.quality));
   if(index>=0){$('release').value=String(index);await loadSubtitles();}else if(state.releases.length){status('The previous release is unavailable. Choose a current release; timing may differ between versions.',false,'play-status');}
  }
 }finally{if(token===state.detailToken){watchState.opening=false;document.querySelector('.detail-controls').inert=false;updatePlay();}}
};
async function resumeWatch(entry){if($('history-dialog').open)$('history-dialog').close();await openDetails(entry.item,entry);}
play=async function(){
 if($('play').disabled)return;const token=state.detailToken;const entry=matchingWatchEntry();state.playing=true;updatePlay();status('Preparing the stream and saved watch position…',false,'play-status');
 const audio=$('audio-language').selectedOptions[0]?.dataset.code||(!state.audioUserChoice?state.preferredAudio:'');
 try{const result=await api('play',{item:state.item,context:requestContext(),release:state.releases[Number($('release').value)],player:$('detail-player').value,subtitle:$('subtitle').value,audio_language:audio,resume_seconds:entry&&!watchState.startOver?Math.floor(entry.position):0});if(token===state.detailToken)status(result.message+(result.source_host?` Stream server: ${result.source_host}.`:''),false,'play-status');}
 catch(e){if(token===state.detailToken)status(e.message,true,'play-status');}
 finally{if(token===state.detailToken){state.playing=false;updatePlay();}refreshWatchHistory(true);}
};
$('play').onclick=play;$('start-over').onclick=()=>{watchState.startOver=!watchState.startOver;updateResume();};
$('view-history').onclick=showWatchHistory;$('nav-history').onclick=showWatchHistory;$('settings-history').onclick=showWatchHistory;$('close-history').onclick=()=>$('history-dialog').close();$('clear-history').onclick=clearWatchHistory;
window.addEventListener('focus',()=>refreshWatchHistory());document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshWatchHistory();});
window.addEventListener('pywebviewready',()=>refreshWatchHistory(true));setInterval(()=>{if(!document.hidden)refreshWatchHistory();},5000);refreshWatchHistory(true);
function showCredits(){if($('settings-dialog').open)$('settings-dialog').close();if(!$('credits-dialog').open)$('credits-dialog').showModal();}
$('credits-footer').onclick=showCredits;$('settings-credits').onclick=showCredits;$('close-credits').onclick=()=>$('credits-dialog').close();
