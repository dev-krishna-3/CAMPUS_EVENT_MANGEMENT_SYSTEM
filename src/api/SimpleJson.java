package api;

import java.util.HashMap;
import java.util.Map;

public class SimpleJson {
    // Very basic parser for 1-level deep JSON: {"email": "test@gmail.com", "password": "123"}
    public static Map<String, String> parse(String jsonStr) {
        Map<String, String> map = new HashMap<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) return map;
        
        jsonStr = jsonStr.trim();
        if (jsonStr.startsWith("{")) jsonStr = jsonStr.substring(1);
        if (jsonStr.endsWith("}")) jsonStr = jsonStr.substring(0, jsonStr.length() - 1);
        
        String[] pairs = jsonStr.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }
        return map;
    }

    public static String escape(String s) {
        if (s == null) return "null";
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
