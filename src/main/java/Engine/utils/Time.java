package Engine.utils;

/**
 * Manages game timing, delta time, and frame rate calculations
 * Essential for smooth animations and physics simulations
 */
public class Time {

    private static Time instance;

    // Timing variables
    private long startTime;
    private long lastFrameTime;
    private long currentFrameTime;
    private float deltaTime;
    private float timeScale;

    // Frame rate calculations
    private int frameCount;
    private float frameTimeAccumulator;
    private float fps;
    private float averageDeltaTime;

    // Fixed timestep for physics
    private float fixedDeltaTime;
    private float fixedTimestep;
    private float accumulator;

    private Time() {
        startTime = System.nanoTime();
        lastFrameTime = startTime;
        currentFrameTime = startTime;
        deltaTime = 0.0f;
        timeScale = 1.0f; // Normal time flow

        fixedTimestep = 1.0f / 60.0f; // 60Hz physics
        fixedDeltaTime = fixedTimestep;
        accumulator = 0.0f;

        frameCount = 0;
        frameTimeAccumulator = 0.0f;
        fps = 0.0f;
        averageDeltaTime = 0.0f;

        System.out.println("Time system initialized with " + (1.0f / fixedTimestep) + "Hz fixed update");
    }

    public static Time getInstance() {
        if (instance == null) {
            instance = new Time();
        }
        return instance;
    }

    public void update() {
        currentFrameTime = System.nanoTime();

        // Calculate delta time in seconds
        long nanoDelta = currentFrameTime - lastFrameTime;
        deltaTime = nanoDelta / 1_000_000_000.0f;
        lastFrameTime = currentFrameTime;

        // Clamp delta time to avoid spiral of death
        if (deltaTime > 0.25f) {
            deltaTime = 0.25f;
            System.err.println("Warning: Large delta time detected, clamping to 0.25s");
        }

        // Update fixed timestep accumulator
        accumulator += deltaTime * timeScale;

        // Calculate FPS and average delta time
        updateFrameStats();
    }

    private void updateFrameStats() {
        frameCount++;
        frameTimeAccumulator += deltaTime;

        // Update FPS every second
        if (frameTimeAccumulator >= 1.0f) {
            fps = frameCount / frameTimeAccumulator;
            averageDeltaTime = frameTimeAccumulator / frameCount;

            // Reset for next second
            frameCount = 0;
            frameTimeAccumulator = 0.0f;

            // Occasionally print FPS for debugging
            if (Math.random() < 0.01f) { // 1% chance per second
                System.out.printf("FPS: %.1f, Avg Delta: %.3fms\n", fps, averageDeltaTime * 1000);
            }
        }
    }

    public boolean shouldDoFixedUpdate() {
        if (accumulator >= fixedTimestep) {
            accumulator -= fixedTimestep;
            return true;
        }
        return false;
    }

    // Getters for timing information

    public float getDeltaTime() {
        return deltaTime * timeScale;
    }

    public float getFixedDeltaTime() {
        return fixedTimestep;
    }

    public float getTimeScale() {
        return timeScale;
    }

    public void setTimeScale(float scale) {
        this.timeScale = Math.max(scale, 0.0f); // Prevent negative time scale
        System.out.println("Time scale set to: " + timeScale);
    }

    public float getFPS() {
        return fps;
    }

    public float getAverageDeltaTime() {
        return averageDeltaTime;
    }

    public float getTime() {
        return (System.nanoTime() - startTime) / 1_000_000_000.0f;
    }

    public float getUnscaledTime() {
        return (System.nanoTime() - startTime) / 1_000_000_000.0f;
    }

    public float getUnscaledDeltaTime() {
        return deltaTime;
    }

    public Timer createTimer(float duration) {
        return new Timer(duration);
    }

    public class Timer {
        private float duration;
        private float elapsed;
        private boolean running;

        public Timer(float duration) {
            this.duration = duration;
            this.elapsed = 0.0f;
            this.running = false;
        }

        public void start() {
            running = true;
            elapsed = 0.0f;
        }

        public void update(float delta) {
            if (running) {
                elapsed += delta;
                if (elapsed >= duration) {
                    running = false;
                }
            }
        }

        public boolean isFinished() {
            return !running && elapsed >= duration;
        }

        public boolean isRunning() {
            return running;
        }

        public float getProgress() {
            return Math.min(elapsed / duration, 1.0f);
        }

        public void reset() {
            elapsed = 0.0f;
            running = false;
        }

        public void setDuration(float newDuration) {
            this.duration = newDuration;
        }
    }

    public void cleanup() {
        System.out.println("Time system shutdown");
        System.out.printf("Final stats - Total runtime: %.1f seconds\n", getTime());
    }
}