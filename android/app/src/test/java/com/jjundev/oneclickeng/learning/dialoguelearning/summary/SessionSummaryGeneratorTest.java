package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.jjundev.oneclickeng.learning.dialoguelearning.model.ConceptualBridge;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.GrammarFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.StyledSentence;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.TextSegment;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.WritingScore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SessionSummaryGeneratorTest {

  @Test
  public void buildSeed_expressionPromptPrefersOriginalSentenceOverLiteralTranslation() {
    SessionSummaryGenerator generator = new SessionSummaryGenerator();
    SentenceFeedback feedback = new SentenceFeedback();
    feedback.setOriginalSentence("그는 어제 학교에 갔다.");
    feedback.setUserSentence("He go to school yesterday.");

    WritingScore writingScore = new WritingScore();
    writingScore.setScore(82);
    feedback.setWritingScore(writingScore);

    ConceptualBridge conceptualBridge = new ConceptualBridge();
    conceptualBridge.setLiteralTranslation("그는 학교에 간다.");
    feedback.setConceptualBridge(conceptualBridge);

    GrammarFeedback grammarFeedback = new GrammarFeedback();
    grammarFeedback.setCorrectedSentence(styledSentence("He went to school yesterday."));
    grammarFeedback.setExplanation("시제에 맞게 고친 문장입니다.");
    feedback.setGrammar(grammarFeedback);

    SessionSummaryGenerator.GenerationSeed seed =
        generator.buildSeed(Arrays.asList(feedback), new ArrayList<>());
    List<SummaryData.ExpressionItem> expressions = seed.getFallbackSummary().getExpressions();

    assertNotNull(expressions);
    assertFalse(expressions.isEmpty());
    assertEquals("그는 어제 학교에 갔다.", expressions.get(0).getKoreanPrompt());
  }

  private StyledSentence styledSentence(String text) {
    TextSegment segment = new TextSegment();
    segment.setType(TextSegment.TYPE_NORMAL);
    segment.setText(text);

    StyledSentence sentence = new StyledSentence();
    sentence.setSegments(Arrays.asList(segment));
    return sentence;
  }
}
