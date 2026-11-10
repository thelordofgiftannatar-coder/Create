package com.primeone.player;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.MediaMetadata;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Base64;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.UUID;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> uploadCallback;
    private static final int FILE_PICKER = 41;
    private static final String CHANNEL = "prime_one_general";
    private MediaSession mediaSession;
    private long mediaPositionMs = 0L;
    private long mediaDurationMs = 0L;
    private boolean mediaPlaying = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(9,13,22));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(9,13,22));
        createNotificationChannel();
        web = new WebView(this);
        web.setBackgroundColor(android.graphics.Color.rgb(9,13,22));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setDatabaseEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowContentAccess(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.addJavascriptInterface(new NativeBridge(this), "PrimeNative");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                Intent intent = params.createIntent();
                try { startActivityForResult(intent, FILE_PICKER); }
                catch (Exception e) { uploadCallback = null; callback.onReceiveValue(null); return false; }
                return true;
            }
            @Override public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.grant(request.getResources());
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl(); String scheme = uri.getScheme();
                if ("file".equals(scheme) && uri.getPath() != null && uri.getPath().startsWith("/android_asset/")) return false;
                if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                    return true;
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 52);
    }



    private void initMediaSession() {
        mediaSession = new MediaSession(this, "Prime One");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { runPlayerCommand("play"); }
            @Override public void onPause() { runPlayerCommand("pause"); }
            @Override public void onSkipToNext() { runPlayerCommand("next"); }
            @Override public void onSkipToPrevious() { runPlayerCommand("previous"); }
            @Override public void onSeekTo(long pos) { runPlayerCommand("seekTo", pos / 1000.0); }
        });
    }

    private void runPlayerCommand(String command) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(
                "window.PrimeOneNativeControls && window.PrimeOneNativeControls." + command + " && window.PrimeOneNativeControls." + command + "()",
                null
            );
        });
    }

    private void runPlayerCommand(String command, double value) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(
                "window.PrimeOneNativeControls && window.PrimeOneNativeControls." + command + " && window.PrimeOneNativeControls." + command + "(" + value + ")",
                null
            );
        });
    }

    private void updateNativePlaybackState(int state) {
        if (mediaSession == null) return;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
            PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT |
            PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        PlaybackState.Builder builder = new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, mediaPositionMs, mediaPlaying ? 1.0f : 0.0f);
        mediaSession.setPlaybackState(builder.build());
        mediaSession.setActive(state != PlaybackState.STATE_STOPPED);
    }

    @Override protected void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
