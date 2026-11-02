'use strict';
(()=>{
 function visible(el){if(!el||el.disabled||el.tabIndex<0||!el.getClientRects().length)return false;const style=getComputedStyle(el);return style.visibility!=='hidden'&&style.display!=='none';}
 function scope(){return document.querySelector('dialog[open]')||document;}
 function controls(){return [...scope().querySelectorAll('button,input,select,[tabindex]')].filter(visible);}
 function focus(el){if(!visible(el))return;el.focus({preventScroll:true});el.scrollIntoView({block:'nearest',inline:'nearest',behavior:'instant'});}
 function openSearch(){document.body.classList.add('tv-search-open');focus(document.getElementById('query'));}
 document.getElementById('tv-search').onclick=openSearch;
 document.getElementById('hero-play').addEventListener('click',()=>{if(!state.heroItem)openSearch();});
 document.getElementById('nav-search').onclick=()=>{state.query='';document.getElementById('query').value='';document.body.classList.remove('tv-search-open');setMode();updateHero(state.homeItems[0]||null);};
 document.addEventListener('focusin',event=>{if(visible(event.target))event.target.scrollIntoView({block:'nearest',inline:'nearest',behavior:'instant'});});
 document.addEventListener('keydown',event=>{
  const directions={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};const direction=directions[event.key];if(!direction)return;
  const current=document.activeElement;if(current?.tagName==='INPUT'&&['ArrowLeft','ArrowRight'].includes(event.key))return;
  event.preventDefault();let candidates=controls();if(!visible(current)||!candidates.includes(current)){focus(candidates[0]);return;}
  const r=current.getBoundingClientRect(),x=r.x+r.width/2,y=r.y+r.height/2;
  const row=current.closest('.poster-row');
  if(direction[0]&&row)candidates=candidates.filter(el=>row.contains(el));
  else if(direction[0]&&current.closest('.card'))candidates=candidates.filter(el=>{const b=el.getBoundingClientRect();return el.classList.contains('poster-button')&&b.top<r.bottom&&b.bottom>r.top;});
  let best=null,bestScore=Infinity;
  for(const el of candidates){if(el===current)continue;const b=el.getBoundingClientRect(),dx=b.x+b.width/2-x,dy=b.y+b.height/2-y;const primary=direction[0]?dx*direction[0]:dy*direction[1];const secondary=Math.abs(direction[0]?dy:dx);if(primary<2)continue;
   // Favor aligned items over diagonals, while preserving cross-row navigation.
   const overlap=direction[0]?(b.top<r.bottom&&b.bottom>r.top):(b.left<r.right&&b.right>r.left);
   const score=primary+(overlap?secondary*.08:secondary*2.5+1000);if(score<bestScore){best=el;bestScore=score;}
  }if(best)focus(best);
 });
 window.tvInitialFocus=()=>focus(document.getElementById('nav-search'));
 window.tvRestoreFocus=()=>{if(typeof refreshWatchHistory==='function')refreshWatchHistory(true);focus(document.querySelector('dialog[open] #play')||document.querySelector('dialog[open] button')||document.getElementById('nav-search'));};
 window.tvBack=()=>{
  const dialog=document.querySelector('dialog[open]');if(dialog){const close=dialog.querySelector('.icon-button');if(close)close.click();else dialog.close();return true;}
  if(document.body.classList.contains('tv-search-open')){document.body.classList.remove('tv-search-open');document.activeElement?.blur();focus(document.getElementById('tv-search'));return true;}
  if(state.mode==='favorites'||state.filter!=='all'||state.query){document.getElementById('brand-home').click();focus(document.getElementById('nav-search'));return true;}return false;
 };
 new MutationObserver(()=>{document.body.classList.toggle('tv-home-feed',!document.getElementById('home-rows').hidden);if(document.activeElement===document.body||!visible(document.activeElement)){const dialog=document.querySelector('dialog[open]');focus(dialog?controls()[0]:document.getElementById('nav-search'));}}).observe(document.body,{childList:true,subtree:true});
 document.body.classList.toggle('tv-home-feed',!document.getElementById('home-rows').hidden);
 window.tvInitialFocus();
})();
