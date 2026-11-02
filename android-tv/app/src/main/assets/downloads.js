'use strict';
const downloadState={busy:false,timer:null,loading:false,lastFocus:null};
function downloadBytes(value){const n=Math.max(0,Number(value)||0);return n>=1073741824?(n/1073741824).toFixed(1)+' GB':n>=1048576?(n/1048576).toFixed(1)+' MB':Math.round(n/1024)+' KB';}
function downloadPayload(){return {item:state.item,context:requestContext(),release:state.releases[Number($('release').value)],player:'android',audio_language:$('audio-language').selectedOptions[0]?.dataset.code||''};}
const beforeDownloadUpdate=updatePlay;
updatePlay=function(){beforeDownloadUpdate();$('download-video').disabled=downloadState.busy||$('play').disabled;};
$('download-video').onclick=async()=>{
 if($('download-video').disabled)return;
 if(!confirm('Download this selected release for offline viewing? Only download content you are authorized to save. Large files use mobile data or Wi-Fi and device storage. External subtitle files are not saved.'))return;
 const payload=downloadPayload();downloadState.busy=true;updatePlay();$('download-video').textContent='Preparing download…';status('Checking and preparing offline video…',false,'play-status');
 try{const result=await api('download',payload);status(result.message,false,'play-status');}catch(e){status(e.message,true,'play-status');}finally{downloadState.busy=false;$('download-video').textContent='↓ Download selected release';updatePlay();}
};
function downloadAction(label,action,entry){const button=document.createElement('button');button.className='secondary';button.textContent=label;button.onclick=async()=>{if(action==='download_remove'&&!confirm('Remove this downloaded video from this device?'))return;button.disabled=true;try{await api(action,{id:entry.id});$('downloads-status').textContent=action==='play_offline'?'Opening downloaded video…':'Request sent. Progress will update shortly.';await refreshDownloads(false);}catch(e){$('downloads-status').textContent=e.message;}finally{button.disabled=false;}};return button;}
function downloadCard(entry){
 const card=document.createElement('article');card.className='download-card';card.dataset.downloadId=entry.id;
 const title=document.createElement('h3');title.textContent=entry.item?.title||'Downloaded title';card.append(title);
 const info=document.createElement('p');info.className='muted';info.textContent=[entry.context?.season?`S${entry.context.season} · E${entry.context.episode}`:'Movie',entry.quality,languageName(entry.audio_language)].filter(Boolean).join(' · ');card.append(info);
 const labels={completed:'Ready offline',downloading:'Downloading',queued:'Queued · waiting for network or a slot',paused:'Paused',failed:'Failed · retry, or remove and download a fresh release',removing:'Removing'};
 const status=document.createElement('p');status.className='download-state';status.textContent=labels[entry.state]||entry.state;card.append(status);
 const progress=document.createElement('progress');progress.max=100;if(entry.state==='completed')progress.value=100;else if(entry.percent>=0)progress.value=Math.min(100,entry.percent);progress.setAttribute('aria-label',`${entry.item?.title||'Video'} download progress`);card.append(progress);
 const size=document.createElement('p');size.className='muted';size.textContent=downloadBytes(entry.bytes)+(entry.total>0?' / '+downloadBytes(entry.total):' saved')+(entry.percent>=0?' · '+Math.floor(entry.percent)+'%':'');card.append(size);
 const actions=document.createElement('div');actions.className='download-actions';
 if(entry.state==='completed')actions.append(downloadAction('▶ Play offline','play_offline',entry));
 if(entry.state==='downloading'||entry.state==='queued')actions.append(downloadAction('Pause','download_pause',entry));
 if(entry.state==='paused'||entry.state==='failed'||entry.state==='queued')actions.append(downloadAction(entry.state==='failed'?'Retry':'Resume','download_resume',entry));
 if(entry.state!=='removing')actions.append(downloadAction('Remove','download_remove',entry));card.append(actions);return card;
}
async function refreshDownloads(report=true){
 if(downloadState.loading)return;downloadState.loading=true;
 try{const data=await api('downloads');if(!data||!Array.isArray(data.items))throw new Error('Downloads are available in the updated Android app.');const focused=document.activeElement;const oldCard=focused?.closest('.download-card'),focusId=oldCard?.dataset.downloadId,focusText=focused?.textContent;const scroll=$('downloads-dialog').scrollTop;
 $('downloads-grid').replaceChildren(...data.items.map(downloadCard));if(!data.items.length&&$('downloads-status').textContent==='Removing downloads…')$('downloads-status').textContent='All downloads removed.';$('downloads-empty').hidden=data.items.length>0;$('clear-downloads').disabled=data.items.length===0&&!(data.used_bytes>0);$('downloads-storage').textContent=`${downloadBytes(data.used_bytes)} stored · ${downloadBytes(data.free_bytes)} free`;
 if(focusId){const card=[...$('downloads-grid').children].find(x=>x.dataset.downloadId===focusId);const button=card&&[...card.querySelectorAll('button')].find(x=>x.textContent===focusText);(button||$('close-downloads')).focus({preventScroll:true});}$('downloads-dialog').scrollTop=scroll;
 }catch(e){if(report)$('downloads-status').textContent=e.message;}finally{downloadState.loading=false;}
}
window.openDownloads=async function(){downloadState.lastFocus=document.activeElement;const dialog=$('downloads-dialog');if(!dialog.open)dialog.showModal();$('downloads-status').textContent='';$('close-downloads').focus();await refreshDownloads();clearInterval(downloadState.timer);downloadState.timer=setInterval(()=>{if(dialog.open&&!document.hidden)refreshDownloads(false);},2000);};
$('nav-downloads').onclick=window.openDownloads;
$('close-downloads').onclick=()=>$('downloads-dialog').close();
$('downloads-dialog').addEventListener('close',()=>{clearInterval(downloadState.timer);downloadState.lastFocus?.focus?.();});
$('refresh-downloads').onclick=()=>refreshDownloads();
$('clear-downloads').onclick=async()=>{if(!confirm('Remove all downloaded videos from this device? This will not remove favorites or watch history.'))return;try{await api('download_clear',{confirm:true});$('downloads-status').textContent='Removing downloads…';await refreshDownloads();}catch(e){$('downloads-status').textContent=e.message;}};
updatePlay();
