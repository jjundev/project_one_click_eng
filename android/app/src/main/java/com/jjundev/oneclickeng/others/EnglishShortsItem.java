package com.jjundev.oneclickeng.others;

import androidx.annotation.NonNull;

/** Data model for a single English Shorts card. */
public class EnglishShortsItem {
  @NonNull private String title;
  @NonNull private String videoUrl;
  @NonNull private String tag;
  private int likeCount;
  private boolean isActive;

  /** No-arg constructor required for Firestore deserialization. */
  public EnglishShortsItem() {
    this.title = "";
    this.videoUrl = "";
    this.tag = "";
    this.likeCount = 0;
    this.isActive = true;
  }

  @NonNull
  public String getTitle() {
    return title;
  }

  public void setTitle(@NonNull String title) {
    this.title = title;
  }

  @NonNull
  public String getVideoUrl() {
    return videoUrl;
  }

  public void setVideoUrl(@NonNull String videoUrl) {
    this.videoUrl = videoUrl;
  }

  @NonNull
  public String getTag() {
    return tag;
  }

  public void setTag(@NonNull String tag) {
    this.tag = tag;
  }

  public int getLikeCount() {
    return likeCount;
  }

  public void setLikeCount(int likeCount) {
    this.likeCount = likeCount;
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean active) {
    isActive = active;
  }
}
