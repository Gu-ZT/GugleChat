package dev.dubhe.gugle.chat.common.util;

public class XssFilter {

    public static String sanitize(String input) {
        if (input == null) return null;
        return input
                .replaceAll("(?i)<script[\\s\\S]*?</script>", "")
                .replaceAll("(?i)<iframe[\\s\\S]*?</iframe>", "")
                .replaceAll("(?i)<object[\\s\\S]*?</object>", "")
                .replaceAll("(?i)<embed[\\s\\S]*?>", "")
                .replaceAll("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']", "")
                .replaceAll("(?i)on\\w+\\s*=\\s*[^\\s>]+", "")
                .replaceAll("(?i)javascript\\s*:", "");
    }
}
