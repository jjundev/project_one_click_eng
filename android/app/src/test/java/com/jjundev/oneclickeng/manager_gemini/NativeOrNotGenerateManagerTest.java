package com.jjundev.oneclickeng.manager_gemini;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotDifficulty;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotQuestion;
import com.jjundev.oneclickeng.game.nativeornot.model.NativeOrNotTag;
import org.junit.Test;

public class NativeOrNotGenerateManagerTest {

  @Test
  public void parseQuestionPayload_returnsQuestion_whenPayloadValid() {
    String payload =
        "{"
            + "\"situation\":\"친구에게 오랜만에 연락이 왔을 때\","
            + "\"options\":[\"I haven't seen you for a long time.\",\"Long time no see!\",\"It's been a while!\"],"
            + "\"correctIndex\":1,"
            + "\"awkwardOptionIndex\":0,"
            + "\"reasonChoices\":[\"문법 오류\",\"원어민은 더 짧은 표현 선호\",\"너무 격식체\",\"단어 선택 오류\"],"
            + "\"reasonAnswerIndex\":1,"
            + "\"explanation\":\"교과서식 문장보다 짧은 관용 표현이 더 자연스럽습니다.\","
            + "\"learningPoint\":\"짧고 리드미컬한 표현을 우선해보세요.\","
            + "\"tag\":\"SPOKEN\","
            + "\"hint\":\"대화에서는 짧은 표현을 떠올려보세요.\","
            + "\"difficulty\":\"NORMAL\""
            + "}";

    NativeOrNotQuestion question =
        NativeOrNotGenerateManager.parseQuestionPayload(payload, NativeOrNotDifficulty.EASY);

    assertNotNull(question);
    assertEquals("친구에게 오랜만에 연락이 왔을 때", question.getSituation());
    assertEquals(3, question.getOptions().size());
    assertEquals(1, question.getCorrectIndex());
    assertEquals(0, question.getAwkwardOptionIndex());
    assertEquals(4, question.getReasonChoices().size());
    assertEquals(1, question.getReasonAnswerIndex());
    assertEquals(NativeOrNotTag.SPOKEN, question.getTag());
    assertEquals(NativeOrNotDifficulty.NORMAL, question.getDifficulty());
  }

  @Test
  public void parseQuestionPayload_returnsNull_whenInvalidSchema() {
    String payload =
        "{"
            + "\"situation\":\"테스트\","
            + "\"options\":[\"A\",\"B\"],"
            + "\"correctIndex\":0,"
            + "\"awkwardOptionIndex\":0"
            + "}";

    NativeOrNotQuestion question =
        NativeOrNotGenerateManager.parseQuestionPayload(payload, NativeOrNotDifficulty.EASY);

    assertNull(question);
  }

  @Test
  public void parseQuestionPayload_usesFallbackDifficulty_whenMissingDifficulty() {
    String payload =
        "{"
            + "\"situation\":\"테스트\","
            + "\"options\":[\"A\",\"B\",\"C\"],"
            + "\"correctIndex\":1,"
            + "\"awkwardOptionIndex\":0,"
            + "\"reasonChoices\":[\"r1\",\"r2\",\"r3\",\"r4\"],"
            + "\"reasonAnswerIndex\":2,"
            + "\"explanation\":\"설명\","
            + "\"learningPoint\":\"포인트\","
            + "\"tag\":\"REGISTER\","
            + "\"hint\":\"힌트\""
            + "}";

    NativeOrNotQuestion question =
        NativeOrNotGenerateManager.parseQuestionPayload(payload, NativeOrNotDifficulty.NORMAL);

    assertNotNull(question);
    assertEquals(NativeOrNotDifficulty.NORMAL, question.getDifficulty());
  }
}
