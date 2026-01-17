package music.resona.playback;

import androidx.media3.exoplayer.DefaultLoadControl;

/**
 * Optimized LoadControl for instant playback with minimal buffering delays.
 * Configured for fast start and smooth playback experience.
 */
public class OptimizedLoadControl {
    
    // Optimized buffer sizes for instant playback
    private static final int MIN_BUFFER_MS = 500;          // 0.5 seconds minimum buffer
    private static final int MAX_BUFFER_MS = 30000;       // 30 seconds maximum buffer
    private static final int BUFFER_FOR_PLAYBACK_MS = 250; // 0.25 seconds to start playback
    private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1000; // 1 second after rebuffer
    
    // Target buffer sizes for smooth experience
    private static final int TARGET_BUFFER_BYTES = 2 * 1024 * 1024; // 2 MB target buffer
    
    /**
     * Creates an optimized LoadControl configured for instant playback.
     */
    public static DefaultLoadControl create() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .setTargetBufferBytes(TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(true) // Prioritize low latency
                .build();
    }
}