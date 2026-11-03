package app.sveglia;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Locale;

@CapacitorPlugin(name = "AlarmBridge")
public class AlarmBridgePlugin extends Plugin {
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String queuedText = null;
    private float queuedVol = 1f;

    @PluginMethod
    public void setAlarms(PluginCall call) {
        JSArray items = call.getArray("items", new JSArray());
        AlarmScheduler.replaceAll(getContext(), items.toString());
        call.resolve();
    }

    @PluginMethod
    public void getPending(PluginCall call) {
        SharedPreferences p = getContext().getSharedPreferences(AlarmScheduler.PREFS, Context.MODE_PRIVATE);
        String id = p.getString("pending", null);
        long at = p.getLong("pendingAt", 0);
        p.edit().remove("pending").apply();
        JSObject o = new JSObject();
        if (id != null) {
            o.put("id", id);
            o.put("at", at);
        }
        call.resolve(o);
    }

    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        JSObject o = new JSObject();
        o.put("overlay", Settings.canDrawOverlays(c));
        o.put("notif", NotificationManagerCompat.from(c).areNotificationsEnabled());
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        o.put("battery", pm.isIgnoringBatteryOptimizations(c.getPackageName()));
        call.resolve(o);
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        Context c = getContext();
        String pkg = c.getPackageName();
        String what = call.getString("what", "");
        Intent i;
        if ("overlay".equals(what)) {
            i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg));
        } else if ("notif".equals(what)) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        } else {
            i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg));
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception ignored) {}
        call.resolve();
    }

    @PluginMethod
    public void speak(PluginCall call) {
        final String text = call.getString("text", "");
        final float vol = call.getFloat("volume", 1f);
        if (tts == null) {
            queuedText = text;
            queuedVol = vol;
            tts = new TextToSpeech(getContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.ITALIAN);
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) {}
                        @Override public void onDone(String utteranceId) { notifyListeners("speakDone", new JSObject()); }
                        @Override public void onError(String utteranceId) { notifyListeners("speakDone", new JSObject()); }
                    });
                    ttsReady = true;
                    if (queuedText != null) doSpeak(queuedText, queuedVol);
                    queuedText = null;
                } else {
                    notifyListeners("speakDone", new JSObject());
                }
            });
        } else if (ttsReady) {
            doSpeak(text, vol);
        }
        call.resolve();
    }

    private void doSpeak(String text, float vol) {
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0f, Math.min(1f, vol)));
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sveglia");
    }

    @PluginMethod
    public void stopSpeak(PluginCall call) {
        queuedText = null;
        if (tts != null && ttsReady) tts.stop();
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
    }
}
