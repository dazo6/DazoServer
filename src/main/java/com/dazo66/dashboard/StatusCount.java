package com.dazo66.dashboard;

public class StatusCount {

    private boolean done;
    private long count;

    public StatusCount() {
    }

    public StatusCount(boolean done, long count) {
        this.done = done;
        this.count = count;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
