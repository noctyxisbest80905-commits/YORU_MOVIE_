package com.moviebox.tv;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
public class DownloadPolicyTest {
 private byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 @Test public void detectsHls()throws Exception{assertEquals("application/x-mpegURL",DownloadPolicy.inferMime("https://video.example/signed","application/octet-stream",bytes("#EXTM3U\n#EXT-X-TARGETDURATION:10")));}
 @Test public void detectsDash()throws Exception{assertEquals("application/dash+xml",DownloadPolicy.inferMime("https://video.example/signed","application/xml",bytes("<?xml version=\"1.0\"?><MPD type=\"static\">")));}
 @Test public void detectsProgressive()throws Exception{assertEquals("video/mp4",DownloadPolicy.inferMime("https://video.example/signed","application/octet-stream",new byte[]{0,0,0,20,'f','t','y','p'}));}
 @Test public void rejectsHtmlEvenWithVideoExtension()throws Exception{assertThrows(java.io.IOException.class,()->DownloadPolicy.inferMime("https://video.example/movie.mp4","text/html",bytes("<html>Sign in</html>")));}
 @Test public void rejectsProtectedDash()throws Exception{assertThrows(java.io.IOException.class,()->DownloadPolicy.inferMime("https://video.example/movie.mpd","application/dash+xml",bytes("<MPD><ContentProtection/></MPD>")));}
 @Test public void rejectsSampleAes()throws Exception{assertThrows(java.io.IOException.class,()->DownloadPolicy.inferMime("https://video.example/movie.m3u8","application/x-mpegurl",bytes("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES")));}
 @Test public void rejectsUnknownPages()throws Exception{assertThrows(java.io.IOException.class,()->DownloadPolicy.inferMime("https://video.example/download","text/plain",bytes("A download page")));}
 @Test public void rejectsNonNetworkSchemes(){for(String s:new String[]{"file:///data/private","content://provider/media","javascript:alert(1)","https:///missing-host"})assertFalse(s,DownloadPolicy.http(s));assertTrue(DownloadPolicy.http("https://example.org/video"));}
 @Test public void stableIdentitySeparatesEpisodeAndRelease()throws Exception{JSONObject p=new JSONObject("{\"context\":{\"provider\":\"moviebox\",\"id\":\"show\",\"season\":1,\"episode\":1},\"release\":{\"resource_id\":\"abc\",\"quality\":\"720p\",\"filename\":\"movie.mp4\"}}");String a=DownloadPolicy.id(p);assertEquals(64,a.length());assertEquals(a,DownloadPolicy.id(new JSONObject(p.toString())));p.getJSONObject("context").put("episode",2);assertNotEquals(a,DownloadPolicy.id(p));p.getJSONObject("context").put("episode",1);p.getJSONObject("release").put("quality","1080p");assertNotEquals(a,DownloadPolicy.id(p));}
 @Test public void seekClamps(){assertEquals(0,DownloadPolicy.seek(3000,100000,-10000));assertEquals(100000,DownloadPolicy.seek(99000,100000,10000));assertEquals(Long.MAX_VALUE,DownloadPolicy.seek(Long.MAX_VALUE-5,0,10));}
}
