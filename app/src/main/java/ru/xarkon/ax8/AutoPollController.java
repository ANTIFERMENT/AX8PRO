package ru.xarkon.ax8;

/** Latency-based cadence, not an undocumented router capability claim. */
public final class AutoPollController {
    private AutoPollController(){}
    public static int clamp(int ms){return Math.max(500,Math.min(5000,((ms+50)/100)*100));}
    /** Pick the shortest stable interval from observed full-request duration. */
    public static int next(int previousMs,long elapsedMs,boolean successful){
        int old=clamp(previousMs);
        if(!successful){return clamp((int)Math.min(5000L,Math.round(old*1.65)+100L));}
        // Allow a safety margin for small fluctuations. Bounded by the user-visible 0.5–5s range.
        long raw=Math.max(500L,Math.min(5000L,Math.round(elapsedMs*1.18d)+90L));
        int target=clamp((int)raw);
        // Move quickly toward a slower interval, cautiously return to faster polling.
        int chosen=target>old?target:(int)Math.round(old*.45d+target*.55d);
        return clamp(chosen);
    }
}