package com.moviebox.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.view.WindowInsets;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.webkit.WebViewAssetLoader;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final String PAGE="https://appassets.androidplatform.net/assets/index.html";
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16));
    private WebView web;
    private FrameLayout root,video;
    private PlayerView playerView;
    private ExoPlayer player;
    private SharedPreferences prefs;
    private WatchHistory history;
    private ContentGate contentGate;
    private final android.os.Handler historyHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable historyTick=new Runnable(){public void run(){if(player!=null&&!destroyed){captureProgress(false);historyHandler.postDelayed(this,3000);}}};
    private boolean historyWarningShown=false;
    private void captureProgress(boolean ended){
        if(player==null||history==null)return;
        if(!ended&&player.getPlaybackState()!=Player.STATE_READY&&player.getPlaybackState()!=Player.STATE_ENDED)return;
        try{long duration=player.getDuration();history.record(player.getCurrentPosition(),duration==C.TIME_UNSET?0:duration,ended||player.getPlaybackState()==Player.STATE_ENDED);}
        catch(Exception error){history.end();if(!historyWarningShown&&!destroyed){historyWarningShown=true;android.widget.Toast.makeText(this,"Watch progress could not be saved on this device.",android.widget.Toast.LENGTH_LONG).show();}}
    }
    private boolean destroyed=false;
    private boolean foreground=false;
    private final java.util.Set<androidx.media3.exoplayer.offline.DownloadHelper> preparing=new java.util.HashSet<>();
    private final java.util.Set<String> preparingIds=new java.util.HashSet<>();
    private DownloadStore downloadStore;
    private boolean controlsLocked=false;
    private void toast(String text){android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_LONG).show();}

    private final Object storageLock=new Object();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(0);
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(11,11,11));setContentView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());var ime=insets.getInsets(WindowInsets.Type.ime());if(video==null)v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));else v.setPadding(0,0,0,0);return insets;});}
        prefs=getSharedPreferences("moviebox_mobile",MODE_PRIVATE);
        android.widget.TextView loading=new android.widget.TextView(this);
        loading.setText("Preparing Yoru...");loading.setTextColor(Color.WHITE);loading.setTextSize(18);loading.setGravity(Gravity.CENTER);
        root.addView(loading,new FrameLayout.LayoutParams(-1,-1));
        // Keep the UI responsive; no catalog or native requests are exposed until the filter is ready.
        workers.execute(()->{
            try(java.io.InputStream index=getAssets().open("adult_anime_index.json")){ContentSafety.loadAnimeIndex(readUtf8(index));}
            catch(Exception error){runOnUiThread(()->{if(!destroyed)new AlertDialog.Builder(this).setTitle("Content filter unavailable").setMessage("The offline content index could not load. Reinstall the complete Yoru APK; no catalog will be opened.").setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show();});return;}
            runOnUiThread(()->{if(!destroyed){root.removeView(loading);finishStartup();}});
        });
    }
    private void finishStartup() {
        contentGate=new ContentGate((action,payload)->new JSONObject(NativeBridge.nativeRequest(new JSONObject().put("action",action).put("payload",payload).toString())),new ContentGate.Storage(){public String read(){return prefs.getString("blocked_content_ids","[]");}public boolean write(String value){return prefs.edit().putString("blocked_content_ids",value).commit();}});

        try{history=new WatchHistory(new WatchHistory.Storage(){public String read(){return prefs.getString("history","[]");}public boolean write(String value){return prefs.edit().putString("history",value).commit();}});}
        catch(Exception error){new AlertDialog.Builder(this).setTitle("Saved history could not load").setMessage("Watch history is unavailable. Existing data was kept; favorites and settings remain usable.").setPositiveButton("OK",null).show();}

        try {JSONObject initialized=new JSONObject(NativeBridge.nativeInit(getFilesDir().getAbsolutePath()));
            if(!initialized.optBoolean("ok"))throw new IllegalStateException("Storage initialization failed");
        } catch(Throwable error){new AlertDialog.Builder(this).setTitle("Yoru could not start").setMessage("The native backend could not load on this device. Use the complete APK for Android 8 or newer, ARM/ARM64/x86_64.\n\n"+error.getClass().getSimpleName()).setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show();return;}
        try{downloadStore=DownloadStore.get(this);}catch(Exception offlineError){toast("Offline storage is unavailable. Streaming is still available.");}
        try {web=new WebView(this);}catch(RuntimeException error){new AlertDialog.Builder(this).setTitle("Android System WebView is unavailable").setMessage("Update or enable Android System WebView through your device's supported app store, then reopen Yoru.").setPositiveButton("Close",(d,w)->finish()).show();return;}root.addView(web,new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setMediaPlaybackRequiresUserGesture(true);
        web.setBackgroundColor(Color.rgb(13,12,17));web.setWebChromeClient(new WebChromeClient(){@Override public boolean onJsConfirm(WebView view,String url,String message,android.webkit.JsResult result){new AlertDialog.Builder(MainActivity.this).setTitle("Confirm action").setMessage(message).setPositiveButton("Confirm",(d,w)->result.confirm()).setNegativeButton("Cancel",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}});
        WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return assets.shouldInterceptRequest(request.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !PAGE.equals(request.getUrl().toString());}
            @Override public void onPageFinished(WebView view,String url){if(PAGE.equals(url)){view.requestFocus();if(getIntent().getBooleanExtra("openDownloads",false)){view.evaluateJavascript("window.openDownloads && window.openDownloads()",null);getIntent().removeExtra("openDownloads");}view.evaluateJavascript("typeof window.mobileReady === 'function'",ready->{if("true".equals(ready))view.evaluateJavascript("window.mobileReady()",null);else new AlertDialog.Builder(MainActivity.this).setTitle("The mobile interface could not load").setMessage("This preview needs an up-to-date Android System WebView. Update it through your device's supported app store, then reopen the app.").setPositiveButton("OK",null).show();});}}
        });
        web.addJavascriptInterface(new JsApi(),"TVNative");web.loadUrl(PAGE);
    }
    private static String readUtf8(java.io.InputStream input)throws java.io.IOException {java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;)bytes.write(buffer,0,n);return bytes.toString("UTF-8");}
    private static JSONObject ok(Object value)throws Exception{return new JSONObject().put("ok",true).put("data",value);}
    private static JSONObject error(String message){try{return new JSONObject().put("ok",false).put("error",message);}catch(Exception e){throw new IllegalStateException(e);}}
    private void reply(String id,JSONObject result){runOnUiThread(()->{if(!destroyed&&web!=null)web.evaluateJavascript("window.TVPlatform && window.TVPlatform.receive("+JSONObject.quote(id)+","+result+")",null);});}
    private final class JsApi {
        @JavascriptInterface public void request(String id,String action,String raw){
            if(raw==null||raw.length()>1024*1024||id.length()>64){reply(id,error("Request too large"));return;}
            try {workers.execute(()->{try{JSONObject p=new JSONObject(raw);JSONObject result;
                switch(action){
                    case "players" -> result=ok(new JSONArray().put(new JSONObject().put("id","android").put("label","Built-in player")));
                    case "settings","save_settings" -> result=settings(action,p);
                    case "favorites","toggle_favorite" -> result=favorites(action,p);
                    case "history" -> result=history==null?error("Watch history unavailable"):ok(history.list());
                    case "remove_history" -> result=history==null?error("Watch history unavailable"):ok(history.remove(p.getString("key")));
                    case "clear_history" -> result=history==null?error("Watch history unavailable"):ok(history.clear(p.optBoolean("confirm",false)));

                    case "downloads" -> {if(downloadStore==null)throw new java.io.IOException();result=ok(downloadStore.list());}
                    case "download" -> {if(downloadStore==null)throw new java.io.IOException();JSONObject resolved=contentGate.request("play",p);if(!resolved.optBoolean("ok")){reply(id,resolved);return;}JSONObject source=resolved.getJSONObject("data");JSONObject metadata=new JSONObject(p.toString()).put("approved_details",contentGate.details(p.getJSONObject("context"))).put("origin",source.getString("url")).put("headers",source.optJSONArray("headers"));downloadStore.guard(metadata);String mime=DownloadStore.probe(source);runOnUiThread(()->prepareDownload(id,source,metadata,mime));return;}
                    case "download_pause","download_resume","download_remove","download_clear" -> {if(downloadStore==null)throw new java.io.IOException();String key=p.optString("id");if(!action.equals("download_clear"))downloadStore.metadata(key);runOnUiThread(()->{try{if(!foreground||destroyed)throw new java.io.IOException();Class<YoruDownloadService> service=YoruDownloadService.class;if(action.equals("download_pause"))androidx.media3.exoplayer.offline.DownloadService.sendSetStopReason(MainActivity.this,service,key,1,false);else if(action.equals("download_resume")){downloadStore.guard(downloadStore.metadata(key));androidx.media3.exoplayer.offline.DownloadService.sendResumeDownloads(MainActivity.this,service,true);androidx.media3.exoplayer.offline.DownloadService.sendSetStopReason(MainActivity.this,service,key,0,true);}else if(action.equals("download_remove"))androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(MainActivity.this,service,key,true);else {if(!p.optBoolean("confirm"))throw new IllegalArgumentException();androidx.media3.exoplayer.offline.DownloadService.sendRemoveAllDownloads(MainActivity.this,service,true);}reply(id,ok(new JSONObject()));}catch(Exception e){reply(id,error("Download action could not complete. Reopen Downloads and try again."));}});return;}
                    case "play_offline" -> {if(downloadStore==null)throw new java.io.IOException();String key=p.getString("id");var saved=downloadStore.manager.getDownloadIndex().getDownload(key);if(saved==null||saved.state!=androidx.media3.exoplayer.offline.Download.STATE_COMPLETED){reply(id,error("Wait until this download finishes before playing offline."));return;}JSONObject metadata=downloadStore.metadata(key);downloadStore.guard(metadata);contentGate.guard(metadata.getJSONObject("item"));JSONObject source=new JSONObject().put("url",saved.request.uri.toString()).put("mime",saved.request.mimeType).put("_offline_id",key);runOnUiThread(()->{try{if(destroyed)throw new java.io.IOException();openPlayer(source,metadata.optString("audio_language"),metadata);reply(id,ok(new JSONObject().put("message","Playing downloaded video. No network fallback.")));}catch(Exception e){stopPlayer();reply(id,error("Offline video could not open. Check storage or download it again."));}});return;}
                    case "home","search","details","releases","subtitles" -> result=contentGate.request(action,p);
                    case "play" -> {JSONObject resolved=contentGate.request("play",p);
                        if(!resolved.optBoolean("ok")){reply(id,resolved);return;}
                        JSONObject source=resolved.getJSONObject("data");String audio=p.optString("audio_language","");
                        runOnUiThread(()->{try{if(destroyed){reply(id,error("Application is closing"));return;}openPlayer(source,audio,p);reply(id,ok(new JSONObject().put("message","Playing on your phone. Use Audio to inspect actual embedded tracks.").put("source_host",Uri.parse(source.optString("url","")).getHost())));}catch(Exception e){stopPlayer();reply(id,error("Cannot open this stream. Try another release."));}});return;
                    }
                    default -> result=error("Unknown app request");
                }if(result.optBoolean("ok")&&result.optJSONArray("data")!=null&&java.util.Arrays.asList("favorites","toggle_favorite","history","remove_history","clear_history").contains(action))result.put("data",contentGate.local(result.getJSONArray("data")));reply(id,result);
            }catch(Throwable e){reply(id,error(e instanceof ContentSafety.Blocked?ContentSafety.MESSAGE:e instanceof IllegalArgumentException?ContentSafety.UNVERIFIED:"Request failed. Try again or select another provider."));}});
            }catch(java.util.concurrent.RejectedExecutionException e){reply(id,error("Please wait for the current requests to finish."));}
        }
    }
    private JSONObject settings(String action,JSONObject p)throws Exception {
        synchronized(storageLock){JSONObject data=new JSONObject(prefs.getString("settings","{}"));
            if(!data.has("player"))data.put("player","android");if(!data.has("audio_language"))data.put("audio_language","");if(!data.has("quality"))data.put("quality","auto");if(!data.has("hide_cam"))data.put("hide_cam",false);
            if(action.equals("save_settings")){
                String audio=p.optString("audio_language",data.optString("audio_language")),quality=p.optString("quality",data.optString("quality"));
                if(!new java.util.HashSet<>(java.util.Arrays.asList("","hin","eng","tam","tel","ben","mal","kan","mar","pan","urd")).contains(audio)||!new java.util.HashSet<>(java.util.Arrays.asList("auto","2160","1080","720","480")).contains(quality))return error("Unsupported audio/quality preference");
                if(p.has("hide_cam") && !(p.get("hide_cam") instanceof Boolean))return error("Invalid CAM preference");
                data.put("player","android").put("audio_language",audio).put("quality",quality).put("hide_cam",p.has("hide_cam")?p.getBoolean("hide_cam"):data.optBoolean("hide_cam",false));
                if(!prefs.edit().putString("settings",data.toString()).commit())return error("Could not save preferences");
            }return ok(data);
        }
    }
    private JSONObject favorites(String action,JSONObject p)throws Exception {
        synchronized(storageLock){JSONArray items=new JSONArray(prefs.getString("favorites","[]"));
            if(action.equals("toggle_favorite")){
                JSONObject incoming=p.getJSONObject("item"),id=incoming.getJSONObject("id");contentGate.guard(incoming);
                if(incoming.optString("title").trim().isEmpty()||id.optString("value").trim().isEmpty()||!new java.util.HashSet<>(java.util.Arrays.asList("moviebox","fourkhdhub","dramachi","bdix_circleftp","bdix_dhakaflix")).contains(id.optString("provider")))return error("Invalid title");
                JSONArray next=new JSONArray();boolean found=false;
                for(int i=0;i<items.length();i++){JSONObject current=items.getJSONObject(i),other=current.getJSONObject("id");if(other.optString("provider").equals(id.optString("provider"))&&other.optString("value").equals(id.optString("value")))found=true;else next.put(current);}
                if(!found){JSONObject clean=new JSONObject();for(String key:new String[]{"id","title","year","media_type","poster_url","season_count"})if(incoming.has(key))clean.put(key,incoming.get(key));next.put(clean);}
                if(!prefs.edit().putString("favorites",next.toString()).commit())return error("Could not save favorites");items=next;
            }return ok(items);
        }
    }
    private void openPlayer(JSONObject source,String audio,JSONObject request)throws Exception {
        stopPlayer();historyWarningShown=false;String url=source.getString("url");Uri uri=Uri.parse(url);
        if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme())))throw new IllegalArgumentException("Unsupported stream");
        Map<String,String> headers=new LinkedHashMap<>();JSONArray array=source.optJSONArray("headers");if(array!=null)for(int i=0;i<array.length();i++){JSONArray pair=array.getJSONArray(i);headers.put(pair.getString(0),pair.getString(1));}
        boolean offline=source.has("_offline_id");
        androidx.media3.datasource.DataSource.Factory data=offline?downloadStore.offline():ScopedHttpSource.factory(url,headers);
        DefaultMediaSourceFactory media=new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,data));
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(media).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        var parameters=player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguages(AudioLanguage.preference(audio));
        int height=source.optInt("max_height",0);if(height>0)parameters.setMaxVideoSize(Integer.MAX_VALUE,height);
        player.setTrackSelectionParameters(parameters.build());
        MediaItem.Builder item=new MediaItem.Builder().setUri(uri);if(source.has("mime"))item.setMimeType(source.optString("mime"));if(offline){var saved=downloadStore.manager.getDownloadIndex().getDownload(source.getString("_offline_id"));if(saved==null)throw new java.io.IOException();item.setStreamKeys(saved.request.streamKeys);}
        String subtitle=source.optString("subtitle","");if(!subtitle.isEmpty())item.setSubtitleConfigurations(java.util.Collections.singletonList(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle)).setMimeType(AudioLanguage.subtitleMime(subtitle)).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()));
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);video=new FrameLayout(this);video.setBackgroundColor(Color.BLACK);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        playerView=new PlayerView(this);playerView.setUseController(true);playerView.setControllerAutoShow(true);playerView.setControllerShowTimeoutMs(5000);playerView.setPlayer(player);video.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        controlsLocked=false;
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(8),dp(4),dp(8),dp(4));actions.setGravity(Gravity.CENTER_VERTICAL);actions.setBackgroundColor(0xc0111111);
        Button back=playerButton("‹ Back",()->stopPlayer());actions.addView(back);
        android.widget.TextView title=new android.widget.TextView(this);title.setText(request.optJSONObject("item")==null?"Yoru player":request.optJSONObject("item").optString("title","Yoru player"));title.setTextColor(Color.WHITE);title.setTextSize(14);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);actions.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button fit=playerButton("Fit",()->{});fit.setOnClickListener(v->{if(playerView==null)return;boolean fill=playerView.getResizeMode()!=androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;playerView.setResizeMode(fill?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);fit.setText(fill?"Fill":"Fit");});actions.addView(fit);
        Button options=playerButton("Options",()->playerOptions());actions.addView(options);
        FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);video.addView(actions,bar);
        Button unlock=playerButton("Unlock controls",()->{});FrameLayout.LayoutParams unlockLayout=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);video.addView(unlock,unlockLayout);unlock.setVisibility(View.GONE);
        View shield=new View(this);video.addView(shield,new FrameLayout.LayoutParams(-1,-1));shield.setVisibility(View.GONE);shield.setOnTouchListener((v,event)->{unlock.setVisibility(View.VISIBLE);historyHandler.removeCallbacks(hideUnlock);historyHandler.postDelayed(hideUnlock,3000);return true;});unlock.bringToFront();
        unlock.setOnClickListener(v->{controlsLocked=false;shield.setVisibility(View.GONE);unlock.setVisibility(View.GONE);playerView.setUseController(true);playerView.showController();immersive();});
        lockControls=()->{if(playerView==null)return;controlsLocked=true;playerView.hideController();playerView.setUseController(false);actions.setVisibility(View.GONE);shield.setVisibility(View.VISIBLE);unlock.setVisibility(View.VISIBLE);unlock.requestFocus();historyHandler.postDelayed(hideUnlock,3000);immersive();};
        unlockButton=unlock;
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{actions.setVisibility(controlsLocked?View.GONE:visibility);});
        video.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){var cut=insets.getDisplayCutout();actions.setPadding(dp(8)+cut.getSafeInsetLeft(),dp(4)+cut.getSafeInsetTop(),dp(8)+cut.getSafeInsetRight(),dp(4));View controller=playerView==null?null:playerView.findViewById(androidx.media3.ui.R.id.exo_controller);if(controller!=null)controller.setPadding(cut.getSafeInsetLeft(),0,cut.getSafeInsetRight(),cut.getSafeInsetBottom());}return insets;});
        immersive();root.requestApplyInsets();
        web.setVisibility(View.GONE);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(history!=null)try{history.begin(request);}catch(Exception error){history.end();android.widget.Toast.makeText(this,"History unavailable for this title.",android.widget.Toast.LENGTH_LONG).show();}
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED)captureProgress(true);}
            @Override public void onIsPlayingChanged(boolean playing){if(!playing)captureProgress(false);}
            @Override public void onPlayerError(PlaybackException error){if(destroyed)return;new AlertDialog.Builder(MainActivity.this).setTitle("This stream could not play").setMessage("Try another release or quality. The provider may be unavailable, or this device may not support the codec.\n\n"+error.getErrorCodeName()).setPositiveButton("Back to title",(d,w)->stopPlayer()).setOnCancelListener(d->stopPlayer()).show();}});
        double requested=request.optDouble("resume_seconds",0);long resumeMs=Double.isFinite(requested)?(long)(Math.max(0,Math.min(requested,2592000))*1000):0;
        player.setMediaItem(item.build(),resumeMs);player.prepare();player.play();historyHandler.removeCallbacks(historyTick);historyHandler.postDelayed(historyTick,3000);playerView.requestFocus();playerView.showController();
    }
    private Button unlockButton;private Runnable lockControls;
    private final Runnable hideUnlock=()->{if(controlsLocked&&unlockButton!=null)unlockButton.setVisibility(View.GONE);};
    private void playerOptions(){
        if(player==null)return;
        String[] options=true?new String[]{"Audio tracks","Subtitles","Rotate screen","Lock controls"}:new String[]{"Audio tracks","Subtitles","Lock controls"};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Player options").setItems(options,(d,n)->{if(n==0)chooseTrack(C.TRACK_TYPE_AUDIO);else if(n==1)chooseTrack(C.TRACK_TYPE_TEXT);else if(true&&n==2){setRequestedOrientation(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);immersive();}else if(lockControls!=null)lockControls.run();}).setNegativeButton("Close",null).create();dialog.setOnDismissListener(d->immersive());dialog.show();
    }
    private void immersive(){if(video==null)return;root.setPadding(0,0,0,0);if(android.os.Build.VERSION.SDK_INT>=28){var attrs=getWindow().getAttributes();attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(attrs);}
        if(android.os.Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);var c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(android.view.WindowInsets.Type.systemBars());}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private void restoreSystemBars(){if(root==null)return;if(true){if(android.os.Build.VERSION.SDK_INT>=30){var c=getWindow().getInsetsController();if(c!=null)c.show(android.view.WindowInsets.Type.systemBars());}getWindow().getDecorView().setSystemUiVisibility(0);if(android.os.Build.VERSION.SDK_INT>=28){var attrs=getWindow().getAttributes();attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;getWindow().setAttributes(attrs);}root.requestApplyInsets();}}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);immersive();if(root!=null)root.requestApplyInsets();}
    @Override protected void onResume(){super.onResume();foreground=true;immersive();}
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("openDownloads",false)&&web!=null){web.evaluateJavascript("window.openDownloads && window.openDownloads()",null);intent.removeExtra("openDownloads");}}
    private void prepareDownload(String id,JSONObject source,JSONObject metadata,String mime){
      androidx.media3.exoplayer.offline.DownloadHelper helper=null;
      try{if(destroyed||!foreground)throw new java.io.IOException("Keep Yoru open while preparing a download.");
       String key=DownloadPolicy.id(metadata);var existing=downloadStore.manager.getDownloadIndex().getDownload(key);if(existing!=null||!preparingIds.add(key)){reply(id,error("This release is already in Downloads. Open Downloads to manage it."));return;}
       if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},41);
       var params=new androidx.media3.common.TrackSelectionParameters.Builder(this).setPreferredAudioLanguages(AudioLanguage.preference(metadata.optString("audio_language"))).setForceHighestSupportedBitrate(true);
       int height=source.optInt("max_height",0);if(height<=0){java.util.regex.Matcher q=java.util.regex.Pattern.compile("(2160|1440|1080|720|480)").matcher(metadata.getJSONObject("release").optString("quality"));height=q.find()?Integer.parseInt(q.group(1)):1080;}params.setMaxVideoSize(Integer.MAX_VALUE,height);
       var media=new MediaItem.Builder().setUri(source.getString("url")).setMimeType(mime).build();
       helper=androidx.media3.exoplayer.offline.DownloadHelper.forMediaItem(media,params.build(),new androidx.media3.exoplayer.DefaultRenderersFactory(this),DownloadStore.http(metadata));
       var pending=helper;preparing.add(pending);Runnable timeout=()->{if(preparing.remove(pending)){preparingIds.remove(key);pending.release();reply(id,error("Download preparation timed out. Try another release."));}};historyHandler.postDelayed(timeout,60000);
       helper.prepare(new androidx.media3.exoplayer.offline.DownloadHelper.Callback(){
        public void onPrepared(androidx.media3.exoplayer.offline.DownloadHelper ready){if(!preparing.remove(ready))return;historyHandler.removeCallbacks(timeout);preparingIds.remove(key);try{if(destroyed||!foreground)throw new java.io.IOException();downloadStore.guard(metadata);for(int period=0;period<ready.getPeriodCount();period++)for(var group:ready.getTracks(period).getGroups())for(int n=0;n<group.length;n++)if(group.getTrackFormat(n).drmInitData!=null)throw new java.io.IOException("Protected downloads are not supported");var request=ready.getDownloadRequest(key,metadata.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));androidx.media3.exoplayer.offline.DownloadService.sendResumeDownloads(MainActivity.this,YoruDownloadService.class,true);androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(MainActivity.this,YoruDownloadService.class,request,true);reply(id,ok(new JSONObject().put("message","Download queued. Open Downloads for progress.")));}catch(Exception e){reply(id,error("Download could not start. Keep the app open and try an unprotected release."));}finally{ready.release();}}
        public void onPrepareError(androidx.media3.exoplayer.offline.DownloadHelper failed,java.io.IOException e){if(!preparing.remove(failed))return;historyHandler.removeCallbacks(timeout);preparingIds.remove(key);failed.release();reply(id,error("This release cannot be downloaded. Live, protected or unavailable streams are not supported."));}
       });
      }catch(Exception e){if(helper!=null){preparing.remove(helper);helper.release();}try{preparingIds.remove(DownloadPolicy.id(metadata));}catch(Exception ignored){}reply(id,error(e instanceof ContentSafety.Blocked?ContentSafety.MESSAGE:"Download preparation failed. Try another release."));}
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private Button playerButton(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setMinWidth(dp(88));b.setOnClickListener(v->action.run());return b;}
    private void chooseTrack(int type){
        if(player==null)return;List<String> labels=new ArrayList<>();List<TrackSelectionOverride> choices=new ArrayList<>();
        labels.add(type==C.TRACK_TYPE_TEXT?"Off":"Auto / preferred language");choices.add(null);
        if(type==C.TRACK_TYPE_TEXT){labels.add("Auto");choices.add(null);}
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)){
            Format f=group.getTrackFormat(i);String label=AudioLanguage.label(f.language);if(f.label!=null&&!f.label.trim().isEmpty())label+=" · "+f.label;if(f.channelCount>0)label+=" · "+f.channelCount+" channels";
            if(group.isTrackSelected(i))label+=" ✓";labels.add(label);choices.add(new TrackSelectionOverride(group.getMediaTrackGroup(),i));
        }
        if(labels.size()==(type==C.TRACK_TYPE_TEXT?2:1)){new AlertDialog.Builder(this).setTitle(type==C.TRACK_TYPE_TEXT?"Subtitles":"Audio tracks").setMessage("No supported tracks are reported yet. Wait for playback to load, or try another release.").setPositiveButton("OK",null).show();return;}
        new AlertDialog.Builder(this).setTitle(type==C.TRACK_TYPE_TEXT?"Subtitle tracks":"Audio tracks").setItems(labels.toArray(new String[0]),(d,index)->{if(player==null)return;var p=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT&&index==0);TrackSelectionOverride override=choices.get(index);if(override!=null)p.setOverrideForType(override);player.setTrackSelectionParameters(p.build());}).setNegativeButton("Cancel",null).show();
    }
    private void stopPlayer(){historyHandler.removeCallbacks(hideUnlock);unlockButton=null;lockControls=null;controlsLocked=false;historyHandler.removeCallbacks(historyTick);captureProgress(false);if(history!=null)history.end();boolean wasPlaying=player!=null;if(playerView!=null){playerView.setPlayer(null);playerView=null;}if(player!=null){player.release();player=null;}if(video!=null){root.removeView(video);video=null;}restoreSystemBars();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(wasPlaying){setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);getWindow().getDecorView().setSystemUiVisibility(0);}if(web!=null){web.setVisibility(View.VISIBLE);web.requestFocus();web.evaluateJavascript("window.mobileRestore && window.mobileRestore()",null);}}
    @Override public void onBackPressed(){if(player!=null){stopPlayer();return;}if(web==null){finish();return;}web.evaluateJavascript("window.mobileBack ? window.mobileBack() : false",handled->{if(!"true".equals(handled))new AlertDialog.Builder(this).setTitle("Exit Yoru?").setPositiveButton("Exit",(d,w)->finish()).setNegativeButton("Stay",null).show();});}
    @Override protected void onStop(){foreground=false;super.onStop();if(player!=null){captureProgress(false);player.pause();}}
    @Override protected void onDestroy(){destroyed=true;for(var pending:preparing)pending.release();preparing.clear();preparingIds.clear();historyHandler.removeCallbacksAndMessages(null);workers.shutdownNow();if(contentGate!=null)contentGate.close();stopPlayer();if(web!=null){web.removeJavascriptInterface("TVNative");web.destroy();web=null;}super.onDestroy();}
}
