package com.jjundev.oneclickeng.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class IncrementalDialogueScriptParserTest {

  @Test
  public void addChunk_extractsMetadataAndTurnsIncrementally() {
    IncrementalDialogueScriptParser parser = new IncrementalDialogueScriptParser();

    IncrementalDialogueScriptParser.ParseUpdate first =
        parser.addChunk("{\"topic\":\"인사\",\"opponent_name\":\"Coach\",");
    assertNotNull(first.getMetadata());
    assertEquals("인사", first.getMetadata().getTopic());
    assertEquals("Coach", first.getMetadata().getOpponentName());
    assertEquals("female", first.getMetadata().getOpponentGender());
    assertEquals(0, first.getCompletedTurnObjects().size());

    IncrementalDialogueScriptParser.ParseUpdate second =
        parser.addChunk(
            "\"opponent_gender\":\"female\",\"script\":[{\"ko\":\"안녕하세요\",\"en\":\"Hello\",\"role\":\"model\"},");
    assertNull(second.getMetadata());
    assertEquals(1, second.getCompletedTurnObjects().size());

    IncrementalDialogueScriptParser.ParseUpdate third =
        parser.addChunk("{\"ko\":\"반가워요\",\"en\":\"Nice to meet you\",\"role\":\"user\"}]}");
    assertNull(third.getMetadata());
    assertEquals(1, third.getCompletedTurnObjects().size());
  }
}
