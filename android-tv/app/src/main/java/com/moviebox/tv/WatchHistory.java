package com.moviebox.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;

/** Local metadata only: never stores stream URLs, auth headers, subtitles or source tokens. */
final class WatchHistory {
    interface Storage { String read(); boolean write(String value); }
    private final Storage storage;
    private JSONArray items;
    private JSONObject active;
    WatchHistory(Storage storage) throws Exception { this.storage=storage; items=new JSONArray(storage.read()); }
    synchronized JSONArray list() throws Exception { return new JSONArray(items.toString()); }
    private void save(JSONArray next) throws Exception {
        if(!storage.write(next.toString()))throw new IllegalStateException("History could not be saved");
        items=next;
    }
    static String key(JSONObject ctx) throws Exception {
        String raw=ctx.getString("provider")+"\n"+ctx.getString("id")+"\n"+ctx.optInt("season",0)+"\n"+ctx.optInt("episode",0);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder();for(byte b:digest)result.append(String.format("%02x",b&255));return result.toString();
    }
    synchronized void begin(JSONObject request) throws Exception {
        active=null;JSONObject input=request.getJSONObject("item"),id=input.getJSONObject("id"),ctx=request.getJSONObject("context");
        String provider=ctx.getString("provider");
        if(!new HashSet<>(Arrays.asList("moviebox","fourkhdhub","dramachi","bdix_circleftp","bdix_dhakaflix")).contains(provider)||!provider.equals(id.getString("provider"))||ctx.getString("id").trim().isEmpty()||id.getString("value").trim().isEmpty()||input.getString("title").trim().isEmpty()||ctx.optInt("season",0)<0||ctx.optInt("episode",0)<0)throw new IllegalArgumentException("Invalid history context");
        JSONObject item=new JSONObject();for(String field:new String[]{"id","title","year","media_type","poster_url","season_count"})if(input.has(field))item.put(field,input.get(field));
        JSONObject context=new JSONObject().put("provider",provider).put("id",ctx.getString("id")).put("season",ctx.optInt("season",0)).put("episode",ctx.optInt("episode",0));
        JSONObject release=request.optJSONObject("release"),selection=new JSONObject();
        if(release!=null)for(String field:new String[]{"resource_id","filename","quality","language"})if(release.has(field))selection.put(field,release.get(field));
        active=new JSONObject().put("key",key(context)).put("item",item).put("context",context).put("audio_language",request.optString("audio_language","")).put("player","android").put("selection",selection).put("position",0).put("duration",0).put("completed",false).put("tracking","active");
    }
    synchronized void record(long positionMs,long durationMs,boolean ended) throws Exception {
        if(active==null||positionMs<0)return;
        double seconds=positionMs/1000.0,duration=Math.max(0,durationMs)/1000.0;
        if(seconds<1&&!ended)return;
        if(duration>0)seconds=Math.min(seconds,duration);
        active.put("position",seconds).put("duration",duration).put("completed",ended||(duration>0&&seconds>=duration*.95)).put("updated_at",System.currentTimeMillis()/1000.0);
        JSONArray next=new JSONArray().put(new JSONObject(active.toString()));String key=active.getString("key");
        for(int i=0;i<items.length()&&next.length()<200;i++){JSONObject row=items.getJSONObject(i);if(!key.equals(row.optString("key")))next.put(row);}
        save(next);
    }
    synchronized void end(){active=null;}
    synchronized JSONArray remove(String key) throws Exception {
        JSONArray next=new JSONArray();for(int i=0;i<items.length();i++){JSONObject row=items.getJSONObject(i);if(!key.equals(row.optString("key")))next.put(row);}
        save(next);if(active!=null&&key.equals(active.optString("key")))active=null;return list();
    }
    synchronized JSONArray clear(boolean confirmed) throws Exception {
        if(!confirmed)throw new IllegalArgumentException("Clear history needs confirmation");save(new JSONArray());active=null;return list();
    }
}
