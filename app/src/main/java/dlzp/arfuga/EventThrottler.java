package dlzp.arfuga;

import java.util.LinkedList;

/**
 * Tracks events triggered in a certain period of time. Allows throttling of events by identifying
 * when they have been triggered too often or are still within throttling limits.
 */
public class EventThrottler {
    private final LinkedList<Long> triggerHistory = new LinkedList<>();
    private final int maxCountsInPeriod;
    private final long periodMs;

    public EventThrottler(int maxCountsInPeriod, long periodMs) {
        this.maxCountsInPeriod = maxCountsInPeriod;
        this.periodMs = periodMs;
    }

    /**
     * Call when the event tries to be triggered.
     * @return True when the event is within throttling limits and has been tracked. False when the
     * event should be prevented / is exceeding throttling limits and is not being tracked.
     */
    public boolean tryTriggerEvent() {
        while(true) {
            final Long oldestTrigger = triggerHistory.peekFirst();
            if(oldestTrigger == null) {
                break;
            }

            if((System.currentTimeMillis() - oldestTrigger) <= periodMs) {
                break;
            }

            triggerHistory.removeFirst();
        }

        if(triggerHistory.size() > maxCountsInPeriod) {
            return false;
        }

        triggerHistory.addLast(System.currentTimeMillis());
        return true;
    }
}
