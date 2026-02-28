public class TestParserWithDelay {
    public static void main(String[] args) {
        String[] chunks = {
            "{\n  \"topic\": \"Testing topic extraction\",\n  \"",
            "opponent_name\": \"John\",\n  \"opponent_gender\": \"male\",\n  \"opponent_role\":",
            " \"Friend\",\n  \"script\": [\n    {\n      \"speaker\": \"User\",\n      \"text",
            "\": \"Hi John, can you help me with this test?\"\n    },\n    {\n      \"speaker\": \"John",
            "\",\n      \"text\": \"Sure! Testing is important for learning new things.\"\n    }\n  ]\n}"
        };
        
        StringBuilder buffer = new StringBuilder();
        for (String chunk : chunks) {
            buffer.append(chunk);
            System.out.println("Buffer length: " + buffer.length() + ", metadata: " + tryExtractMetadata(buffer.toString()));
        }
    }

    private static String tryExtractMetadata(String source) {
        String topic = readJsonStringValue(source, "topic");
        String opponentName = readJsonStringValue(source, "opponent_name");
        if (topic == null || opponentName == null || topic.trim().isEmpty() || opponentName.trim().isEmpty()) {
            return null;
        }

        String opponentGender = readJsonStringValue(source, "opponent_gender");
        if (opponentGender == null || opponentGender.trim().isEmpty()) {
            opponentGender = "female";
        }
        return "Topic: " + topic.trim() + ", Name: " + opponentName.trim() + ", Gender: " + opponentGender.trim();
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