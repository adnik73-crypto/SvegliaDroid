package app.sveglia;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {
    static final int NOTIF_ID = 1001;
    static final String CHANNEL = "sveglia_alarm";

    @Override
    public void onReceive(Context c, Intent in) {
        String id = in.getStringExtra("id");
        if (id == null) return;

        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sveglia:alarm");
        wl.acquire(60000);

        c.getSharedPreferences(AlarmScheduler.PREFS, Context.MODE_PRIVATE).edit()
                .putString("pending", id)
                .putLong("pendingAt", System.currentTimeMillis())
                .apply();

        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("alarmId", id);

        PendingIntent pi = PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "SvegliaDroid", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("SvegliaDroid")
                .setContentText("Tocca per aprire")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true);
        try {
            nm.notify(NOTIF_ID, b.build());
        } catch (Exception ignored) {}

        try {
            c.startActivity(open);
        } catch (Exception ignored) {}
    }
}
