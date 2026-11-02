package com.moviebox.tv;
import android.app.*;
import android.content.Intent;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.Scheduler;
import java.util.List;

public final class YoruDownloadService extends DownloadService {
 private DownloadNotificationHelper notifications;
 public YoruDownloadService(){super(301,1000,"yoru-downloads",R.string.download_channel_name,0);}
 @Override protected androidx.media3.exoplayer.offline.DownloadManager getDownloadManager(){try{return DownloadStore.get(this).manager;}catch(Exception e){throw new IllegalStateException("Offline downloads are unavailable",e);}}
 @Override public void onTimeout(int startId,int foregroundServiceType){try{DownloadStore.get(this).manager.pauseDownloads();}catch(Exception ignored){}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 @Override protected Scheduler getScheduler(){return null;}
 @Override protected Notification getForegroundNotification(List<Download> downloads,int unmet){
  if(notifications==null)notifications=new DownloadNotificationHelper(this,"yoru-downloads");Intent open=new Intent(this,MainActivity.class).putExtra("openDownloads",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent action=PendingIntent.getActivity(this,31,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return notifications.buildProgressNotification(this,R.drawable.ic_download,action,"Open Yoru → Downloads for progress and controls",downloads,unmet);
 }
}
