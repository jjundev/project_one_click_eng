package com.jjundev.oneclickeng;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import java.io.File;

public class OneClickEngApplication extends Application {

  @Nullable private static SimpleCache simpleCache;

  @Override
  public void onCreate() {
    super.onCreate();
  }

  @NonNull
  public static synchronized SimpleCache getCache(@NonNull android.content.Context context) {
    if (simpleCache == null) {
      File cacheDir = new File(context.getCacheDir(), "media_cache");
      // 100MB cache size
      LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024);
      DatabaseProvider databaseProvider = new StandaloneDatabaseProvider(context);
      simpleCache = new SimpleCache(cacheDir, evictor, databaseProvider);
    }
    return simpleCache;
  }
}
