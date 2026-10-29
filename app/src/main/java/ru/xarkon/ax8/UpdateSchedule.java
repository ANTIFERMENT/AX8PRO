package ru.xarkon.ax8;
import android.app.job.*;
import android.content.*;
/** Optional periodic updates check; JobScheduler is subject to Android scheduling. */
public final class UpdateSchedule {
    private UpdateSchedule(){}
    private static final int ID=0x4188301;
    public static void configure(Context c){
        JobScheduler service=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(service==null)return;
        if(!c.getSharedPreferences("ax8_control",0).getBoolean("ota_background",false)){service.cancel(ID);return;}
        service.schedule(new JobInfo.Builder(ID,new ComponentName(c,UpdateJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(24L*60L*60L*1000L).build());
    }
}
