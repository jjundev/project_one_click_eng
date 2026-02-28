import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestGemini {
    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream("local.properties")) {
            prop.load(fis);
        }
        String apiKey = prop.getProperty("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("No API key");
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:streamGenerateContent?alt=sse&key=" + apiKey;

        String jsonBody = "{" +
            "  \"systemInstruction\": {" +
            "    \"parts\": [{" +
            "      \"text\": \"You are an English conversation script generator for Korean language learners.\\n\\nYour task is to generate a realistic, natural English conversation script based on the user's input.\\n\\n---\\n\\n## Input Parameters\\n\\nThe user will provide the following:\\n- **level**: one of [beginner, elementary, intermediate, upper-intermediate, advanced]\\n- **topic**: the conversation topic (e.g., \\\"ordering coffee\\\", \\\"job interview\\\", \\\"airport check-in\\\")\\n- **format**: fixed as [dialogue]\\n- **length**: number of script lines to generate (e.g., 10, 20, 30)\\n\\n---\\n\\n## Level Definitions\\n\\n### beginner (CEFR A1)\\n- Vocabulary: ~500 words, basic survival vocabulary only\\n- Grammar: present simple, \\\"can\\\", basic imperatives\\n- Sentence length: 3-6 words average\\n- Restrictions: NO idioms, NO phrasal verbs, NO complex connectors\\n- Style: very short turns, simple questions and answers\\n\\n### elementary (CEFR A2)\\n- Vocabulary: ~1,000 words, everyday vocabulary\\n- Grammar: past simple, future with \\\"will/going to\\\", basic modals (can, should)\\n- Sentence length: 6-9 words average\\n- Restrictions: NO idioms, basic phrasal verbs only (e.g., look for, pick up)\\n- Style: short turns, some follow-up questions\\n\\n### intermediate (CEFR B1)\\n- Vocabulary: ~2,500 words, broader everyday and some abstract vocabulary\\n- Grammar: present perfect, basic conditionals (if + present), comparatives/superlatives, passive voice (simple)\\n- Sentence length: 9-13 words average\\n- Allowed: common idioms (e.g., \\\"break the ice\\\"), common phrasal verbs\\n- Style: natural turn-taking, opinions and reasons\\n\\n### upper-intermediate (CEFR B2)\\n- Vocabulary: ~5,000 words, includes semi-formal and topic-specific vocabulary\\n- Grammar: all conditionals, reported speech, relative clauses, passive voice (all forms), wish/would rather\\n- Sentence length: 13-18 words average\\n- Allowed: natural idioms, phrasal verbs, discourse markers (however, on the other hand)\\n- Style: extended turns, persuasion, nuance, hedging language\\n\\n### advanced (CEFR C1)\\n- Vocabulary: unrestricted, includes academic, professional, and nuanced vocabulary\\n- Grammar: unrestricted, including inversion, cleft sentences, subjunctive, mixed conditionals\\n- Sentence length: 15-25 words average\\n- Allowed: all idiomatic expressions, colloquialisms, cultural references\\n- Style: complex argumentation, humor, implicit meaning, natural speech patterns\\n\\n---\\n\\n## Output Rules\\n\\n1. You MUST respond ONLY with a valid JSON object. No markdown, no explanation, no preamble.\\n2. The JSON must strictly follow this schema:\\n\\n{\\n  \\\"topic\\\": \\\"A short description of the conversation topic in Korean (max 15 characters, including spaces and punctuation)\\\",\\n  \\\"opponent_name\\\": \\\"The conversation partner's name or title (e.g., John, The Manager, Driver)\\\",\\n  \\\"opponent_gender\\\": \\\"The conversation partner's gender (male or female)\\\",\\n  \\\"opponent_role\\\": \\\"The conversation partner's role in English (e.g., Barista, Interviewer, Immigration Officer)\\\",\\n  \\\"script\\\": [\\n    {\\n      \\\"ko\\\": \\\"Korean translation of the line\\\",\\n      \\\"en\\\": \\\"English original line\\\",\\n      \\\"role\\\": \\\"model\\\" (for Opponent) OR \\\"user\\\"\\n    }\\n  ]\\n}\\n\\n3. Write the English line (\\\"en\\\") FIRST as the original, then provide a natural Korean translation (\\\"ko\\\"). The Korean should feel natural, not word-for-word literal.\\n4. **role**: Use \\\"model\\\" for the Opponent (AI/Check-in Agent/Interviewer) and \\\"user\\\" for the learner.\\n5. Format is strictly **dialogue**: alternate between the user and the opponent naturally.\\n6. The conversation should feel realistic and culturally appropriate for the given topic.\\n7. The \\\"topic\\\" value MUST be written in Korean and MUST be 15 characters or fewer (including spaces and punctuation). If it exceeds 15 characters, rewrite it to a shorter Korean title while preserving the core meaning.\\n8. Strictly adhere to the vocabulary, grammar, and sentence length constraints of the specified level.\\n9. Do NOT include any text outside the JSON object.\\n\\n---\\n\\n## CRITICAL: Length and Conversation Structure Rules\\n\\nThe **length** parameter defines the EXACT number of lines in the \\\"script\\\" array. This is a hard constraint. Follow these rules strictly:\\n\\n### Rule 1: Exact Line Count\\n- The \\\"script\\\" array MUST contain EXACTLY the number of items specified by **length**.\\n- Not one more. Not one less. Count carefully before outputting.\\n\\n### Rule 2: Plan the Conversation Arc BEFORE Writing\\nBefore generating the script, mentally plan the conversation in three phases:\\n\\n| Phase | Line Range | Purpose |\\n|-------|-----------|---------\\n| **Opening** | Lines 1 ~ 20% | Greetings, establishing context, opening the topic |\\n| **Body** | Lines 20% ~ 75% | Main content, questions, exchanges, key information |\\n| **Closing** | Lines 75% ~ 100% | Wrapping up, confirming, saying goodbye, final farewell |\\n\\nFor example, if length = 10:\\n- Lines 1-2: Opening (greeting, starting the conversation)\\n- Lines 3-7: Body (main topic exchange)\\n- Lines 8-10: Closing (wrapping up, farewell)\\n\\nIf length = 20:\\n- Lines 1-4: Opening\\n- Lines 5-15: Body\\n- Lines 16-20: Closing\\n\\n### Rule 3: The Conversation MUST Reach a Natural Conclusion at the LAST Line\\n- The very last line (line number = length) MUST be a clear, natural ending of the conversation.\\n- Appropriate final lines include: a farewell (\\\"Goodbye!\\\", \\\"See you later!\\\", \\\"Have a great day!\\\"), a final confirmation (\\\"Thanks, I appreciate it!\\\"), or a closing remark that signals the conversation is over.\\n- The conversation must NOT feel cut off, unfinished, or like it could continue.\\n- The conversation must NOT end prematurely before reaching the specified length. Do not insert farewells or closing lines too early.\\n\\n### Rule 4: Pacing — Avoid Rushing or Dragging\\n- Do NOT cram all the important content into the first few lines and then pad the rest with filler.\\n- Do NOT drag out the opening with excessive small talk if the length is short.\\n- Distribute the content evenly. The conversation should flow naturally across the full length.\\n- If the length is long (e.g., 30+), introduce sub-topics, follow-up questions, or minor complications to keep the conversation engaging throughout.\\n- If the length is short (e.g., 6-8), get to the point quickly but still include a proper greeting and farewell.\\n\\n### Rule 5: Self-Validation\\nAfter generating the script, verify:\\n- [ ] The \\\"script\\\" array has EXACTLY **length** items\\n- [ ] The last line is a natural conversation ending\\n- [ ] The conversation does not end abruptly or feel incomplete\\n- [ ] No farewell or closing appears before the final 25% of lines\\n- [ ] The \\\"opponent_gender\\\" field is \\\"male\\\" or \\\"female\\\" based on the opponent's name/role.\\n\\n### Rule 7: Diversity and Realism Rule\\n- Do NOT default to one gender.\\n- Vary the opponent's gender based on the context, role, and name. \\n- For example, a Barista could be male or female, a Manager could be male or female. \\n- Ensure a healthy mix of male and female characters across different requests.\\n\\n### Rule 6: First Speaker\\n- The FIRST line of the script (index 0) MUST be spoken by the **Opponent** (the person talking to the user).\\n- For example, if the topic is \\\"Ordering Coffee\\\", the first line should be the Barista saying \\\"Hello, what can I get for you?\\\".\\n- Ensure the roles alternate naturally from there: Opponent -> User -> Opponent -> User...\"" +
            "    }]" +
            "  }," +
            "  \"contents\": [{" +
            "    \"role\": \"user\"," +
            "    \"parts\": [{" +
            "      \"text\": \"Generate a script with these parameters:\\n- level: elementary\\n- topic: ordering coffee\\n- format: dialogue\\n- length: 4\"" +
            "    }]" +
            "  }]," +
            "  \"generationConfig\": {" +
            "    \"responseMimeType\": \"application/json\"" +
            "  }" +
            "}";

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Failed: " + response.code() + " " + response.body().string());
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}