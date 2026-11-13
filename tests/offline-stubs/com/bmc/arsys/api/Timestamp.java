package com.bmc.arsys.api;
public final class Timestamp {
    private final long time;
    public Timestamp(long value) { time=value; }
    @Override public boolean equals(Object other) { return other instanceof Timestamp t && time==t.time; }
    @Override public int hashCode() { return Long.hashCode(time); }
}
