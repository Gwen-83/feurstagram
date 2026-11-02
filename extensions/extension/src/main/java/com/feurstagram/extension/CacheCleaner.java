package com.feurstagram.extension;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Clears Instagram's media caches on a background thread, then kills every
 * process belonging to the app so it relaunches from a clean slate with the
 * newly chosen blocks applied. Auth/session state is deliberately preserved.
 */
public final class CacheCleaner implements Runnable {

    private static final String TAG = "FeurCache";
    private static final int MAX_FAILURES_TO_LOG = 10;

    private static final Pattern REELS_DATABASE_FILE = Pattern.compile(
            "^(?:delivery_media_room_db|user_reel_medias_room_db|flash_media_[A-Za-z0-9_-]+|clips_[A-Za-z0-9_-]+)" +
                    "(?:\\.db)?(?:-(?:wal|shm|journal))?$",
            Pattern.CASE_INSENSITIVE);

    static final class CleanupResult {
        int deleted;
        int failures;
        final List<String> failurePaths = new ArrayList<>();

        void fail(File file, String reason, Throwable error) {
            failures++;
            if (failurePaths.size() < MAX_FAILURES_TO_LOG) {
                String path = file == null ? "<unknown>" : file.getAbsolutePath();
                failurePaths.add(path);
                if (error == null) {
                    Log.e(TAG, reason + ": " + path);
                } else {
                    Log.e(TAG, reason + ": " + path, error);
                }
            }
        }
    }

    private final Context context;

    private CacheCleaner(Context context) {
        this.context = context;
    }

    /** Show a toast, then run the wipe + restart on a worker thread. */
    public static void clearAndRestart(Context context) {
        if (context == null) return;
        try {
            Toast.makeText(context, "Feurstagram: clearing cache...", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
        new Thread(new CacheCleaner(context)).start();
    }

    @Override
    public void run() {
        // Stop sibling Instagram processes before clearing caches so they cannot
        // recreate or hold files while the cleanup is in progress.
        stopAppProcesses(context, true);

        CleanupResult result = new CleanupResult();
        wipeKnownVideoCaches(context, result);
        wipeKnownReelsArtifacts(context, result);

        deleteContents(context.getCacheDir(), result);
        deleteContents(context.getCodeCacheDir(), result);

        File ext = context.getExternalCacheDir();
        if (ext != null) deleteContents(ext, result);

        wipeReelsDatabases(context, result);

        wipeKnownVideoCaches(context, result);
        wipeKnownReelsArtifacts(context, result);

        if (result.failures == 0) {
            Log.i(TAG, "Cache cleanup complete; deleted " + result.deleted + " entries.");
        } else {
            Log.e(TAG, "Cache cleanup incomplete: " + result.failures + " failure(s); "
                    + result.deleted + " entries deleted.");
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Toast.makeText(context.getApplicationContext(),
                            "Cache cleanup incomplete; see FeurCache log", Toast.LENGTH_LONG).show();
                } catch (RuntimeException error) {
                    Log.e(TAG, "Could not show cache-cleanup warning.", error);
                }
            });
        }

        if (startRestartRelay(context)) {
            // The relay kills this process from the outside and relaunches the app.
            // If it somehow never arrives, fall through rather than hang here.
            try {
                Thread.sleep(RELAY_TIMEOUT_MS);
            } catch (Throwable ignored) {
            }
        }

