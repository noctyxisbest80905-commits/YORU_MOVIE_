package com.moviebox.tv;
import android.content.Context;
import android.net.Uri;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

final class DownloadStore {
 private static DownloadStore instance;
 static synchronized DownloadStore get(Context context)throws Exception{if(instance==null)instance=new DownloadStore(context.getApplicationContext());return instance;}
 private final File cacheDirectory;final SimpleCache cache;final DownloadManager manager;private final Context context;private final ExecutorService network=Executors.newFixedThreadPool(3);
 private DownloadStore(Context context)throws Exception {
  this.context=context;
  if(ContentSafety.ANIME_TITLES.isEmpty())try(InputStream input=context.getAssets().open("adult_anime_index.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=input.read(b))!=-1;)bytes.write(b,0,n);ContentSafety.loadAnimeIndex(bytes.toString("UTF-8"));}
  File parent=context.getExternalFilesDir("offline");if(parent==null)parent=new File(context.getFilesDir(),"offline");if(!parent.exists()&&!parent.mkdirs())throw new IOException("Offline storage is unavailable");
  cacheDirectory=new File(parent,"video-cache");cache=new SimpleCache(cacheDirectory,new NoOpCacheEvictor(),new StandaloneDatabaseProvider(context));
  StandaloneDatabaseProvider database=new StandaloneDatabaseProvider(context);
  manager=new DownloadManager(context,new DefaultDownloadIndex(database),request->{
   JSONObject metadata;try{metadata=new JSONObject(new String(request.data,StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalArgumentException("Invalid offline request",e);}
   DataSource.Factory http=http(metadata);
   Downloader delegate=new DefaultDownloaderFactory(new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(http),network).createDownloader(request);
   return new Downloader(){public void download(Downloader.ProgressListener listener)throws IOException,InterruptedException{try{guard(metadata);}catch(Exception blocked){throw new IOException("Blocked by the content filter",blocked);}delegate.download(listener);}public void cancel(){delegate.cancel();}public void remove(){delegate.remove();}};
  });manager.setMaxParallelDownloads(2);manager.setMinRetryCount(2);
 }
 static Map<String,String> headers(JSONObject metadata){Map<String,String> map=new LinkedHashMap<>();JSONArray a=metadata.optJSONArray("headers");if(a!=null)for(int i=0;i<a.length();i++){JSONArray pair=a.optJSONArray(i);if(pair!=null&&pair.length()==2)map.put(pair.optString(0),pair.optString(1));}return map;}
 static DataSource.Factory http(JSONObject metadata){return ScopedHttpSource.factory(metadata.optString("origin"),headers(metadata));}
 CacheDataSource.Factory offline(){return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null).setCacheWriteDataSinkFactory(null);}
 JSONObject metadata(String id)throws Exception{Download d=manager.getDownloadIndex().getDownload(id);if(d==null)throw new IOException("Download not found");return new JSONObject(new String(d.request.data,StandardCharsets.UTF_8));}
 void guard(JSONObject m)throws Exception{
  for(String field:Arrays.asList("item","release","approved_details"))if(ContentSafety.blocked(m.optJSONObject(field)))throw new ContentSafety.Blocked();
  JSONArray denied=new JSONArray(context.getSharedPreferences("moviebox_"+(context.getPackageName().endsWith(".tv")?"tv":"mobile"),Context.MODE_PRIVATE).getString("blocked_content_ids","[]"));JSONObject c=m.optJSONObject("context");String ctx=c==null?"":c.optString("provider")+"\n"+c.optString("id");String key=ContentSafety.key(m.optJSONObject("item"));for(int i=0;i<denied.length();i++)if(denied.optString(i).equals(ctx)||denied.optString(i).equals(key))throw new ContentSafety.Blocked();
 }
 JSONObject list()throws Exception {
  JSONArray out=new JSONArray();try(DownloadCursor cursor=manager.getDownloadIndex().getDownloads()){
   while(cursor.moveToNext()){
    Download d=cursor.getDownload();JSONObject m;try{m=new JSONObject(new String(d.request.data,StandardCharsets.UTF_8));guard(m);}catch(Exception e){continue;}
    String state=d.state==Download.STATE_COMPLETED?"completed":d.state==Download.STATE_FAILED?"failed":d.state==Download.STATE_STOPPED?"paused":d.state==Download.STATE_REMOVING?"removing":d.state==Download.STATE_DOWNLOADING?"downloading":"queued";
    float percent=d.getPercentDownloaded();out.put(new JSONObject().put("id",d.request.id).put("item",m.getJSONObject("item")).put("context",m.getJSONObject("context")).put("quality",m.optJSONObject("release")==null?"":m.optJSONObject("release").optString("quality")).put("audio_language",m.optString("audio_language")).put("state",state).put("bytes",d.getBytesDownloaded()).put("total",Math.max(-1,d.contentLength)).put("percent",Float.isFinite(percent)?percent:-1).put("updated_at",d.updateTimeMs));
   }
  }
  return new JSONObject().put("items",out).put("used_bytes",cache.getCacheSpace()).put("free_bytes",Math.max(0,new android.os.StatFs(cacheDirectory.getAbsolutePath()).getAvailableBytes()));
 }
 static String probe(JSONObject source)throws Exception {
  String url=source.getString("url");JSONObject metadata=new JSONObject().put("origin",url).put("headers",source.optJSONArray("headers"));DataSource data=http(metadata).createDataSource();
  try{data.open(new DataSpec.Builder().setUri(Uri.parse(url)).setLength(4096).build());ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[1024];while(bytes.size()<4096){int n=data.read(b,0,Math.min(b.length,4096-bytes.size()));if(n<0)break;bytes.write(b,0,n);}String type="";for(Map.Entry<String,List<String>> e:data.getResponseHeaders().entrySet())if(e.getKey()!=null&&e.getKey().equalsIgnoreCase("Content-Type")&&!e.getValue().isEmpty())type=e.getValue().get(0);return DownloadPolicy.inferMime(url,type,bytes.toByteArray());}finally{data.close();}
 }
}
