package ru.xarkon.ax8;

/**
 * Independent state machine for verifying the requested LTE Band after a router reboot.
 * No Android dependencies, no network operations and no reboot calls.
 *
 * WebUI may return a valid snapshot before the reboot actually starts, so an early
 * LTE-online sample is never sufficient evidence of a successful transition.
 */
public final class BandSwitchTracker {
    public static final int NONE = -1;
    public static final String WAITING = "waiting";
    public static final String CONFIRMED = "confirmed";
    public static final String AUTO_UNVERIFIED = "auto_unverified";
    public static final String TIMED_OUT = "timed_out";

    private static final long DEADLINE_MS = 180_000L;
    private static final long MIN_REBOOT_SETTLE_MS = 12_000L;
    private static final long CONFIRM_SPACING_MS = 1_500L;
    private static final int REQUIRED_MATCHES = 2;

    private int requested = NONE;
    private long since = 0L;
    private long lastMatchAt = 0L;
    private int matches = 0;
    private boolean wentOffline = false;

    public synchronized void begin(int band, long now) {
        if (band < 0) throw new IllegalArgumentException("Band must be AUTO (0) or positive");
        requested = band;
        since = now;
        lastMatchAt = 0L;
        matches = 0;
        wentOffline = false;
    }
    public synchronized void restore(int band, long start, long now) {
        if (band >= 0 && start > 0 && start <= now && now - start < DEADLINE_MS) begin(band,start);
        else clear();
    }
    public synchronized boolean isPending() { return requested != NONE; }
    public synchronized int requestedBand() { return requested; }
    public synchronized long startedAt() { return since; }
    public synchronized void recordUnavailable() {
        if (isPending()) {wentOffline = true;matches=0;lastMatchAt=0L;}
    }
    public synchronized String observe(String liveBand, String ppp, long now) {
        if (!isPending()) return WAITING;
        if (now - since > DEADLINE_MS) {clear();return TIMED_OUT;}
        boolean connected = "ppp_connected".equals(ppp);
        String b = liveBand==null?"":liveBand.trim().toUpperCase(java.util.Locale.US).replace("B","");
        boolean bandMatches = requested == 0 ? !b.isEmpty() : String.valueOf(requested).equals(b);
        if (!connected || !bandMatches) {
            matches = 0; lastMatchAt = 0L;
            if (!connected) wentOffline = true;
            return WAITING;
        }
        // A fresh, authenticated LTE response must be received after the reboot settling window.
        if (now - since < MIN_REBOOT_SETTLE_MS) return WAITING;
        if (lastMatchAt == 0L || now - lastMatchAt >= CONFIRM_SPACING_MS) {
            matches++;lastMatchAt=now;
        }
        if (matches >= REQUIRED_MATCHES) {
            boolean auto = requested == 0;
            clear();
            // Active Band cannot prove the previously allowed AUTO band mask.
            return auto?AUTO_UNVERIFIED:CONFIRMED;
        }
        return WAITING;
    }
    public synchronized String checkTimeout(long now) {
        if (isPending() && now - since > DEADLINE_MS) {clear();return TIMED_OUT;}
        return WAITING;
    }
    public synchronized void clear() {
        requested=NONE;since=0L;lastMatchAt=0L;matches=0;wentOffline=false;
    }
}
