package ru.xarkon.ax8;

import java.util.*;
/** Detects persistent overspeed; limits in KB/s and rates in bytes/s. */
final class QosWatchdog {
    private final Map<String,Long> since=new HashMap<>();
    private long lastRepair=0;
    private String status="Не проверено";
    String status(){return status;}
    void check(List<RouterClient.Device> devices,DeviceTrafficMonitor traffic,QosController qos){
        Map<String,Integer> caps=qos.limits();long now=System.currentTimeMillis();boolean hasOver=false;
        for(RouterClient.Device dev:devices){
            Integer cap=caps.get(dev.mac);DeviceTrafficMonitor.Rate r=traffic.get(dev.mac);
            if(cap==null||cap<=0||r==null){since.remove(dev.mac);continue;}
            // 30% tolerance for short packet bursts / sampling jitter.
            boolean exceeds=r.dl>(cap*1000.0*1.30)||r.ul>(cap*1000.0*1.30);
            if(!exceeds){since.remove(dev.mac);continue;}
            hasOver=true;
            if(!since.containsKey(dev.mac))since.put(dev.mac,now);
            if(now-since.get(dev.mac)<4000)continue;
            status="ПРЕВЫШЕНИЕ: "+dev.mac+" · "+cap+" KB/s. Проверка tc обязательна";
            if(now-lastRepair>=90000){
                lastRepair=now;
                try{qos.restore(devices,note->{});status="QoS повторно применён. Требуется проверить скорость";}
                catch(Exception e){status="QoS НЕ ПРИМЕНЁН: "+e.getMessage();}
            }
        }
        if(!hasOver)status=caps.isEmpty()?"Лимиты не установлены":"Превышений не выявлено (по выборкам)";
    }
}
