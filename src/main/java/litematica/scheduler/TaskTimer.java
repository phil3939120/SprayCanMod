package litematica.scheduler;

// TODO move to malilib as IntervalTimer
public class TaskTimer
{
    private int interval;
    private int counter;

    public TaskTimer(int interval)
    {
        this.interval = interval;
        this.counter = interval;
    }

    /**
     * Ticks the timer. Returns true when it hits 0, and then resets itself back to the interval.
     * @return true if the timer just counted down to 0
     */
    public boolean tick()
    {
        if (--this.counter <= 0)
        {
            this.reset();
            return true;
        }

        return false;
    }

    public void reset()
    {
        this.counter = this.interval;
    }

    /**
     * Sets the current delay time for the ongoing cycle.
     * Note that this changes the total interval of the ongoing cycle if called in the middle of the cycle,
     * but this does not change the interval that is loaded at reset.
     */
    public void setNextDelay(int delay)
    {
        this.counter = delay;
    }

    /**
     * Sets the trigger interval. This interval is used whenever the timer is reset.
     */
    public void setInterval(int interval)
    {
        this.interval = interval;
    }
}
