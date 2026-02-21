package com.jjundev.oneclickeng.others;

/** Model class for script templates in the selection screen. */
public class ScriptTemplate {
  private String emoji;
  private String title;
  private String subtitle;
  private String initialKo;

  public ScriptTemplate(String emoji, String title, String subtitle, String initialKo) {
    this.emoji = emoji;
    this.title = title;
    this.subtitle = subtitle;
    this.initialKo = initialKo;
  }

  public String getEmoji() {
    return emoji;
  }

  public String getTitle() {
    return title;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public String getInitialKo() {
    return initialKo;
  }
}