        stopAppProcesses(context, false);
        Process.killProcess(Process.myPid());
    }

    /** How long the relay gets to kill this process before we do it ourselves. */
    private static final long RELAY_TIMEOUT_MS = 5000L;

    /**
     * Hand the restart to {@link RestartActivity}, which the Restart relay patch
     * declares in a process of its own. Without it "clear cache and restart" only
     * ever did the first half: the app vanished and the system surfaced whatever
     * task sat behind it — on a device that also has stock Instagram installed,
     * confusingly the unpatched app.
     *
     * @return whether the relay was started (false falls back to the plain kill)
     */
    private static boolean startRestartRelay(Context context) {
        if (context == null) return false;
        try {
            context.startActivity(RestartActivity.intentFor(context, Process.myPid()));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // Reels video chunks live under cacheDir/filesDir ExoPlayer dirs.
    private static final String[] VIDEO_CACHE_PATHS = {
            "ExoPlayerCacheDir/videocache",
            "ExoPlayerCacheDir/videoprefetchcache",
            "ExoPlayerCacheDir/videocachemetadata",
            "videocache",
            "videoprefetchcache",
            "videocachemetadata",
            "ExoPlayerCacheDir",
    };

    private static final String[] REELS_ARTIFACT_PATHS = {
            "most_recent_reels_cache",
            "ig_pando_response_cache",
            "direct_background_prefetch_cache",
            "pending_reel_tray_seen_states",
            "pending_reel_seen_states",
            "pending_clips_seen_states",
            "pending_reel_quiz_responses",
            "pending_reel_slider_votes",
            "pending_reel_countdown_follow_requests",
            "ExoPlayerCacheDir/videoprefetchcache",
            "ExoPlayerCacheDir/videocachemetadata",
            "files/ExoPlayerCacheDir",
    };

    private static void wipeKnownVideoCaches(Context context, CleanupResult result) {
        wipeUnder(context.getCacheDir(), VIDEO_CACHE_PATHS, result);
        wipeUnder(context.getFilesDir(), VIDEO_CACHE_PATHS, result);
    }

    private static void wipeKnownReelsArtifacts(Context context, CleanupResult result) {
        wipeUnder(context.getCacheDir(), REELS_ARTIFACT_PATHS, result);
        wipeUnder(context.getFilesDir(), REELS_ARTIFACT_PATHS, result);
        File parent = context.getFilesDir() == null ? null : context.getFilesDir().getParentFile();
        wipeUnder(parent, REELS_ARTIFACT_PATHS, result);
    }

    private static void wipeReelsDatabases(Context context, CleanupResult result) {
        try {
            File probe = context.getDatabasePath("delivery_media_room_db");
            File databasesDir = probe == null ? null : probe.getParentFile();
            if (databasesDir == null) return;
            File[] children = databasesDir.listFiles();
            if (children == null) {
                if (databasesDir.exists()) result.fail(databasesDir, "Could not list database directory", null);
                return;
            }
            for (File child : children) {
                if (child.isFile() && isReelsDatabaseFileName(child.getName())) {
                    deleteRecursive(child, databasesDir, result);
                }
            }
        } catch (SecurityException error) {
            result.fail(null, "Could not inspect reel databases", error);
        }
    }

    static boolean isReelsDatabaseFileName(String name) {
        return name != null && REELS_DATABASE_FILE.matcher(name).matches();
    }

    private static void wipeUnder(File root, String[] relativePaths, CleanupResult result) {
        if (root == null) return;
        for (String rel : relativePaths) {
            deleteRecursive(new File(root, rel), root, result);
        }
    }

    /** Delete the directory's contents but keep the directory itself. */
    static void deleteContents(File dir, CleanupResult result) {
        if (dir == null) return;
        try {
            File[] children = dir.listFiles();
            if (children == null) {
                if (dir.exists()) result.fail(dir, "Could not list cache directory", null);
                return;
            }
            for (File child : children) {
                deleteRecursive(child, dir, result);
            }
        } catch (SecurityException error) {
            result.fail(dir, "Could not inspect cache directory", error);
        }
    }

    static void deleteRecursive(File file, File allowedRoot, CleanupResult result) {
        try {
            if (!file.exists()) return;
            String rootPath = allowedRoot.getCanonicalPath();
            String canonicalPath = file.getCanonicalPath();
            String absolutePath = file.getAbsolutePath();
            boolean symbolicLink = !canonicalPath.equals(absolutePath);
            if (!canonicalPath.equals(rootPath)
                    && !canonicalPath.startsWith(rootPath + File.separator)) {
                result.fail(file, "Refusing to delete outside approved cache directory", null);
                return;
            }

            if (file.isDirectory() && !symbolicLink) {
                File[] children = file.listFiles();
                if (children == null) {
                    result.fail(file, "Could not list approved cache directory", null);
                    return;
                }
                for (File child : children) {
                    deleteRecursive(child, allowedRoot, result);
                }
            }

            if (!file.delete() && file.exists()) {
                result.fail(file, "Could not delete cache entry", null);
            } else {
                result.deleted++;
            }
        } catch (IOException | SecurityException error) {
            result.fail(file, "Could not safely remove cache entry", error);
        }
    }

    /**
     * Best-effort force-close: kill every process belonging to this app.
     *
     * @param keepSelf spare this process (and, with it, the relay it is about to
     *                 start) so the restart has something left to run in
     */
    private static void stopAppProcesses(Context context, boolean keepSelf) {
        if (context == null) return;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            String pkg = context.getPackageName();
            if (pkg == null) return;

            try {
                am.killBackgroundProcesses(pkg);
            } catch (Throwable ignored) {
            }

            int self = Process.myPid();
            String prefix = pkg + ":";
            List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
            if (running == null) return;
            for (ActivityManager.RunningAppProcessInfo info : running) {
                if (info == null || info.processName == null) continue;
                if (info.pid <= 0) continue;
                if (keepSelf && info.pid == self) continue;
                if (info.processName.equals(pkg) || info.processName.startsWith(prefix)) {
                    Process.killProcess(info.pid);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
