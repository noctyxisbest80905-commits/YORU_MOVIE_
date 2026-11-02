'use strict';
(()=>{
 const pending=new Map();let counter=0;const session=Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,8);
 window.TVPlatform={
  dispatch(action,payload={}){return new Promise((resolve,reject)=>{
   if(!window.TVNative?.request){reject(new Error('Native Android app bridge unavailable. Open the installed APK.'));return;}
   const id=session+'-'+(++counter);const timer=setTimeout(()=>{pending.delete(id);reject(new Error('Request timed out. Try another provider.'));},115000);
   pending.set(id,{resolve,reject,timer});try{window.TVNative.request(id,action,JSON.stringify(payload));}catch(error){clearTimeout(timer);pending.delete(id);reject(error);}
  });},
  receive(id,result){const item=pending.get(id);if(!item)return;pending.delete(id);clearTimeout(item.timer);item.resolve(result);}
 };
 window.pywebview={api:{dispatch:(action,payload)=>window.TVPlatform.dispatch(action,payload)}};
})();
