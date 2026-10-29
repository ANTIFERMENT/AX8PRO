package ru.xarkon.ax8;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Identify the handset by its current Wi-Fi IPv4, never by hostname or a random MAC. */
final class OwnerIdentity {
    static final class Result {
        final String mac, reason, origin;
        final boolean manualSelectionPossible;
        Result(String mac,String reason,String origin,boolean manualSelectionPossible){
            this.mac=mac;this.reason=reason;this.origin=origin;this.manualSelectionPossible=manualSelectionPossible;
        }
        boolean resolved(){return !mac.isEmpty();}
    }
    private OwnerIdentity(){}

    static LinkedHashSet<String> wifiIps(Context ctx){
        LinkedHashSet<String> ips=new LinkedHashSet<>();
        try {
            ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(cm!=null)for(Network network:cm.getAllNetworks()){
                NetworkCapabilities caps=cm.getNetworkCapabilities(network);
                if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
                LinkProperties lp=cm.getLinkProperties(network);
                if(lp==null)continue;
                for(LinkAddress addr:lp.getLinkAddresses()){
                    InetAddress ip=addr.getAddress();
                    if(ip instanceof Inet4Address&&!ip.isLoopbackAddress()&&!ip.isLinkLocalAddress())ips.add(ip.getHostAddress());
                }
            }
        }catch(Exception ignored){}
        // Wi-Fi might not be Android's default network if the router itself has no internet.
        try {
            WifiManager wifi=(WifiManager)ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info=wifi==null?null:wifi.getConnectionInfo();
            int ip=info==null?0:info.getIpAddress();
            if(ip!=0){
                String candidate=(ip&255)+"."+((ip>>8)&255)+"."+((ip>>16)&255)+"."+((ip>>24)&255);
                ips.add(candidate);
            }
        }catch(Exception ignored){}
        return ips;
    }

    /** Fail closed on conflicting sources or stale LAN records. */
    static Result resolve(Context ctx,List<RouterClient.Device> online,RouterClient.BlockState block,String selectedMac){
        LinkedHashSet<String> addresses=wifiIps(ctx);
        String fromRouter=RouterClient.normalizeMac(block==null?"":block.userMac);
        String selected=RouterClient.normalizeMac(selectedMac);
        LinkedHashSet<String> matches=new LinkedHashSet<>();
        for(RouterClient.Device d:online)if(addresses.contains(d.ip))matches.add(d.mac);
        String mac="",source="";
        if(!addresses.isEmpty()){
            if(matches.size()!=1)return new Result("","IP телефона: "+String.join(", ",addresses)+
                ". Совпадений в клиентах AX8: "+matches.size()+". Обновите список и проверьте подключение телефона к Wi-Fi AX8.","",false);
            mac=matches.iterator().next();source="по Wi-Fi IP";
            // The WebUI user MAC is often stale or represents a separate admin session.
            // An unambiguous current Wi-Fi IP match is more reliable than that field.
        }else if(!fromRouter.isEmpty()){
            mac=fromRouter;source="WebUI AX8";
        }else if(!selected.isEmpty()){
            mac=selected;source="ручной выбор";
        }else return new Result("","AX8 не сообщил MAC, а IP телефона по Wi-Fi пока недоступен. Выберите управляющее устройство вручную.","",true);
        int occurrences=0;
        for(RouterClient.Device d:online)if(mac.equals(d.mac))occurrences++;
        if(occurrences!=1)return new Result("","Управляющий MAC "+mac+" отсутствует в актуальном списке подключённых клиентов.","",addresses.isEmpty());
        if(block!=null&&block.black.contains(mac))return new Result("","MAC управляющего устройства уже числится заблокированным. Проверьте список блокировок.","",false);
        return new Result(mac,"Управляющее устройство: "+mac+" ("+source+")",source,false);
    }
}