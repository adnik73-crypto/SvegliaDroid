package app.sveglia;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

final class AlarmScheduler {
    static final String PREFS = "sveglia";
    static final String KEY = "items";

    private AlarmScheduler() {}

    static void replaceAll(Context c, String json) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cancelAll(c, p.getString(KEY, "[]"));
        p.edit().putString(KEY, json).apply();
        scheduleAll(c, json);
    }

    static void rescheduleSaved(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        scheduleAll(c, p.getString(KEY, "[]"));
    }

    private static PendingIntent pending(Context c, String id, boolean create) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("app.sveglia.ALARM");
        i.setData(Uri.parse("sveglia://alarm/" + id));
        i.putExtra("id", id);
        int flags = PendingIntent.FLAG_IMMUTABLE | (create ? PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(c, 0, i, flags);
    }

    private static void cancelAll(Context c, String json) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray arr = new JSONArray(json);
            for (int k = 0; k < arr.length(); k++) {
                String id = arr.getJSONObject(k).getString("id");
                PendingIntent pi = pending(c, id, false);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
        } catch (Exception ignored) {}
    }

    private static void scheduleAll(Context c, String json) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        PendingIntent show = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            JSONArray arr = new JSONArray(json);
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                long at = o.getLong("at");
                if (at <= now) continue;
                PendingIntent pi = pending(c, o.getString("id"), true);
                try {
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
                } catch (SecurityException e) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
            }
        } catch (Exception ignored) {}
    }
}
