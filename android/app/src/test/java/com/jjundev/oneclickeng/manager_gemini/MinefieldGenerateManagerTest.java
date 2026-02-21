package com.jjundev.oneclickeng.manager_gemini;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.jjundev.oneclickeng.game.minefield.model.MinefieldDifficulty;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldEvaluation;
import com.jjundev.oneclickeng.game.minefield.model.MinefieldQuestion;
import org.junit.Test;

public class MinefieldGenerateManagerTest {

  @Test
  public void parseQuestionPayload_returnsQuestion_whenPayloadValid() {
    String payload =
        "{"
            + "\"situation\":\"회사 동료가 지각 이유를 묻습니다\","
            + "\"question\":\"Why were you late this morning?\","
            + "\"words\":[\"traffic\",\"stuck\",\"unexpected\",\"call\",\"sorry\",\"longer\",\"than\"],"
            + "\"requiredWordIndices\":[0],"
            + "\"difficulty\":\"NORMAL\""
            + "}";

    MinefieldQuestion question =
        MinefieldGenerateManager.parseQuestionPayload(payload, MinefieldDifficulty.EASY);

    assertNotNull(question);
    assertEquals("회사 동료가 지각 이유를 묻습니다", question.getSituation());
    assertEquals("Why were you late this morning?", question.getQuestion());
    assertEquals(7, question.getWords().size());
    assertEquals(1, question.getRequiredWordIndices().size());
    assertEquals(MinefieldDifficulty.NORMAL, question.getDifficulty());
  }

  @Test
  public void parseQuestionPayload_returnsNull_whenRequiredFieldsMissing() {
    String payload =
        "{"
            + "\"situation\":\"테스트\","
            + "\"words\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"],"
            + "\"difficulty\":\"EASY\""
            + "}";

    MinefieldQuestion question =
        MinefieldGenerateManager.parseQuestionPayload(payload, MinefieldDifficulty.EASY);

    assertNull(question);
  }

  @Test
  public void parseQuestionPayload_usesFallbackDifficulty_whenDifficultyMissing() {
    String payload =
        "{"
            + "\"situation\":\"테스트 상황\","
            + "\"question\":\"Tell me about your weekend\","
            + "\"words\":[\"great\",\"relaxed\",\"stayed\",\"home\",\"watched\",\"movies\"],"
            + "\"requiredWordIndices\":[1,2]"
            + "}";

    MinefieldQuestion question =
        MinefieldGenerateManager.parseQuestionPayload(payload, MinefieldDifficulty.HARD);

    assertNotNull(question);
    assertEquals(MinefieldDifficulty.HARD, question.getDifficulty());
  }

  @Test
  public void parseQuestionPayload_stripsFencedJson() {
    String payload =
        "```json\n"
            + "{"
            + "\"situation\":\"테스트 상황\","
            + "\"question\":\"Describe your ideal job\","
            + "\"words\":[\"passionate\",\"matter\",\"regardless\",\"salary\",\"contribute\",\"challenge\"],"
            + "\"requiredWordIndices\":[0,2],"
            + "\"difficulty\":\"EXPERT\""
            + "}\n"
            + "```";

    MinefieldQuestion question =
        MinefieldGenerateManager.parseQuestionPayload(payload, MinefieldDifficulty.EASY);

    assertNotNull(question);
    assertEquals(MinefieldDifficulty.EXPERT, question.getDifficulty());
    assertEquals(2, question.getRequiredWordIndices().size());
  }

  @Test
  public void parseEvaluationPayload_returnsEvaluation_whenPayloadValid() {
    String payload =
        "{"
            + "\"grammarScore\":80,"
            + "\"naturalnessScore\":75,"
            + "\"wordUsageScore\":100,"
            + "\"usedWordCount\":7,"
            + "\"totalWordCount\":7,"
            + "\"usedWords\":[\"traffic\",\"stuck\"],"
            + "\"unusedWords\":[],"
            + "\"missingRequiredWords\":[],"
            + "\"advancedTransformUsed\":true,"
            + "\"strengthsComment\":\"콜로케이션 사용이 좋습니다.\","
            + "\"improvementComment\":\"마지막 문장을 분리하면 더 자연스럽습니다.\","
            + "\"improvedSentence\":\"I got stuck in traffic longer than expected. Sorry about that.\","
            + "\"exampleBasic\":\"Sorry, I got stuck in traffic longer than usual.\","
            + "\"exampleIntermediate\":\"An unexpected call made me leave late, and I got stuck in traffic.\","
            + "\"exampleAdvanced\":\"I got an unexpected call and left late, so the traffic delay was longer than anticipated.\""
            + "}";

    MinefieldEvaluation evaluation = MinefieldGenerateManager.parseEvaluationPayload(payload);

    assertNotNull(evaluation);
    assertEquals(80, evaluation.getGrammarScore());
    assertEquals(75, evaluation.getNaturalnessScore());
    assertEquals(100, evaluation.getWordUsageScore());
    assertTrue(evaluation.isAdvancedTransformUsed());
    assertTrue(evaluation.isValidForV1());
  }

  @Test
  public void parseEvaluationPayload_returnsNull_whenInvalid() {
    String payload =
        "{"
            + "\"grammarScore\":80,"
            + "\"naturalnessScore\":75,"
            + "\"wordUsageScore\":100"
            + "}";

    MinefieldEvaluation evaluation = MinefieldGenerateManager.parseEvaluationPayload(payload);

    assertNull(evaluation);
  }

  @Test
  public void parseEvaluationPayload_stripsFencedJson() {
    String payload =
        "```\n"
            + "{"
            + "\"grammarScore\":60,"
            + "\"naturalnessScore\":65,"
            + "\"wordUsageScore\":50,"
            + "\"usedWordCount\":3,"
            + "\"totalWordCount\":6,"
            + "\"usedWords\":[\"a\"],"
            + "\"unusedWords\":[\"b\"],"
            + "\"missingRequiredWords\":[\"matter\"],"
            + "\"advancedTransformUsed\":false,"
            + "\"strengthsComment\":\"좋은 시도입니다.\","
            + "\"improvementComment\":\"필수 단어를 포함해 보세요.\","
            + "\"improvedSentence\":\"I want a job that matters.\","
            + "\"exampleBasic\":\"I want meaningful work.\","
            + "\"exampleIntermediate\":\"I value work that contributes to society.\","
            + "\"exampleAdvanced\":\"I seek a role where impact outweighs salary alone.\""
            + "}\n"
            + "```";

    MinefieldEvaluation evaluation = MinefieldGenerateManager.parseEvaluationPayload(payload);

    assertNotNull(evaluation);
    assertFalse(evaluation.getMissingRequiredWords().isEmpty());
  }
}
