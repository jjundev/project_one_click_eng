package com.jjundev.oneclickeng.manager_gemini;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.jjundev.oneclickeng.game.refiner.model.RefinerDifficulty;
import com.jjundev.oneclickeng.game.refiner.model.RefinerEvaluation;
import com.jjundev.oneclickeng.game.refiner.model.RefinerLevel;
import com.jjundev.oneclickeng.game.refiner.model.RefinerQuestion;
import org.junit.Test;

public class RefinerGenerateManagerTest {

  @Test
  public void parseQuestionPayload_returnsQuestion_whenPayloadValid() {
    String payload =
        "{"
            + "\"sourceSentence\":\"I want you to give me more time for the project.\","
            + "\"styleContext\":\"비즈니스 이메일\","
            + "\"constraints\":{"
            + "\"bannedWords\":[\"want\",\"give\"],"
            + "\"wordLimit\":{\"mode\":\"MAX\",\"value\":10},"
            + "\"requiredWord\":\"\""
            + "},"
            + "\"hint\":\"request나 extension을 떠올려 보세요.\","
            + "\"difficulty\":\"NORMAL\""
            + "}";

    RefinerQuestion question =
        RefinerGenerateManager.parseQuestionPayload(payload, RefinerDifficulty.EASY);

    assertNotNull(question);
    assertEquals("비즈니스 이메일", question.getStyleContext());
    assertEquals(2, question.getConstraints().getBannedWords().size());
    assertNotNull(question.getConstraints().getWordLimit());
    assertEquals(10, question.getConstraints().getWordLimit().getValue());
    assertEquals(RefinerDifficulty.NORMAL, question.getDifficulty());
  }

  @Test
  public void parseQuestionPayload_returnsNull_whenConstraintCountOutOfRange() {
    String payload =
        "{"
            + "\"sourceSentence\":\"I think this plan might have some problems.\","
            + "\"styleContext\":\"비즈니스\","
            + "\"constraints\":{"
            + "\"bannedWords\":[\"think\"],"
            + "\"wordLimit\":{\"mode\":\"MAX\",\"value\":10},"
            + "\"requiredWord\":\"appreciate\""
            + "},"
            + "\"hint\":\"완곡한 표현을 써보세요.\","
            + "\"difficulty\":\"HARD\""
            + "}";

    RefinerQuestion question =
        RefinerGenerateManager.parseQuestionPayload(payload, RefinerDifficulty.EASY);

    assertNull(question);
  }

  @Test
  public void parseQuestionPayload_returnsNull_whenConstraintTypeInvalid() {
    String payload =
        "{"
            + "\"sourceSentence\":\"This movie is very very good.\","
            + "\"styleContext\":\"친구 문자\","
            + "\"constraints\":{"
            + "\"wordLimit\":{\"mode\":\"MAX\",\"value\":\"invalid\"}"
            + "},"
            + "\"hint\":\"형용사를 바꿔보세요.\","
            + "\"difficulty\":\"EASY\""
            + "}";

    RefinerQuestion question =
        RefinerGenerateManager.parseQuestionPayload(payload, RefinerDifficulty.EASY);

    assertNull(question);
  }

  @Test
  public void parseQuestionPayload_stripsFencedJson() {
    String payload =
        "```json\n"
            + "{"
            + "\"sourceSentence\":\"This movie is very very good.\","
            + "\"styleContext\":\"친구 문자\","
            + "\"constraints\":{"
            + "\"bannedWords\":[\"very\"],"
            + "\"requiredWord\":\"\""
            + "},"
            + "\"hint\":\"형용사를 바꿔보세요.\","
            + "\"difficulty\":\"EASY\""
            + "}\n"
            + "```";

    RefinerQuestion question =
        RefinerGenerateManager.parseQuestionPayload(payload, RefinerDifficulty.NORMAL);

    assertNotNull(question);
    assertEquals(RefinerDifficulty.EASY, question.getDifficulty());
  }

