package Engine.astro;

import org.joml.Vector3f;

/**
 * Floating-origin ("camera-relative") coordinate infrastructure.
 *
 * <p>OpenGL vertex positions use {@code float}, which loses precision beyond roughly a few
 * million units. In a Space Engine-style procedural universe a body can sit billions of units
 * from the origin, so representing absolute positions with {@code float} causes severe
 * jitter/wobble when the camera moves.</p>
 *
 * <p>The standard fix is a <em>floating origin</em>: the camera is conceptually kept at the
 * render origin and every object is drawn offset <em>relative</em> to the camera. The relative
 * offset is computed in {@code double} precision, then narrowed to {@code float} only for the
 * GPU. This keeps all per-draw matrices small and precise regardless of how far you travel.</p>
 *
 * <p>This class stores the authoritative camera world position as {@code double} and exposes
 * helpers to move geometry into camera-relative space.</p>
 */
public final class CoordinateSystem {

    private final double[] origin = {0.0, 0.0, 0.0};

    public CoordinateSystem() {
    }

    /** Reset the origin to {@code (0,0,0)}. */
    public void reset() {
        origin[0] = 0.0;
        origin[1] = 0.0;
        origin[2] = 0.0;
    }

    /** Set the absolute world-space camera position (double precision). */
    public void setOrigin(double x, double y, double z) {
        origin[0] = x;
        origin[1] = y;
        origin[2] = z;
    }

    /** Move the origin by a double-valued offset (e.g. from a camera step). */
    public void translate(double dx, double dy, double dz) {
        origin[0] += dx;
        origin[1] += dy;
        origin[2] += dz;
    }

    public double getOriginX() {
        return origin[0];
    }

    public double getOriginY() {
        return origin[1];
    }

    public double getOriginZ() {
        return origin[2];
    }

    /**
     * Convert an absolute world position to a camera-relative float position, computed in
     * double precision to avoid precision loss.
     *
     * @param worldX absolute world X
     * @param worldY absolute world Y
     * @param worldZ absolute world Z
     * @return camera-relative position as floats
     */
    public Vector3f toCameraRelative(float worldX, float worldY, float worldZ) {
        return new Vector3f(
                (float) (worldX - origin[0]),
                (float) (worldY - origin[1]),
                (float) (worldZ - origin[2]));
    }

    /**
     * Convert an absolute world position given as {@code double} to a camera-relative float
     * position. Prefer this overload for far-away bodies where the absolute coordinate exceeds
     * float precision.
     */
    public Vector3f toCameraRelative(double worldX, double worldY, double worldZ) {
        return new Vector3f(
                (float) (worldX - origin[0]),
                (float) (worldY - origin[1]),
                (float) (worldZ - origin[2]));
    }

    /**
     * Whether the origin has moved far enough from {@code (0,0,0)} that it is worth re-centering
     * (used to periodically re-baseline object caches). Threshold is one render unit.
     */
    public boolean needsRebaseline() {
        return Math.abs(origin[0]) > 1.0
                || Math.abs(origin[1]) > 1.0
                || Math.abs(origin[2]) > 1.0;
    }
}
