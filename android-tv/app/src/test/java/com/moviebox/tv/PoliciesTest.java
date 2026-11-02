package com.moviebox.tv;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;
public class PoliciesTest {
    @Test public void hindiAndEnglishPreferences(){assertArrayEquals(new String[]{"hi","hin"},AudioLanguage.preference("hin"));assertArrayEquals(new String[]{"en","eng"},AudioLanguage.preference("eng"));assertEquals(0,AudioLanguage.preference("unknown").length);}
    @Test public void labelsAndSubtitleTypes(){assertEquals("Hindi",AudioLanguage.label("hin"));assertEquals("English",AudioLanguage.label("en"));assertEquals("text/vtt",AudioLanguage.subtitleMime("https://example.com/sub.vtt?token=x"));assertEquals("application/x-subrip",AudioLanguage.subtitleMime("https://example.com/a.srt"));}
    @Test public void originComparison(){assertTrue(HeaderPolicy.sameOrigin("https://EXAMPLE.com/a","https://example.com:443/b"));assertFalse(HeaderPolicy.sameOrigin("https://example.com/a","http://example.com/b"));assertFalse(HeaderPolicy.sameOrigin("https://a.example/a","https://b.example/a"));}
    @Test public void authIsNotForwardedToOtherOrigins(){Map<String,String> input=Map.of("Cookie","secret","Authorization","private","User-Agent","TV","Referer","https://origin.example");Map<String,String> result=HeaderPolicy.headers("https://origin.example/a","https://cdn.example/b",input,Map.of());assertFalse(result.containsKey("Cookie"));assertFalse(result.containsKey("Authorization"));assertEquals("TV",result.get("User-Agent"));}
    @Test public void sameOriginAndInvalidHeaders(){Map<String,String> result=HeaderPolicy.headers("https://a.example/a","https://a.example/b",Map.of("Cookie","value","Host","wrong","Unsafe","a\r\nb"),Map.of());assertEquals("value",result.get("Cookie"));assertFalse(result.containsKey("Host"));assertFalse(result.containsKey("Unsafe"));}
}