  @Test
  public void parseEvaluationPayload_returnsEvaluation_whenPayloadValid() {
    String payload =
        "{"
            + "\"level\":\"B2\","
            + "\"lexicalScore\":82,"
            + "\"syntaxScore\":78,"
            + "\"naturalnessScore\":85,"
            + "\"complianceScore\":94,"
            + "\"creativeRequiredWordUse\":true,"
            + "\"insight\":\"request 대신 appreciate 패턴을 익혀보세요.\","
            + "\"levelExamples\":["
            + "{\"level\":\"A2\",\"sentence\":\"Please give me more time.\",\"comment\":\"의미 전달은 가능하지만 단조롭습니다.\"},"
            + "{\"level\":\"B1\",\"sentence\":\"Can I get more time for this?\",\"comment\":\"정중하지만 평범합니다.\"},"
            + "{\"level\":\"B2\",\"sentence\":\"Could I request a deadline extension?\",\"comment\":\"비즈니스 어휘가 적절합니다.\"},"
            + "{\"level\":\"C1\",\"sentence\":\"I'd appreciate some flexibility on the deadline.\",\"comment\":\"완곡한 고급 표현입니다.\"},"
            + "{\"level\":\"C2\",\"sentence\":\"Would it be possible to revisit the timeline on this?\",\"comment\":\"협상 뉘앙스가 살아있습니다.\"}"
            + "]"
            + "}";

    RefinerEvaluation evaluation = RefinerGenerateManager.parseEvaluationPayload(payload);

    assertNotNull(evaluation);
    assertEquals(RefinerLevel.B2, evaluation.getLevel());
    assertTrue(evaluation.isCreativeRequiredWordUse());
    assertEquals(5, evaluation.getLevelExamples().size());
    assertTrue(evaluation.isValidForV1());
  }

  @Test
  public void parseEvaluationPayload_returnsNull_whenLevelExamplesMissing() {
    String payload =
        "{"
            + "\"level\":\"B2\","
            + "\"lexicalScore\":82,"
            + "\"syntaxScore\":78,"
            + "\"naturalnessScore\":85,"
            + "\"complianceScore\":94,"
            + "\"creativeRequiredWordUse\":false,"
            + "\"insight\":\"insight only\""
            + "}";

    RefinerEvaluation evaluation = RefinerGenerateManager.parseEvaluationPayload(payload);

    assertNull(evaluation);
  }

  @Test
  public void parseEvaluationPayload_stripsFencedJson() {
    String payload =
        "```\n"
            + "{"
            + "\"level\":\"A2\","
            + "\"lexicalScore\":60,"
            + "\"syntaxScore\":58,"
            + "\"naturalnessScore\":62,"
            + "\"complianceScore\":80,"
            + "\"creativeRequiredWordUse\":false,"
            + "\"insight\":\"문장을 더 간결하게 정리해보세요.\","
            + "\"levelExamples\":["
            + "{\"level\":\"A2\",\"sentence\":\"Please give me more time.\",\"comment\":\"직접적입니다.\"},"
            + "{\"level\":\"B1\",\"sentence\":\"Can I have more time for the project?\",\"comment\":\"정중합니다.\"},"
            + "{\"level\":\"B2\",\"sentence\":\"Could I request a short extension?\",\"comment\":\"격식이 올라갑니다.\"},"
            + "{\"level\":\"C1\",\"sentence\":\"I'd appreciate some flexibility on the deadline.\",\"comment\":\"완곡합니다.\"},"
            + "{\"level\":\"C2\",\"sentence\":\"Would it be possible to revisit the timeline?\",\"comment\":\"협상적 뉘앙스가 있습니다.\"}"
            + "]"
            + "}\n"
            + "```";

    RefinerEvaluation evaluation = RefinerGenerateManager.parseEvaluationPayload(payload);

    assertNotNull(evaluation);
    assertFalse(evaluation.getLevelExamples().isEmpty());
  }
}
