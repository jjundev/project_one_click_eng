package com.jjundev.oneclickeng.others;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import com.jjundev.oneclickeng.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the English Shorts ViewPager2. Each page displays a full-screen video with a tag
 * badge.
 */
public class EnglishShortsPagerAdapter
    extends RecyclerView.Adapter<EnglishShortsPagerAdapter.ShortViewHolder> {

  @NonNull private final List<EnglishShortsItem> items;

  public EnglishShortsPagerAdapter(@NonNull List<EnglishShortsItem> items) {
    this.items = new ArrayList<>(items);
  }

  /** Replaces the current item list and refreshes the adapter. */
  public void submitList(@NonNull List<EnglishShortsItem> newItems) {
    items.clear();
    items.addAll(newItems);
    notifyDataSetChanged();
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
  public void onViewRecycled(@NonNull ShortViewHolder holder) {
    super.onViewRecycled(holder);
    holder.releasePlayer();
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  /** Pauses all currently playing videos in the attached RecyclerView. */
  public void pauseAll(@Nullable RecyclerView recyclerView) {
    if (recyclerView == null) return;
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
      if (holder instanceof ShortViewHolder) {
        ((ShortViewHolder) holder).pause();
      }
    }
  }

  /** Plays the video at the given position and pauses all others. */
  public void playAtPosition(@Nullable RecyclerView recyclerView, int position) {
    if (recyclerView == null) return;
    pauseAll(recyclerView);
    RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
    if (holder instanceof ShortViewHolder) {
      ((ShortViewHolder) holder).play();
    }
  }

  /** ViewHolder for a single Shorts page. */
  public static class ShortViewHolder extends RecyclerView.ViewHolder {
    @NonNull private final PlayerView playerView;
    @NonNull private final ImageView ivThumbnail;
    @NonNull private final TextView tvTag;

    @Nullable private ExoPlayer player;
    @Nullable private EnglishShortsItem currentItem;

    ShortViewHolder(@NonNull View itemView) {
      super(itemView);
      playerView = itemView.findViewById(R.id.player_view);
      ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
      tvTag = itemView.findViewById(R.id.tv_short_tag);

      playerView.setOnClickListener(v -> togglePlayback());
    }

    private void togglePlayback() {
      if (player != null) {
        if (player.isPlaying()) {
          player.pause();
        } else {
          player.play();
        }
      }
    }

    void bind(@NonNull EnglishShortsItem item) {
      this.currentItem = item;
      tvTag.setText(item.getTag());
      ivThumbnail.setVisibility(View.VISIBLE);
      releasePlayer();
    }

    /** Starts playback if a player is ready, or initializes it. */
    public void play() {
      if (currentItem == null) return;
      String videoUrl = currentItem.getVideoUrl();
      if (videoUrl == null || videoUrl.isEmpty()) return;

      if (player == null) {
        androidx.media3.datasource.DataSource.Factory cacheDataSourceFactory =
            new androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(
                    com.jjundev.oneclickeng.OneClickEngApplication.getCache(
                        itemView.getContext().getApplicationContext()))
                .setUpstreamDataSourceFactory(
                    new androidx.media3.datasource.DefaultHttpDataSource.Factory());

        player =
            new ExoPlayer.Builder(itemView.getContext())
                .setMediaSourceFactory(
                    new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                        cacheDataSourceFactory))
                .build();
        playerView.setPlayer(player);

        // Remove surrounding quotes from JSON parsing if they exist
        if (videoUrl.startsWith("\"") && videoUrl.endsWith("\"") && videoUrl.length() > 2) {
          videoUrl = videoUrl.substring(1, videoUrl.length() - 1);
        }

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
        player.setMediaItem(mediaItem);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setVolume(0f);
        player.addListener(
            new Player.Listener() {
              @Override
              public void onRenderedFirstFrame() {
                ivThumbnail.setVisibility(View.GONE);
              }
            });
        player.prepare();
      }
      player.play();
    }

    /** Pauses playback and releases the player to free decoders. */
    public void pause() {
      releasePlayer();
    }

    /** Releases the ExoPlayer instance to free resources. */
    public void releasePlayer() {
      if (player != null) {
        player.release();
        player = null;
      }
      playerView.setPlayer(null);
      ivThumbnail.setVisibility(View.VISIBLE);
    }
  }
}
