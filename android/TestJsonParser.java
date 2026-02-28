public class TestJsonParser {
    public static void main(String[] args) {
        String testJson = "`json\n{\"topic\": \"주문하기\", \"opponent_name\": \"John\", \"opponent_gender\": \"male\", \"script\": []}\n`";
        System.out.println("topic: " + readJsonStringValue(testJson, "topic"));
        
        testJson = "data: {\"topic\": \"테스트 토픽 안녕하세요\", \"opponent_name\": \"John\", \"opponent_gender\": \"male\", \"script\": []}";
        System.out.println("topic: " + readJsonStringValue(testJson, "topic"));
    }

    private static String readJsonStringValue(String source, String key) {
        String quotedKey = "\"" + key + "\"";
        int keyIndex = source.indexOf(quotedKey);
        if (keyIndex < 0) {
            return null;
        }

        int colonIndex = source.indexOf(':', keyIndex + quotedKey.length());
        if (colonIndex < 0) {
            return null;
        }

        int valueStart = -1;
        for (int i = colonIndex + 1; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch != '"') {
                return null;
            }
            valueStart = i + 1;
            break;
        }
        if (valueStart < 0 || valueStart > source.length()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = valueStart; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (escaping) {
                builder.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                return builder.toString();
            }
            builder.append(ch);
        }
        return null;
    }
}