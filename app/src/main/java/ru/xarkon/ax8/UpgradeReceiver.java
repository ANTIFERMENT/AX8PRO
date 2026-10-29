package ru.xarkon.ax8;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Gives the user a reliable launch action after the platform package installer completes. */
public final class UpgradeReceiver extends BroadcastReceiver {
    private static final String CHANNEL="ax8pro_installed";
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(
            android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Установка обновлений",
                NotificationManager.IMPORTANCE_HIGH));
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if(launch==null)launch=new Intent(context,MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent open=PendingIntent.getActivity(context,0,launch,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notice=new Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("AX8 PRO CONTROL обновлено")
                .setContentText("Нажмите, чтобы открыть приложение")
                .setContentIntent(open).setAutoCancel(true).build();
            manager.notify(8057,notice);
        }catch(Exception ignored){}
    }
}
