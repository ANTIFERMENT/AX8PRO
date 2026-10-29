package ru.xarkon.ax8;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
/** Background metadata checker, never silently installs an APK. */
public final class UpdateJob extends JobService {
    @Override public boolean onStartJob(JobParameters params){
        new Thread(()->{
            try{
                UpdateManager.Release r=UpdateManager.fetchLatest();
                if(r.versionCode>UpdateManager.currentCode(this)){
                    android.content.SharedPreferences prefs=getSharedPreferences("ax8_control",0);
                    long notified=prefs.getLong("ota_notified_code",0);
                    if(r.versionCode>notified&&canNotify()){
                        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                        manager.createNotificationChannel(new NotificationChannel("ax8pro_ota","AX 8 PRO · Обновления",NotificationManager.IMPORTANCE_DEFAULT));
                        Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        PendingIntent open=PendingIntent.getActivity(this,123,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                        Notification n=new Notification.Builder(this,"ax8pro_ota").setSmallIcon(R.drawable.ic_app)
                            .setContentTitle("AX 8 PRO · обновление "+r.versionName)
                            .setContentText("Откройте приложение, чтобы установить новую версию")
                            .setContentIntent(open).setAutoCancel(true).build();
                        manager.notify(123,n);prefs.edit().putLong("ota_notified_code",r.versionCode).apply();
                    }
                }
            }catch(Exception ignored){}finally{jobFinished(params,false);}
        },"ax8pro-ota-job").start();return true;
    }
    private boolean canNotify(){return Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
    @Override public boolean onStopJob(JobParameters params){return true;}
}