\n    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER && uploadCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            uploadCallback.onReceiveValue(results); uploadCallback = null;
        }
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Prime One", NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    private File trashDir() { File f = new File(getExternalFilesDir(null), "PrimeOneTrash"); if (!f.exists()) f.mkdirs(); return f; }

    public class NativeBridge {
        private final Context ctx;
        NativeBridge(Context c) { ctx = c; }
        @JavascriptInterface public boolean hasAllFilesAccess() {
            return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
        }
        @JavascriptInterface public void requestAllFilesAccess() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                    try { Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())); startActivity(i); }
                    catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                } else Toast.makeText(ctx, "Full storage access is already enabled", Toast.LENGTH_SHORT).show();
            });
        }
        @JavascriptInterface public String listFiles(String query) {
            JSONArray out = new JSONArray();
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return out.toString();
            File root = Environment.getExternalStorageDirectory();
            ArrayDeque<File> q = new ArrayDeque<>(); q.add(root);
            String needle = query == null ? "" : query.toLowerCase(); int count = 0;
            while (!q.isEmpty() && count < 2500) {
                File dir = q.removeFirst(); File[] children = dir.listFiles(); if (children == null) continue;
                for (File f : children) {
                    String path = f.getAbsolutePath();
                    if (path.contains("/Android/data/") || path.contains("/Android/obb/")) continue;
                    if (f.isDirectory()) { if (!f.isHidden()) q.addLast(f); continue; }
                    if (!f.isFile() || !f.canRead()) continue;
                    if (!needle.isEmpty() && !f.getName().toLowerCase().contains(needle)) continue;
                    try { JSONObject o = new JSONObject(); o.put("name", f.getName()); o.put("path", path); o.put("size", f.length()); o.put("modified", f.lastModified()); o.put("type", mime(f.getName())); out.put(o); count++; }
                    catch (Exception ignored) {}
                    if (count >= 2500) break;
                }
            }
            return out.toString();
        }
        @JavascriptInterface public String listTrash() {
            JSONArray a = new JSONArray(); File[] fs = trashDir().listFiles(); if (fs == null) return a.toString();
            for (File f : fs) if (f.isFile() && f.getName().endsWith(".meta")) try {
                String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); JSONObject o = new JSONObject(json);
                String stored = o.optString("stored"); File data = new File(trashDir(), stored);
                if (data.exists()) { o.put("size", data.length()); a.put(o); }
            } catch (Exception ignored) {}
            return a.toString();
        }
        @JavascriptInterface public boolean moveToTrash(String path) {
            try {
                File src = safeFile(path); if (!src.isFile() || !src.exists()) return false;
                String id = UUID.randomUUID().toString(); String stored = id + ".bin"; File dest = new File(trashDir(), stored);
                Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                JSONObject meta = new JSONObject(); meta.put("id", id); meta.put("name", src.getName()); meta.put("original", src.getAbsolutePath()); meta.put("stored", stored); meta.put("deletedAt", System.currentTimeMillis());
                try (FileOutputStream fos = new FileOutputStream(new File(trashDir(), id + ".meta"))) { fos.write(meta.toString().getBytes(StandardCharsets.UTF_8)); }
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public boolean restoreTrash(String id) {
            try {
                File metaFile = new File(trashDir(), id + ".meta"); if (!metaFile.exists()) return false;
                JSONObject m = new JSONObject(new String(Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8));
                File data = new File(trashDir(), m.getString("stored")); File dest = new File(m.getString("original"));
                if (!dest.getCanonicalPath().startsWith(Environment.getExternalStorageDirectory().getCanonicalPath() + File.separator) || dest.exists()) return false;
                File parent = dest.getParentFile(); if (parent != null) parent.mkdirs();
                Files.move(data.toPath(), dest.toPath()); metaFile.delete(); return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public boolean deleteForever(String id) {
            try { File mf = new File(trashDir(), id + ".meta"); if (!mf.exists()) return false; JSONObject m = new JSONObject(new String(Files.readAllBytes(mf.toPath()), StandardCharsets.UTF_8)); new File(trashDir(), m.getString("stored")).delete(); mf.delete(); return true; }
            catch (Exception e) { return false; }
        }
        @JavascriptInterface public String readText(String path) {
            try { File f = safeFile(path); if (f.length() > 2_000_000) return ""; return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
            catch (Exception e) { return ""; }
        }
        @JavascriptInterface public boolean writeText(String path, String value) {
            try { File f = safeFile(path); if (!f.isFile() || value == null || value.length() > 2_000_000) return false; Files.write(f.toPath(), value.getBytes(StandardCharsets.UTF_8)); return true; }
            catch (Exception e) { return false; }
        }
        @JavascriptInterface public String readImage(String path) {
            try {
                File f = safeFile(path); if (!f.isFile() || f.length() > 12_000_000) return "";
                String n = f.getName().toLowerCase();
                if (!(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".heic"))) return "";
                String mime = n.endsWith(".png") ? "image/png" : n.endsWith(".webp") ? "image/webp" : n.endsWith(".gif") ? "image/gif" : n.endsWith(".bmp") ? "image/bmp" : n.endsWith(".heic") ? "image/heic" : "image/jpeg";
                return "data:" + mime + ";base64," + Base64.encodeToString(Files.readAllBytes(f.toPath()), Base64.NO_WRAP);
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public void updateMediaMetadata(String title, String artist, String album, String artworkData) {
            runOnUiThread(() -> {
                if (mediaSession == null) return;
                android.media.MediaMetadata.Builder metadata = new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title == null ? "Prime One" : title)
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, artist == null ? "" : artist)
                    .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, album == null ? "Prime One" : album)
                    .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, mediaDurationMs);
                try {
                    android.graphics.Bitmap art = null;
                    if (artworkData != null && artworkData.startsWith("data:image/") && artworkData.contains(",")) {
                        String encoded = artworkData.substring(artworkData.indexOf(",") + 1);
                        if (encoded.length() < 3_000_000) {
                            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                            art = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        }
                    }
                    if (art == null) art = BitmapFactory.decodeResource(getResources(), com.primeone.player.R.mipmap.ic_launcher);
                    if (art != null) {
                        int edge = Math.max(art.getWidth(), art.getHeight());
                        if (edge > 512) art = android.graphics.Bitmap.createScaledBitmap(art,
                            Math.max(1, art.getWidth() * 512 / edge), Math.max(1, art.getHeight() * 512 / edge), true);
                        metadata.putBitmap(android.media.MediaMetadata.METADATA_KEY_ART, art);
                        metadata.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, art);
                    }
                } catch (Exception ignored) {}
                mediaSession.setMetadata(metadata.build());
                mediaSession.setActive(true);
            });
        }
        @JavascriptInterface public void updateMediaState(String state) {
            runOnUiThread(() -> {
                mediaPlaying = "playing".equals(state);
                updateNativePlaybackState(mediaPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
            });
        }
        @JavascriptInterface public void updateMediaPosition(double positionSeconds, double durationSeconds) {
            runOnUiThread(() -> {
                mediaPositionMs = Math.max(0L, (long)(positionSeconds * 1000));
                mediaDurationMs = Math.max(0L, (long)(durationSeconds * 1000));
                if (mediaSession != null && mediaSession.getController().getMetadata() != null) {
                    android.media.MediaMetadata metadata = new android.media.MediaMetadata.Builder(mediaSession.getController().getMetadata())
                        .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, mediaDurationMs).build();
                    mediaSession.setMetadata(metadata);
                    updateNativePlaybackState(mediaPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
                }
            });
        }

        @JavascriptInterface public void notifyUser(String title, String message) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 52); return; }
                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title == null ? "Prime One" : title).setContentText(message == null ? "" : message).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true);
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)System.currentTimeMillis(), b.build());
            });
        }
        private File safeFile(String path) throws Exception {
            File f = new File(path); String root = Environment.getExternalStorageDirectory().getCanonicalPath();
            if (!f.getCanonicalPath().startsWith(root + File.separator)) throw new SecurityException("Outside shared storage");
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) throw new SecurityException("Storage permission required");
            return f;
        }
        private String mime(String n) {
            String x = n.toLowerCase();
            if (x.matches(".*\\.(mp3|m4a|aac|wav|flac|ogg|opus|wma)$")) return "audio";
            if (x.matches(".*\\.(jpg|jpeg|png|gif|webp|heic|bmp)$")) return "image";
            if (x.matches(".*\\.(mp4|mkv|mov|webm|3gp)$")) return "video";
            if (x.matches(".*\\.(txt|md|json|xml|csv|html|css|js|log)$")) return "text";
            return "file";
        }
    }
}
