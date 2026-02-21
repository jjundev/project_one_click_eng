package com.jjundev.oneclickeng.others;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import com.jjundev.oneclickeng.R;
import java.util.ArrayList;
import java.util.List;

public class EnglishShortsPagerAdapter
    extends RecyclerView.Adapter<EnglishShortsPagerAdapter.ShortViewHolder> {

  @NonNull private final List<EnglishShortsItem> items;

  public EnglishShortsPagerAdapter(@NonNull List<EnglishShortsItem> items) {
    this.items = new ArrayList<>(items);
  }

  @NonNull
  @Override
  public ShortViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_english_short_page, parent, false);
    return new ShortViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ShortViewHolder holder, int position) {
    holder.bind(items.get(position));
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static class ShortViewHolder extends RecyclerView.ViewHolder {
    @NonNull private final View pageRoot;
    @NonNull private final View accentWash;
    @NonNull private final TextView tvLessonTag;
    @NonNull private final TextView tvLearningSentence;
    @NonNull private final TextView tvPronunciationHint;
    @NonNull private final TextView tvPracticePrompt;

    @NonNull
    private final GradientDrawable pageBackground =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {Color.BLACK, Color.DKGRAY});

    ShortViewHolder(@NonNull View itemView) {
      super(itemView);
      pageRoot = itemView.findViewById(R.id.shorts_page_root);
      accentWash = itemView.findViewById(R.id.v_accent_wash);
      tvLessonTag = itemView.findViewById(R.id.tv_short_lesson_badge);
      tvLearningSentence = itemView.findViewById(R.id.tv_short_sentence);
      tvPronunciationHint = itemView.findViewById(R.id.tv_short_hint);
      tvPracticePrompt = itemView.findViewById(R.id.tv_short_prompt);
      pageBackground.setCornerRadius(0f);
      pageRoot.setBackground(pageBackground);
    }

    void bind(@NonNull EnglishShortsItem item) {
      pageBackground.setColors(
          new int[] {item.getGradientStartColor(), item.getGradientEndColor()});
      accentWash.setBackgroundColor(ColorUtils.setAlphaComponent(item.getAccentColor(), 88));
      tvLessonTag.setBackground(
          createBadgeBackground(itemView.getContext(), item.getAccentColor()));
      tvLessonTag.setText(item.getLessonTag());
      tvLearningSentence.setText(item.getLearningSentence());
      tvPronunciationHint.setText(item.getPronunciationHint());
      tvPracticePrompt.setText(item.getPracticePrompt());
    }

    @NonNull
    private GradientDrawable createBadgeBackground(@NonNull Context context, int accentColor) {
      GradientDrawable drawable = new GradientDrawable();
      drawable.setShape(GradientDrawable.RECTANGLE);
      drawable.setCornerRadius(dp(context, 100));
      drawable.setColor(ColorUtils.setAlphaComponent(accentColor, 110));
      drawable.setStroke(dp(context, 1), ColorUtils.setAlphaComponent(Color.WHITE, 120));
      return drawable;
    }

    private static int dp(@NonNull Context context, int dp) {
      float density = context.getResources().getDisplayMetrics().density;
      return Math.round(dp * density);
    }
  }
}
