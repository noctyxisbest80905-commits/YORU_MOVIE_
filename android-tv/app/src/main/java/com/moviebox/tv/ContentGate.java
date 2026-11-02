package com.moviebox.tv;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Native Java gate: the WebView never receives unchecked provider catalog entries. */
final class ContentGate {
 interface Backend {JSONObject request(String action,JSONObject payload)throws Exception;}
 interface Storage {String read();boolean write(String value);}
 private final Backend backend;
 private final Storage storage;
 private final Set<String> denied=ConcurrentHashMap.newKeySet();
 private final Map<String,Cached> cache=new ConcurrentHashMap<>();
 private final ExecutorService checks=Executors.newFixedThreadPool(3);
 private static final class Cached {final long time=System.nanoTime();final String json;Cached(JSONObject value){json=value.toString();}}
 ContentGate(Backend backend,Storage storage){this.backend=backend;this.storage=storage;try{JSONArray a=new JSONArray(storage.read());for(int i=0;i<a.length();i++)denied.add(a.getString(i));}catch(Exception ignored){}}
 void close(){checks.shutdownNow();}
 private synchronized void remember(String key){if(key.isEmpty())return;denied.add(key);cache.remove(key);try{List<String> values=new ArrayList<>(denied);Collections.sort(values);if(values.size()>5000)values=values.subList(values.size()-5000,values.size());storage.write(new JSONArray(values).toString());}catch(Exception ignored){}}
 void guard(JSONObject value){guard(value,false);}
 void guard(JSONObject value,boolean anime){String key=ContentSafety.key(value);JSONObject ctx=value==null?null:value.optJSONObject("context");String ctxKey=ctx==null?"":ctx.optString("provider")+"\n"+ctx.optString("id");if(ContentSafety.blocked(value,anime)||(!key.isEmpty()&&denied.contains(key))||(!ctxKey.isEmpty()&&denied.contains(ctxKey))){remember(key);throw new ContentSafety.Blocked();}}
 JSONArray local(JSONArray values)throws Exception{JSONArray clean=new JSONArray();for(int i=0;i<values.length();i++)try{guard(values.optJSONObject(i));}catch(ContentSafety.Blocked ignored){}for(int i=0;i<values.length();i++){JSONObject value=values.optJSONObject(i);if(value==null)continue;try{guard(value);clean.put(value);}catch(ContentSafety.Blocked ignored){}}return clean;}
 private static JSONObject ok(Object v)throws Exception{return new JSONObject().put("ok",true).put("data",v);}
 private static JSONObject context(JSONObject value)throws Exception{JSONObject id=value.optJSONObject("id");if(id==null||id.optString("provider").isEmpty()||id.optString("value").isEmpty())throw new IllegalArgumentException(ContentSafety.UNVERIFIED);return new JSONObject().put("provider",id.getString("provider")).put("id",id.getString("value"));}
 JSONObject details(JSONObject p)throws Exception{
  String key=p.optString("provider")+"\n"+p.optString("id");if(denied.contains(key))throw new ContentSafety.Blocked();Cached hit=cache.get(key);if(hit!=null&&System.nanoTime()-hit.time<TimeUnit.MINUTES.toNanos(5))return new JSONObject(hit.json);
  JSONObject request=new JSONObject().put("provider",p.optString("provider")).put("id",p.optString("id"));JSONObject response=backend.request("details",request);JSONObject value=response.optBoolean("ok")?response.optJSONObject("data"):null;
  if(value==null||!key.equals(ContentSafety.key(value))||value.optString("title").trim().isEmpty())throw new IllegalArgumentException(ContentSafety.UNVERIFIED);
  guard(value);boolean metadata=false;for(String k:Arrays.asList("description","synopsis","summary","tagline","genres","genre","tags")){Object v=value.opt(k);if(v instanceof String&&!((String)v).trim().isEmpty())metadata=true;if(v instanceof JSONArray&&((JSONArray)v).length()>0)metadata=true;}
  if(!metadata)throw new IllegalArgumentException(ContentSafety.UNVERIFIED);
  value=new JSONObject(value.toString());for(String field:Arrays.asList("dubs","related","recommendations"))if(value.optJSONArray(field)!=null)value.put(field,local(value.getJSONArray(field)));
  if(cache.size()>=500)cache.remove(cache.keySet().iterator().next());cache.put(key,new Cached(value));return new JSONObject(value.toString());
 }
 private JSONArray catalog(JSONArray original,boolean anime)throws Exception{
  JSONArray items=local(original);List<Callable<Integer>> tasks=new ArrayList<>();int limit=Math.min(items.length(),100);
  for(int i=0;i<limit;i++){final int index=i;tasks.add(()->{try{JSONObject item=items.getJSONObject(index);guard(item,anime);details(context(item));guard(item,anime);return index;}catch(Exception denied){return -1;}});}
  List<Future<Integer>> results=checks.invokeAll(tasks,30,TimeUnit.SECONDS);Set<Integer> allowed=new HashSet<>();for(Future<Integer> f:results)if(!f.isCancelled())try{int i=f.get();if(i>=0)allowed.add(i);}catch(Exception ignored){}
  JSONArray clean=new JSONArray();for(int i=0;i<items.length();i++)if(allowed.contains(i))clean.put(items.getJSONObject(i));return local(clean);
 }
 private static JSONArray featuredSubset(JSONArray values,String query)throws Exception{
  Set<String> names=new HashSet<>();for(String n:Arrays.asList("Bleach","Attack on Titan","Sword Art Online","Dragon Ball Z","One Piece","Naruto","Demon Slayer","Jujutsu Kaisen"))names.add(ContentSafety.normalize(n));
  String q=ContentSafety.normalize(query);JSONArray out=new JSONArray();if(!names.contains(q))return out;
  for(int i=0;i<values.length()&&out.length()<4;i++){JSONObject v=values.optJSONObject(i);if(v==null)continue;String title=ContentSafety.normalize(v.optString("title"));if(q.equals(ContentSafety.normalize("One Piece"))&&(v.optString("year").equals("2023")||title.contains("netflix")))continue;if(title.equals(q)||title.startsWith(q+" "))out.put(v);}return out;
 }
 private static String releaseKey(JSONObject v){StringBuilder b=new StringBuilder();for(String k:Arrays.asList("provider","resource_id","filename","quality"))b.append(v.optString(k)).append('\n');return b.toString();}
 JSONObject request(String action,JSONObject input)throws Exception{
  JSONObject p=new JSONObject(input.toString());if(action.equals("search")&&(ContentSafety.blockedText(p.optString("query"))||ContentSafety.contains(p.optString("query"),"harem")))throw new ContentSafety.Blocked();if(action.equals("details"))return ok(details(p));
  if(action.equals("play")){guard(p.optJSONObject("item"));guard(p.optJSONObject("release"));JSONObject ctx=p.optJSONObject("context");if(ctx==null)throw new IllegalArgumentException(ContentSafety.UNVERIFIED);details(ctx);JSONObject approved=request("releases",ctx);JSONArray releases=approved.optBoolean("ok")?approved.optJSONArray("data"):null;JSONObject incoming=p.optJSONObject("release"),match=null;if(releases!=null&&incoming!=null)for(int i=0;i<releases.length();i++){JSONObject release=releases.getJSONObject(i);if(releaseKey(release).equals(releaseKey(incoming))){match=release;break;}}if(match==null)throw new IllegalArgumentException(ContentSafety.UNVERIFIED);p.put("release",match);input.put("release",match);return backend.request("resolve",p);}
  if(action.equals("releases")||action.equals("subtitles"))details(p);
  JSONObject response=backend.request(action,p);if(!response.optBoolean("ok"))return response;
  if(response.optJSONArray("data")!=null){JSONArray values=response.getJSONArray("data");if(action.equals("home")||action.equals("search")){if(action.equals("search")&&p.optBoolean("featured_anime",false))values=featuredSubset(values,p.optString("query"));return ok(catalog(values,action.equals("search")&&(p.optBoolean("anime_context",false)||p.optString("query").toLowerCase(Locale.ROOT).contains("anime")||p.optString("query").toLowerCase(Locale.ROOT).contains("animation"))));}if(action.equals("releases"))return ok(local(values));}
  return response;
 }
}
