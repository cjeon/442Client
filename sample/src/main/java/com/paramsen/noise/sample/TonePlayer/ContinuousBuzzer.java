package com.paramsen.noise.sample.TonePlayer;

/**
 * A buzzer that will continue playing until stop() is called.
 */
public class ContinuousBuzzer extends TonePlayer{
    protected double pausePeriodInMs = 5;
    protected int pauseTimeInMs = 1;

    /**
     * The time the buzzer should pause for every cycle in milliseconds.
     */
    public int getPauseTimeInMs() {
        return pauseTimeInMs;
    }

    /**
     * The time the buzzer should pause for every cycle in milliseconds.
     */
    public void setPauseTimeInMs(int pauseTimeInMs) {
        this.pauseTimeInMs = pauseTimeInMs;
    }

    /**
     * The time period between when a buzzer pause should occur in seconds.
     */
    public double getPausePeriodInMs() {
        return pausePeriodInMs;
    }

    /**
     * The time period between when a buzzer pause should occur in seconds.
     * IE pause the buzzer every X/pausePeriod seconds.
     */
    public void setPausePeriodInMs(double pausePeriodInMs) {
        this.pausePeriodInMs = pausePeriodInMs;
    }

    protected void asyncPlayTrack(final double toneFreqInHz) {
        playerWorker = new Thread(() -> {
            while (isPlaying) {
                // will pause every x seconds useful for determining when a certain amount
                // of time has passed while whatever the buzzer is signaling is active
                playTone(toneFreqInHz, pausePeriodInMs);
                try {
                    Thread.sleep(pauseTimeInMs);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        playerWorker.start();
    }
}
