package com.jjundev.oneclickeng.game.refiner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.jjundev.oneclickeng.game.refiner.model.RefinerConstraints;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimit;
import com.jjundev.oneclickeng.game.refiner.model.RefinerWordLimitMode;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RefinerConstraintValidatorTest {

  @Test
  public void bannedWord_detectedWithTokenRange() {
    RefinerConstraints constraints =
        new RefinerConstraints(Arrays.asList("want", "give"), null, null);

    RefinerConstraintValidator.ValidationResult result =
        RefinerConstraintValidator.validate(constraints, "I want more time.");

    assertFalse(result.isBannedWordsSatisfied());
    assertEquals(1, result.getBannedWordRanges().size());
    assertEquals("want", result.getBannedWordRanges().get(0).getStem());
  }

  @Test
  public void bannedWord_substringIsNotFalsePositive() {
    RefinerConstraints constraints = new RefinerConstraints(Collections.singletonList("get"), null, null);

    RefinerConstraintValidator.ValidationResult result =
        RefinerConstraintValidator.validate(constraints, "This target is fine.");

    assertTrue(result.isBannedWordsSatisfied());
    assertTrue(result.getBannedWordRanges().isEmpty());
  }

  @Test
  public void wordLimit_maxAndExact_areEvaluated() {
    RefinerConstraints maxConstraints =
        new RefinerConstraints(
            null, new RefinerWordLimit(RefinerWordLimitMode.MAX, 5), null);
    RefinerConstraintValidator.ValidationResult maxResult =
        RefinerConstraintValidator.validate(maxConstraints, "Could I request more time");
    assertTrue(maxResult.isWordLimitSatisfied());

    RefinerConstraints exactConstraints =
        new RefinerConstraints(
            null, new RefinerWordLimit(RefinerWordLimitMode.EXACT, 5), null);
    RefinerConstraintValidator.ValidationResult exactFail =
        RefinerConstraintValidator.validate(exactConstraints, "Could I request more time now please");
    assertFalse(exactFail.isWordLimitSatisfied());
  }

  @Test
  public void requiredWord_detectsBasicInflection() {
    RefinerConstraints constraints = new RefinerConstraints(null, null, "appreciate");

    RefinerConstraintValidator.ValidationResult result =
        RefinerConstraintValidator.validate(
            constraints, "I appreciates your flexibility on the deadline.");

    assertTrue(result.isRequiredWordSatisfied());
  }
}
