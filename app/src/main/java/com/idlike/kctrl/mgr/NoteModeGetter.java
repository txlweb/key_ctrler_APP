package com.idlike.kctrl.mgr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class NoteModeGetter extends Service {
    private static final String CHANNEL_ID = "keep_alive_channel";
    private PowerManager.WakeLock wakeLock;
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        PowerManager powerManager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "com.idlike.kctrl.app:TorchWakeLock"
        );

        // 保持10分钟（单位毫秒）
        wakeLock.acquire(10 * 60 * 1000L);  // 传 0 表示无限时持有
    }
    private void setRingerMode(int mode) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        switch (mode) {
            case 0:
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);  // 响铃
                Toast.makeText(this, "已切换到响铃模式", Toast.LENGTH_SHORT).show();

                break;
            case 1:
                audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE); // 震动
                Toast.makeText(this, "已切换到震动模式", Toast.LENGTH_SHORT).show();

                break;
            case 2:
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!nm.isNotificationPolicyAccessGranted()) {
                        // 这里需要引导用户开启权限
                        Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return;
                    }
                }
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);  // 静音
                Toast.makeText(this, "已切换到静音模式", Toast.LENGTH_SHORT).show();

                break;
            default:
                // 不处理或默认行为
                break;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("模式检测服务")
                .setContentText("正在写入当前模式")
                .setSmallIcon(android.R.drawable.ic_dialog_info)  // 示例图标
                .build();

        startForeground(1, notification);

        // 读取传入参数
        if (intent != null && intent.hasExtra("mode")) {
            int mode = intent.getIntExtra("mode", -1);
            setRingerMode(mode);
        }

        writeCurrentModeToFile(this);

        stopSelf(); // 写完立即停止服务

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "模式检测服务通知",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void writeCurrentModeToFile(Context context) {
        String modeStr = getModeString(context);
        File outFile = new File(context.getExternalFilesDir(null), "mode.txt");

        try (FileWriter writer = new FileWriter(outFile)) {
            writer.write(modeStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getModeString(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int ringer = audioManager.getRingerMode();
        int zen = 2; // INTERRUPTION_FILTER_ALL 默认值

        if (Build.VERSION.SDK_INT >= 23) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            zen = nm.getCurrentInterruptionFilter();
        }

        if (zen == NotificationManager.INTERRUPTION_FILTER_NONE) {
            return "勿扰";
        } else if (ringer == AudioManager.RINGER_MODE_SILENT) {
            return "静音";
        } else if (ringer == AudioManager.RINGER_MODE_VIBRATE) {
            return "震动";
        } else {
            return "响铃";
        }
    }
}
