'use strict';
let mobileRoute='home';
function syncMobile(){
 const library=state.mode==='favorites';
 const searching=mobileRoute==='search'&&!library;
 document.body.classList.toggle('mobile-searching',searching);
 document.body.classList.toggle('mobile-library',library);
 document.body.classList.toggle('mobile-home-feed',!library&&!state.query&&state.filter==='all'&&state.homeItems.length>0);
 $('route-title').hidden=!searching&&!library;
 $('route-heading').textContent=library?'My list':'Search';
 $('route-caption').textContent=library?'The stories you saved. All in one place.':'Find a story worth staying in for.';
 for(const [id,active]of[['nav-search',!searching&&!library],['mobile-search',searching],['nav-favorites',library]]){const el=$(id);el.classList.toggle('active',active);if(active)el.setAttribute('aria-current','page');else el.removeAttribute('aria-current');}
 for(const [id,active]of[['all-category',state.filter==='all'],['nav-movies',state.filter==='movie'],['nav-series',state.filter==='series']]){const el=$(id);el.classList.toggle('active',active);el.setAttribute('aria-pressed',String(active));}
}
const mobileRenderBase=renderCatalog;
renderCatalog=function(){mobileRenderBase();syncMobile();};
const mobileModeBase=setMode;
setMode=function(mode='search',filter='all'){mobileRoute=mode==='favorites'?'library':mobileRoute;mobileModeBase(mode,filter);syncMobile();};
function mobileHome(filter='all'){mobileRoute='home';state.query='';$('query').value='';setMode('search',filter);updateHero(filtered(state.homeItems).find(x=>imageUrl(x.poster_url))||filtered(state.homeItems)[0]||null);window.scrollTo({top:0,behavior:'instant'});}
function mobileSearch(){mobileRoute='search';setMode('search');window.scrollTo({top:0,behavior:'instant'});setTimeout(()=>$('query').focus(),50);}
$('nav-search').onclick=()=>mobileHome();$('all-category').onclick=()=>mobileHome();$('brand-home').onclick=()=>mobileHome();$('nav-movies').onclick=()=>mobileHome('movie');$('nav-series').onclick=()=>mobileHome('series');
$('mobile-search').onclick=mobileSearch;$('header-search').onclick=mobileSearch;
$('nav-favorites').onclick=()=>{setMode('favorites');window.scrollTo({top:0,behavior:'instant'});};
const mobileSearchBase=search;search=async function(page=1){$('query').blur();await mobileSearchBase(page);syncMobile();};
$('search-form').onsubmit=e=>{e.preventDefault();search();};
$('hero-play').onclick=()=>state.heroItem?openDetails(state.heroItem):mobileSearch();
window.mobileReady=()=>{syncMobile();};
window.mobileRestore=()=>{if(typeof refreshWatchHistory==='function')refreshWatchHistory(true);};
window.mobileBack=()=>{const yoruDialog=document.querySelector('dialog[open]');if(yoruDialog&& !['details-dialog','settings-dialog'].includes(yoruDialog.id)){yoruDialog.close();return true;}if($('details-dialog').open){closeDetails();return true;}if($('settings-dialog').open){$('settings-dialog').close();return true;}if(mobileRoute!=='home'||state.mode==='favorites'||state.filter!=='all'){mobileHome();return true;}return false;};
for(const dialog of document.querySelectorAll('dialog')){dialog.addEventListener('close',()=>document.body.classList.remove('sheet-open'));const show=dialog.showModal.bind(dialog);dialog.showModal=()=>{show();dialog.scrollTop=0;document.body.classList.add('sheet-open');};}
syncMobile();
function mobileHeroSaved(){const item=state.heroItem;const saved=item&&state.favorites.some(x=>x.id.provider===item.id.provider&&x.id.value===item.id.value);$('hero-list').textContent=saved?'✓ My list':'＋ My list';$('hero-list').setAttribute('aria-pressed',String(!!saved));}
const mobileHeroBase=updateHero;updateHero=function(item=null){mobileHeroBase(item);mobileHeroSaved();};
const mobileSavedBase=renderSaved;renderSaved=function(){mobileSavedBase();mobileHeroSaved();};
$('hero-list').onclick=async()=>{const item=state.heroItem;if(!item){setMode('favorites');return;}$('hero-list').disabled=true;try{state.favorites=await api('toggle_favorite',{item});renderSaved();}catch(e){status(e.message,true);}finally{$('hero-list').disabled=false;}};
mobileHeroSaved();
