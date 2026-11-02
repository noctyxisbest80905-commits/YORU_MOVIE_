package com.moviebox.tv;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;

final class DownloadPolicy {
 static String inferMime(String url,String contentType,byte[] sample)throws java.io.IOException {
  String type=(contentType==null?"":contentType).split(";",2)[0].trim().toLowerCase(Locale.ROOT);
  String prefix=new String(sample,StandardCharsets.UTF_8).trim();String lower=prefix.toLowerCase(Locale.ROOT);
  if(type.contains("html")||type.contains("json")||lower.startsWith("<!doctype html")||lower.startsWith("<html"))throw new java.io.IOException("The server returned a page, not a downloadable video.");
  if(lower.contains("contentprotection")||lower.contains("keyformat=\"com.")||lower.contains("method=sample-aes"))throw new java.io.IOException("Protected downloads are not supported.");
  if(prefix.startsWith("#EXTM3U")||type.contains("mpegurl"))return "application/x-mpegURL";
  if(lower.contains("<mpd")||type.contains("dash+xml"))return "application/dash+xml";
  String path;try{path=URI.create(url).getPath().toLowerCase(Locale.ROOT);}catch(Exception e){throw new java.io.IOException("Invalid video link");}
  if(path.endsWith(".m3u8"))return "application/x-mpegURL";
  if(path.endsWith(".mpd"))return "application/dash+xml";
  if(type.startsWith("video/")||type.startsWith("audio/"))return type;
  if(path.matches(".*\\.(mp4|m4v|mkv|webm|ts|mov|mp3|m4a|aac)$"))return path.endsWith(".webm")?"video/webm":"video/mp4";
  if(sample.length>=8&&sample[4]=='f'&&sample[5]=='t'&&sample[6]=='y'&&sample[7]=='p')return "video/mp4";
  if(sample.length>=4&&(sample[0]&255)==0x1a&&(sample[1]&255)==0x45&&(sample[2]&255)==0xdf&&(sample[3]&255)==0xa3)return "video/x-matroska";
  throw new java.io.IOException("This source did not identify a supported downloadable video. Try another release.");
 }
 static boolean http(String url){try{URI u=URI.create(url);return u.getHost()!=null&&(u.getScheme().equalsIgnoreCase("https")||u.getScheme().equalsIgnoreCase("http"));}catch(Exception e){return false;}}
 static String id(JSONObject p)throws Exception {
  JSONObject c=p.getJSONObject("context"),r=p.getJSONObject("release");String raw=c.getString("provider")+"\n"+c.getString("id")+"\n"+c.optInt("season",0)+"\n"+c.optInt("episode",0)+"\n"+r.optString("resource_id")+"\n"+r.optString("quality")+"\n"+r.optString("filename");
  byte[] bytes=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
 }
 static long seek(long position,long duration,long delta){long target=delta>0&&position>Long.MAX_VALUE-delta?Long.MAX_VALUE:Math.max(0,position+delta);return duration>0?Math.min(duration,target):target;}
 private DownloadPolicy(){}
}
