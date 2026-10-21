package df.root;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Private, bounded history of MANUAL RMP root sessions.
 * Data is stored only under this application's credential-protected filesDir.
 * No network, external storage or logging of unrelated background activity.
 */
public final class RmpSessionHistory {
    private static final String DIRECTORY = "rmp_session_history";
    private static final int MAX_SESSIONS = 20;
    private static final long MAX_SESSION_BYTES = 256L * 1024L;
    private static final int MAX_RECORD_CHARS = 16000;
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final class Session {
        public final String id;
        public final String title;
        public final String subtitle;
        public final long size;

        Session(String id, String title, String subtitle, long size) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.size = size;
        }
    }

    private RmpSessionHistory() {}

    private static File dir(Context ctx) { return new File(ctx.getFilesDir(), DIRECTORY); }

    private static boolean valid(String id) {
        return id != null && id.matches("session-[0-9]+-[0-9]+\\.log");
    }

    private static File locate(Context ctx, String id) {
        if (!valid(id)) throw new IllegalArgumentException("Invalid session id");
        return new File(dir(ctx), id);
    }

    public static String begin(Context context, String manager) {
        Context app = context.getApplicationContext();
        String id = "session-" + System.currentTimeMillis() + "-"
            + COUNTER.incrementAndGet() + ".log";
        IO.execute(() -> {
            File folder = dir(app);
            if (!folder.isDirectory() && !folder.mkdirs()) {
                Log.w("RmpHistory", "Cannot create private log history directory");
                return;
            }
            appendInternal(new File(folder, id),
                "RMP / SESSION " + new Date() + "\n" +
                "Manager: " + manager + "\n" +
                "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Firmware: " + Build.DISPLAY + "\n" +
                "----------------------------------------\n");
            prune(folder);
        });
        return id;
    }

    public static void append(Context context, String sessionId, String log) {
        if (!valid(sessionId) || log == null || log.isEmpty()) return;
        Context app = context.getApplicationContext();
        // Store log text precisely as reported, with a finite per-event cap.
        String payload = log.length() > MAX_RECORD_CHARS
            ? log.substring(0, MAX_RECORD_CHARS) + "\n[Entry truncated]\n"
            : log;
        IO.execute(() -> appendInternal(locate(app, sessionId), payload));
    }

    public static void finish(Context context, String sessionId, String summary) {
        append(context, sessionId, "\n----------------------------------------\n"
            + "RESULT: " + summary + "\n" + "Finished: " + new Date() + "\n");
    }

    private static void appendInternal(File file, String text) {
        try {
            long available = MAX_SESSION_BYTES - file.length();
            if (available <= 0) return;
            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
            if (encoded.length > available) {
                String limit = "\n[Log truncated at 256 KiB]\n";
                byte[] suffix = limit.getBytes(StandardCharsets.UTF_8);
                int count = Math.max(0, (int)Math.min(encoded.length, available - suffix.length));
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(encoded, 0, count);
                    if (available > suffix.length) out.write(suffix);
                }
            } else {
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(encoded);
                }
            }
        } catch (Exception ex) {
            Log.w("RmpHistory", "Unable to write private session history", ex);
        }
    }

    private static File[] sortedFiles(File directory) {
        File[] files = directory.listFiles((d, name) -> valid(name));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> {
            long aa = parseTime(a.getName());
            long bb = parseTime(b.getName());
            return Long.compare(bb, aa);
        });
        return files;
    }

    private static long parseTime(String id) {
        try { return Long.parseLong(id.substring(8, id.indexOf('-', 8))); }
        catch (Exception ex) { return 0; }
    }

    private static void prune(File directory) {
        File[] files = sortedFiles(directory);
        for (int i = MAX_SESSIONS; i < files.length; i++)
            if (!files[i].delete()) Log.w("RmpHistory", "Could not prune old log");
    }

    public static void sessions(Context context, Consumer<List<Session>> callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<Session> sessions = new ArrayList<>();
            File[] files = sortedFiles(dir(app));
            for (File f : files) {
                long time = parseTime(f.getName());
                String title = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                    DateFormat.SHORT, Locale.getDefault()).format(new Date(time));
                String subtitle = f.length() / 1024 + " KiB  •  Tap to view log";
                sessions.add(new Session(f.getName(), title, subtitle, f.length()));
            }
            MAIN.post(() -> callback.accept(sessions));
        });
    }

    public static void read(Context context, String id, Consumer<String> callback) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            String value;
            try {
                File file = locate(app, id);
                if (!file.isFile()) value = "Session not found.";
                else {
                    byte[] content = Files.readAllBytes(file.toPath());
                    value = new String(content, StandardCharsets.UTF_8);
                }
            } catch (Exception ex) {
                value = "Unable to read session history: " + ex.getMessage();
            }
            String result = value;
            MAIN.post(() -> callback.accept(result));
        });
    }

    public static void remove(Context context, String id, Runnable done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try { if (!locate(app, id).delete()) Log.i("RmpHistory", "Session already absent"); }
            catch (Exception ex) { Log.w("RmpHistory", "Unable to remove session", ex); }
            MAIN.post(done);
        });
    }

    public static void clear(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            for (File file : sortedFiles(dir(app))) {
                if (!file.delete()) Log.w("RmpHistory", "Unable to delete session " + file.getName());
            }
            MAIN.post(done);
        });
    }
}
