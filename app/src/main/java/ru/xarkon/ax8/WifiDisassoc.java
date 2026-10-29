package ru.xarkon.ax8;

import android.content.SharedPreferences;
import java.util.*;
import java.util.regex.*;

/**
 * Optional precise Wi-Fi session disassociation on AX8 via an existing Telnet
 * interface. No guesswork: only act on a MAC actually reported by the kernel's
 * station dump, never on the current controlling handset.
 *
 * A temporary MAC blacklist is still the primary protection. Some firmwares
 * cannot deauthenticate existing stations, in which case we report the limit.
 */
final class WifiDisassoc {
    static final class Result {
        final String message;
        Result(String message){this.message=message;}
    }
    private WifiDisassoc(){}

    static Result disconnect(RouterClient router,SharedPreferences prefs,
                             List<String> targets,String owner,RouterClient.Progress progress){
        String own=RouterClient.normalizeMac(owner);
        LinkedHashSet<String> wanted=new LinkedHashSet<>();
        for(String item:targets){
            String mac=RouterClient.normalizeMac(item);
            if(!mac.isEmpty()&&!mac.equals(own))wanted.add(mac);
        }
        if(own.isEmpty()||wanted.isEmpty())return new Result("Не удалось безопасно определить целевые MAC");
        try(TelnetShell shell=new TelnetShell(router,
            prefs.getString("telnet_user","admin"),prefs.getString("telnet_pass","admin"))){
            TelnetShell.Result iw=shell.exec("iw dev",4000);
            if(iw.code!=0)return new Result("Команда iw dev недоступна на прошивке");
            Matcher m=Pattern.compile("(?m)^\\s*Interface\\s+([A-Za-z0-9_.-]+)\\s*$").matcher(iw.output);
            ArrayList<String> ifaces=new ArrayList<>();
            while(m.find())if(!ifaces.contains(m.group(1)))ifaces.add(m.group(1));
            if(ifaces.isEmpty())return new Result("Не найдены интерфейсы с поддержкой iw");
            Set<String> found=new HashSet<>(),done=new HashSet<>();
            StringBuilder errors=new StringBuilder();
            for(String iface:ifaces){
                TelnetShell.Result dump=shell.exec("iw dev "+iface+" station dump",4000);
                if(dump.code!=0)continue;
                for(String mac:wanted){
                    if(done.contains(mac))continue;
                    Pattern station=Pattern.compile("(?im)^\\s*Station\\s+"+Pattern.quote(mac)+"(?:\\s|$)");
                    if(!station.matcher(dump.output).find())continue;
                    found.add(mac);
                    // A MAC is normalized and the interface is parsed from the iw output;
                    // neither field accepts arbitrary shell characters.
                    TelnetShell.Result r=shell.exec("iw dev "+iface+" station del "+mac,4000);
                    if(r.code!=0){
                        // Some firmware exposes hostapd_cli instead of station del.
                        // Only try the exact interface/MAC already verified above.
                        TelnetShell.Result alt=shell.exec("command -v hostapd_cli >/dev/null 2>&1 && hostapd_cli -i "+iface+" deauthenticate "+mac,4000);
                        if(alt.code==0 && alt.output.toUpperCase(Locale.US).contains("OK"))r=alt;
                    }
                    if(r.code==0){done.add(mac);progress.note("Команда отключения Wi-Fi "+mac+" принята драйвером "+iface);}
                    else errors.append(mac).append(": ").append(r.output).append("; ");
                }
            }
            if(done.containsAll(wanted)){
                Thread.sleep(350);
                boolean remains=false;
                for(String iface:ifaces){
                    TelnetShell.Result after=shell.exec("iw dev "+iface+" station dump",4000);
                    if(after.code!=0)continue;
                    for(String mac:wanted)if(Pattern.compile("(?im)^\\s*Station\\s+"+Pattern.quote(mac)+"(?:\\s|$)")
                            .matcher(after.output).find())remains=true;
                }
                return new Result(remains?"Драйвер принял команду, но станция всё ещё числится подключённой":"");
            }
            if(!errors.toString().isEmpty())return new Result("Драйвер не принял отключение: "+errors);
            if(found.isEmpty())return new Result("Wi-Fi-драйвер не предоставляет информацию о станциях; разрыв соединения не подтверждён");
            return new Result("Не удалось отключить все станции: "+wanted.size()+" целевых, "+done.size()+" подтверждённых команд");
        }catch(Exception e){
            return new Result("Telnet/iw: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
        }
    }
}
