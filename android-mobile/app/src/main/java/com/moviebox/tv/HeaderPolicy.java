package com.moviebox.tv;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
final class HeaderPolicy {
    static boolean sameOrigin(String a,String b) {
        try { URI x=URI.create(a),y=URI.create(b);
            return x.getHost()!=null && y.getHost()!=null && x.getHost().equalsIgnoreCase(y.getHost()) &&
                x.getScheme().equalsIgnoreCase(y.getScheme()) && port(x)==port(y);
        } catch (Exception e) { return false; }
    }
    private static int port(URI u){return u.getPort()>=0?u.getPort():"https".equalsIgnoreCase(u.getScheme())?443:80;}
    static Map<String,String> headers(String origin,String target,Map<String,String> supplied,Map<String,String> existing) {
        Map<String,String> result=new LinkedHashMap<>();
        Map<String,String> combined=new LinkedHashMap<>(existing);combined.putAll(supplied);
        boolean same=sameOrigin(origin,target);
        for (Map.Entry<String,String> entry:combined.entrySet()) {
            String key=entry.getKey(),value=entry.getValue(),lower=key.toLowerCase(Locale.ROOT);
            if(key.contains("\r")||key.contains("\n")||value.contains("\r")||value.contains("\n"))continue;
            if(lower.equals("host")||lower.equals("connection")||lower.equals("range")||lower.equals("content-length")||lower.equals("transfer-encoding"))continue;
            if(same||lower.equals("user-agent")||lower.equals("referer"))result.put(key,value);
        }
        return result;
    }
    private HeaderPolicy() {}
}
