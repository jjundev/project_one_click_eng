package com.example.test.others;

import androidx.annotation.NonNull;

public class EnglishShortsItem {
  @NonNull private final String creatorHandle;
  @NonNull private final String captionTitle;
  @NonNull private final String captionSubtitle;
  @NonNull private final String hashtagLine;
  @NonNull private final String lessonTag;
  @NonNull private final String learningSentence;
  @NonNull private final String pronunciationHint;
  @NonNull private final String practicePrompt;
  private final int likeCount;
  private final int bookmarkCount;
  private final int shareCount;
  private final int gradientStartColor;
  private final int gradientEndColor;
  private final int accentColor;

  public EnglishShortsItem(
      @NonNull String creatorHandle,
      @NonNull String captionTitle,
      @NonNull String captionSubtitle,
      @NonNull String hashtagLine,
      @NonNull String lessonTag,
      @NonNull String learningSentence,
      @NonNull String pronunciationHint,
      @NonNull String practicePrompt,
      int likeCount,
      int bookmarkCount,
      int shareCount,
      int gradientStartColor,
      int gradientEndColor,
      int accentColor) {
    this.creatorHandle = creatorHandle;
    this.captionTitle = captionTitle;
    this.captionSubtitle = captionSubtitle;
    this.hashtagLine = hashtagLine;
    this.lessonTag = lessonTag;
    this.learningSentence = learningSentence;
    this.pronunciationHint = pronunciationHint;
    this.practicePrompt = practicePrompt;
    this.likeCount = likeCount;
    this.bookmarkCount = bookmarkCount;
    this.shareCount = shareCount;
    this.gradientStartColor = gradientStartColor;
    this.gradientEndColor = gradientEndColor;
    this.accentColor = accentColor;
  }

  @NonNull
  public String getCreatorHandle() {
    return creatorHandle;
  }

  @NonNull
  public String getCaptionTitle() {
    return captionTitle;
  }

  @NonNull
  public String getCaptionSubtitle() {
    return captionSubtitle;
  }

  @NonNull
  public String getHashtagLine() {
    return hashtagLine;
  }

  @NonNull
  public String getLessonTag() {
    return lessonTag;
  }

  @NonNull
  public String getLearningSentence() {
    return learningSentence;
  }

  @NonNull
  public String getPronunciationHint() {
    return pronunciationHint;
  }

  @NonNull
  public String getPracticePrompt() {
    return practicePrompt;
  }

  public int getLikeCount() {
    return likeCount;
  }

  public int getBookmarkCount() {
    return bookmarkCount;
  }

  public int getShareCount() {
    return shareCount;
  }

  public int getGradientStartColor() {
    return gradientStartColor;
  }

  public int getGradientEndColor() {
    return gradientEndColor;
  }

  public int getAccentColor() {
    return accentColor;
  }
}
