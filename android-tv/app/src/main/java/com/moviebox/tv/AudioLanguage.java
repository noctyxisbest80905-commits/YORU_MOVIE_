package com.moviebox.tv;
import java.util.Locale;
final class AudioLanguage {
    static String[] preference(String code) {
        return switch(code == null ? "" : code) {
            case "hin" -> new String[]{"hi","hin"}; case "eng" -> new String[]{"en","eng"};
            case "tam" -> new String[]{"ta","tam"}; case "tel" -> new String[]{"te","tel"};
            case "ben" -> new String[]{"bn","ben"}; case "mal" -> new String[]{"ml","mal"};
            case "kan" -> new String[]{"kn","kan"}; case "mar" -> new String[]{"mr","mar"};
            case "pan" -> new String[]{"pa","pan"}; case "urd" -> new String[]{"ur","urd"};
            default -> new String[0];
        };
    }
    static String label(String code) {
        if (code == null || code.trim().isEmpty() || code.equals("und")) return "Unlabelled language";
        return switch(code.toLowerCase(Locale.ROOT)) {
            case "hi", "hin" -> "Hindi"; case "en", "eng" -> "English";
            case "ta", "tam" -> "Tamil"; case "te", "tel" -> "Telugu";
            case "bn", "ben" -> "Bengali"; case "ml", "mal" -> "Malayalam";
            default -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH);
        };
    }
    static String subtitleMime(String url) {
        String path=url.toLowerCase(Locale.ROOT).split("\\?",2)[0];
        if(path.endsWith(".vtt"))return "text/vtt";
        if(path.endsWith(".ssa")||path.endsWith(".ass"))return "text/x-ssa";
        return "application/x-subrip";
    }
    private AudioLanguage() {}
}
