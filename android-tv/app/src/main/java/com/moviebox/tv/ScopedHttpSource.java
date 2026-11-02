package com.moviebox.tv;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import java.io.*;
import java.net.*;
import java.util.*;

/** Applies sensitive headers to their original origin on EVERY redirect and segment. */
final class ScopedHttpSource extends BaseDataSource {
 static DataSource.Factory factory(String origin,Map<String,String> headers){return ()->new ScopedHttpSource(origin,headers);}
 private final String origin;private final Map<String,String> supplied;private HttpURLConnection connection;private InputStream input;private Uri uri;private long remaining;private boolean started;
 ScopedHttpSource(String origin,Map<String,String> headers){super(true);this.origin=origin;this.supplied=new LinkedHashMap<>(headers);}
 @Override public long open(DataSpec spec)throws IOException {
  transferInitializing(spec);if(spec.httpMethod!=DataSpec.HTTP_METHOD_GET&&spec.httpMethod!=DataSpec.HTTP_METHOD_HEAD)throw new IOException("Unsupported media request");String target=spec.uri.toString();
  for(int redirects=0;redirects<=5;redirects++){
   if(!DownloadPolicy.http(target))throw new IOException("Unsupported video link");
   connection=(HttpURLConnection)new URL(target).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestMethod(spec.httpMethod==DataSpec.HTTP_METHOD_HEAD?"HEAD":"GET");
   connection.setRequestProperty("User-Agent","Yoru-Android/0.4");
   for(Map.Entry<String,String> e:HeaderPolicy.headers(origin,target,supplied,spec.httpRequestHeaders).entrySet())connection.setRequestProperty(e.getKey(),e.getValue());
   connection.setRequestProperty("Accept-Encoding","identity");if(spec.position>0||spec.length!=C.LENGTH_UNSET)connection.setRequestProperty("Range","bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":spec.position+spec.length-1));
   int code=connection.getResponseCode();if(code>=300&&code<=399){String location=connection.getHeaderField("Location");connection.disconnect();connection=null;if(location==null)throw new IOException("Missing video redirect");String next=new URL(new URL(target),location).toString();if(new URL(target).getProtocol().equalsIgnoreCase("https")&&new URL(next).getProtocol().equalsIgnoreCase("http"))throw new IOException("Insecure video redirect refused");target=next;continue;}
   if(code<200||code>=300){connection.disconnect();connection=null;throw new IOException("Video server returned HTTP "+code);}
   if(code==206){String range=connection.getHeaderField("Content-Range");if(range==null||!range.toLowerCase(java.util.Locale.ROOT).startsWith("bytes "+spec.position+"-"))throw new IOException("Invalid server resume range");}uri=Uri.parse(target);input=connection.getInputStream();long length=connection.getContentLengthLong();long skip=code==200?spec.position:0;
   if(skip>0){byte[] buffer=new byte[8192];long left=skip;while(left>0){int n=input.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)throw new EOFException("Video does not support this resume position");left-=n;}}
   long available=length>=0?Math.max(0,length-skip):C.LENGTH_UNSET;remaining=spec.length!=C.LENGTH_UNSET?(available==C.LENGTH_UNSET?spec.length:Math.min(spec.length,available)):available;started=true;transferStarted(spec);return remaining;
  }throw new IOException("Too many video redirects");
 }
 @Override public int read(byte[] buffer,int offset,int length)throws IOException{if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;int n=input.read(buffer,offset,remaining==C.LENGTH_UNSET?length:(int)Math.min(remaining,length));if(n<0){if(remaining>0)throw new EOFException("Video download ended before the reported length");return C.RESULT_END_OF_INPUT;}if(remaining!=C.LENGTH_UNSET)remaining-=n;bytesTransferred(n);return n;}
 @Override public Uri getUri(){return uri;}
 @Override public Map<String,List<String>> getResponseHeaders(){return connection==null?Collections.emptyMap():connection.getHeaderFields();}
 @Override public void close()throws IOException{try{if(input!=null)input.close();}finally{input=null;if(connection!=null)connection.disconnect();connection=null;uri=null;if(started){started=false;transferEnded();}}}
}
