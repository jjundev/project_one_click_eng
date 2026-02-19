package com.example.test.others;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.test.R;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class ScriptSelectAdapter extends RecyclerView.Adapter<ScriptSelectAdapter.ViewHolder> {

  private final List<ScriptTemplate> templates;
  private final OnTemplateClickListener listener;

  public interface OnTemplateClickListener {
    void onTemplateClick(ScriptTemplate template);
  }

  public ScriptSelectAdapter(List<ScriptTemplate> templates, OnTemplateClickListener listener) {
    this.templates = templates;
    this.listener = listener;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_script_card, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    ScriptTemplate template = templates.get(position);
    holder.tvEmoji.setText(template.getEmoji());
    holder.tvTitle.setText(template.getTitle());
    holder.tvSubtitle.setText(template.getSubtitle());
    holder.card.setOnClickListener(v -> listener.onTemplateClick(template));
  }

  @Override
  public int getItemCount() {
    return templates.size();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    MaterialCardView card;
    TextView tvEmoji, tvTitle, tvSubtitle;

    public ViewHolder(@NonNull View itemView) {
      super(itemView);
      card = itemView.findViewById(R.id.card_script);
      tvEmoji = itemView.findViewById(R.id.tv_card_emoji);
      tvTitle = itemView.findViewById(R.id.tv_card_title);
      tvSubtitle = itemView.findViewById(R.id.tv_card_subtitle);
    }
  }
}
