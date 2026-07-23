package cl.exequiel.royalspin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic event timeline used to keep animation, sound and haptics synchronized. */
public final class AnimationTimeline {
    public interface Listener {
        void onTimelineEvent(int type, int argument, long scheduledAtMs);
    }

    private static final class Event {
        final long offsetMs;
        final int type;
        final int argument;
        boolean fired;

        Event(long offsetMs, int type, int argument) {
            this.offsetMs = Math.max(0L, offsetMs);
            this.type = type;
            this.argument = argument;
        }
    }

    private final List<Event> events = new ArrayList<>();
    private long startedAtMs;
    private boolean running;

    public AnimationTimeline add(long offsetMs, int type, int argument) {
        if (running) throw new IllegalStateException("Cannot add events while the timeline is running");
        events.add(new Event(offsetMs, type, argument));
        return this;
    }

    public void start(long startTimeMs) {
        events.sort(Comparator.comparingLong(event -> event.offsetMs));
        for (Event event : events) event.fired = false;
        startedAtMs = startTimeMs;
        running = !events.isEmpty();
    }

    public void dispatch(long nowMs, Listener listener) {
        if (!running) return;
        boolean pending = false;
        for (Event event : events) {
            if (!event.fired && nowMs >= startedAtMs + event.offsetMs) {
                event.fired = true;
                listener.onTimelineEvent(event.type, event.argument, startedAtMs + event.offsetMs);
            }
            if (!event.fired) pending = true;
        }
        running = pending;
    }

    public void fireRemaining(Listener listener) {
        for (Event event : events) {
            if (!event.fired) {
                event.fired = true;
                listener.onTimelineEvent(event.type, event.argument, startedAtMs + event.offsetMs);
            }
        }
        running = false;
    }

    public void clear() {
        events.clear();
        running = false;
        startedAtMs = 0L;
    }

    public boolean isRunning() {
        return running;
    }
}
