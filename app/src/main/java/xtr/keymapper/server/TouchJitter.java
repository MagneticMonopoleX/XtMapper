package xtr.keymapper.server;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages coordinate jitter / random spread for touch event dispatching.
 * Prevents identical coordinate dispatching by wrapping target X and Y with
 * a bounded random offset, maintaining stroke consistency between DOWN, MOVE, and UP.
 */
public class TouchJitter {

    public static class Point {
        public final float x;
        public final float y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return Float.compare(point.x, x) == 0 && Float.compare(point.y, y) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "Point{" + "x=" + x + ", y=" + y + '}';
        }
    }

    private volatile boolean enabled = false;
    private volatile float spread = 15f;
    private volatile int screenWidth = 0;
    private volatile int screenHeight = 0;

    private final Random random;
    private final Map<Integer, Point> activeOffsets = new ConcurrentHashMap<>();

    public TouchJitter() {
        this(new Random());
    }

    public TouchJitter(Random random) {
        this.random = random;
    }

    public TouchJitter(boolean enabled, float spread) {
        this(new Random());
        this.enabled = enabled;
        this.spread = spread;
    }

    public TouchJitter(boolean enabled, float spread, Random random) {
        this.random = random;
        this.enabled = enabled;
        this.spread = spread;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSpread(float spread) {
        this.spread = spread;
    }

    public float getSpread() {
        return spread;
    }

    public void setScreenDimensions(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void updateConfig(boolean enabled, float spread) {
        this.enabled = enabled;
        this.spread = spread;
    }

    public void clear() {
        activeOffsets.clear();
    }

    /**
     * Wrap target X and Y with a random spread for touch dispatching.
     * Ensures DOWN, MOVE, and UP events for the same stroke share consistent offset
     * to prevent spurious drag/scroll detection.
     *
     * @param x target X coordinate
     * @param y target Y coordinate
     * @param action InputService action (UP=0, DOWN=1, MOVE=2)
     * @param pointerId pointer identifier
     * @return Point containing bounded, jittered coordinates
     */
    public Point wrapCoordinates(float x, float y, int action, int pointerId) {
        if (!enabled || spread <= 0f) {
            return new Point(x, y);
        }

        Point offset;
        if (action == InputService.DOWN) {
            // New touch stroke: generate new random error/spread
            offset = generateRandomSpread(spread);
            activeOffsets.put(pointerId, offset);
        } else if (action == InputService.UP) {
            // Touch release: reuse active stroke offset if present, then clean up
            offset = activeOffsets.remove(pointerId);
            if (offset == null) {
                offset = generateRandomSpread(spread);
            }
        } else {
            // MOVE or other ongoing action: preserve existing offset
            offset = activeOffsets.get(pointerId);
            if (offset == null) {
                offset = generateRandomSpread(spread);
                activeOffsets.put(pointerId, offset);
            }
        }

        float jitteredX = x + offset.x;
        float jitteredY = y + offset.y;

        // Wrap / clamp within screen bounds if screen dimensions are known
        if (screenWidth > 0) {
            jitteredX = Math.max(0f, Math.min((float) screenWidth, jitteredX));
        }
        if (screenHeight > 0) {
            jitteredY = Math.max(0f, Math.min((float) screenHeight, jitteredY));
        }

        return new Point(jitteredX, jitteredY);
    }

    /**
     * Generate uniform random spread within a circular disk of radius maxSpread.
     *
     * @param maxSpread maximum offset radius in pixels
     * @return Point random offset (dx, dy)
     */
    public Point generateRandomSpread(float maxSpread) {
        double angle = random.nextDouble() * 2.0 * Math.PI;
        // Square root of uniform random gives uniform areal distribution on a disk
        double r = Math.sqrt(random.nextDouble()) * maxSpread;
        float dx = (float) (r * Math.cos(angle));
        float dy = (float) (r * Math.sin(angle));
        return new Point(dx, dy);
    }
}
