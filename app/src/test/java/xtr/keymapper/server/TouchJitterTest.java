package xtr.keymapper.server;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TouchJitterTest {

    private TouchJitter touchJitter;

    @Before
    public void setUp() {
        touchJitter = new TouchJitter();
    }

    @Test
    public void testDisabledReturnsExactCoordinates() {
        touchJitter.setEnabled(false);
        touchJitter.setSpread(20f);

        TouchJitter.Point pt = touchJitter.wrapCoordinates(500f, 600f, InputService.DOWN, 1);
        assertEquals(500f, pt.x, 0.001f);
        assertEquals(600f, pt.y, 0.001f);
    }

    @Test
    public void testZeroSpreadReturnsExactCoordinates() {
        touchJitter.setEnabled(true);
        touchJitter.setSpread(0f);

        TouchJitter.Point pt = touchJitter.wrapCoordinates(500f, 600f, InputService.DOWN, 1);
        assertEquals(500f, pt.x, 0.001f);
        assertEquals(600f, pt.y, 0.001f);
    }

    @Test
    public void testEnabledAppliesJitterWithinSpreadRadius() {
        float spread = 15f;
        touchJitter.setEnabled(true);
        touchJitter.setSpread(spread);

        float originX = 500f;
        float originY = 500f;

        for (int i = 0; i < 100; i++) {
            touchJitter.clear();
            TouchJitter.Point pt = touchJitter.wrapCoordinates(originX, originY, InputService.DOWN, i);
            double dist = Math.hypot(pt.x - originX, pt.y - originY);
            assertTrue("Distance " + dist + " should be <= spread " + spread, dist <= spread + 0.001);
        }
    }

    @Test
    public void testStrokeConsistencyDownMoveUp() {
        touchJitter.setEnabled(true);
        touchJitter.setSpread(25f);

        int pointerId = 3;
        float targetX = 400f;
        float targetY = 300f;

        // DOWN
        TouchJitter.Point downPt = touchJitter.wrapCoordinates(targetX, targetY, InputService.DOWN, pointerId);

        // MOVE 1
        TouchJitter.Point movePt1 = touchJitter.wrapCoordinates(targetX, targetY, InputService.MOVE, pointerId);
        assertEquals(downPt.x, movePt1.x, 0.0001f);
        assertEquals(downPt.y, movePt1.y, 0.0001f);

        // MOVE 2 with different target (e.g. dragging)
        float movedTargetX = 450f;
        float movedTargetY = 350f;
        TouchJitter.Point movePt2 = touchJitter.wrapCoordinates(movedTargetX, movedTargetY, InputService.MOVE, pointerId);
        // Offset relative to new target should match DOWN offset
        float downOffsetX = downPt.x - targetX;
        float downOffsetY = downPt.y - targetY;
        assertEquals(movedTargetX + downOffsetX, movePt2.x, 0.0001f);
        assertEquals(movedTargetY + downOffsetY, movePt2.y, 0.0001f);

        // UP
        TouchJitter.Point upPt = touchJitter.wrapCoordinates(targetX, targetY, InputService.UP, pointerId);
        assertEquals(downPt.x, upPt.x, 0.0001f);
        assertEquals(downPt.y, upPt.y, 0.0001f);
    }

    @Test
    public void testConsecutiveTouchesReceiveDifferentOffsets() {
        touchJitter.setEnabled(true);
        touchJitter.setSpread(20f);

        int pointerId = 0;
        float targetX = 200f;
        float targetY = 200f;

        Set<String> coordinates = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            TouchJitter.Point down = touchJitter.wrapCoordinates(targetX, targetY, InputService.DOWN, pointerId);
            touchJitter.wrapCoordinates(targetX, targetY, InputService.UP, pointerId);
            coordinates.add(down.x + "," + down.y);
        }

        // Over 20 taps with 20px spread, there should be multiple distinct coordinates
        assertTrue("Expected distinct coordinates across touches", coordinates.size() > 15);
    }

    @Test
    public void testIndependentPointerIds() {
        touchJitter.setEnabled(true);
        touchJitter.setSpread(20f);

        float targetX = 100f;
        float targetY = 100f;

        TouchJitter.Point p0 = touchJitter.wrapCoordinates(targetX, targetY, InputService.DOWN, 0);
        TouchJitter.Point p1 = touchJitter.wrapCoordinates(targetX, targetY, InputService.DOWN, 1);

        // With high probability, two separate pointers get different offsets
        assertNotEquals(p0.x, p1.x, 0.00001f);

        // Up on pointer 0 should not affect pointer 1
        touchJitter.wrapCoordinates(targetX, targetY, InputService.UP, 0);
        TouchJitter.Point p1Move = touchJitter.wrapCoordinates(targetX, targetY, InputService.MOVE, 1);
        assertEquals(p1.x, p1Move.x, 0.0001f);
        assertEquals(p1.y, p1Move.y, 0.0001f);
    }

    @Test
    public void testScreenBoundaryClamping() {
        touchJitter.setEnabled(true);
        touchJitter.setSpread(30f);
        touchJitter.setScreenDimensions(1920, 1080);

        // Near (0, 0)
        for (int i = 0; i < 50; i++) {
            TouchJitter.Point pt = touchJitter.wrapCoordinates(0f, 0f, InputService.DOWN, i);
            assertTrue("X should not be negative: " + pt.x, pt.x >= 0f);
            assertTrue("Y should not be negative: " + pt.y, pt.y >= 0f);
            touchJitter.wrapCoordinates(0f, 0f, InputService.UP, i);
        }

        // Near (1920, 1080)
        for (int i = 0; i < 50; i++) {
            TouchJitter.Point pt = touchJitter.wrapCoordinates(1920f, 1080f, InputService.DOWN, i);
            assertTrue("X should not exceed 1920: " + pt.x, pt.x <= 1920f);
            assertTrue("Y should not exceed 1080: " + pt.y, pt.y <= 1080f);
            touchJitter.wrapCoordinates(1920f, 1080f, InputService.UP, i);
        }
    }
}
