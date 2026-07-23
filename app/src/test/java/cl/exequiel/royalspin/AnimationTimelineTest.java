package cl.exequiel.royalspin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AnimationTimelineTest {
    @Test
    public void dispatchesEventsInScheduledOrder() {
        AnimationTimeline timeline = new AnimationTimeline();
        List<Integer> received = new ArrayList<>();
        timeline.add(300L, 3, 0)
                .add(100L, 1, 0)
                .add(200L, 2, 0);
        timeline.start(1_000L);

        timeline.dispatch(1_150L, (type, argument, at) -> received.add(type));
        assertEquals(List.of(1), received);
        assertTrue(timeline.isRunning());

        timeline.dispatch(1_350L, (type, argument, at) -> received.add(type));
        assertEquals(List.of(1, 2, 3), received);
        assertFalse(timeline.isRunning());
    }

    @Test
    public void fireRemainingEmitsOnlyPendingEvents() {
        AnimationTimeline timeline = new AnimationTimeline();
        List<Integer> received = new ArrayList<>();
        timeline.add(10L, 1, 0).add(20L, 2, 0).start(100L);
        timeline.dispatch(115L, (type, argument, at) -> received.add(type));
        timeline.fireRemaining((type, argument, at) -> received.add(type));

        assertEquals(List.of(1, 2), received);
        assertFalse(timeline.isRunning());
    }
}
