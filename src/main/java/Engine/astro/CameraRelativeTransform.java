package Engine.astro;

import org.joml.Matrix4f;

/**
 * Builds model matrices with the camera-relative floating-origin math applied.
 *
 * <p>Standard practice in the existing engine builds a model matrix using absolute coordinates,
 * then feeds it through the (also absolute) view matrix. At extreme distances the absolute float
 * values overflow precision, causing visible jitter (see {@link CoordinateSystem}).</p>
 *
 * <p>These helpers instead compute the translation component as {@code worldPosition - cameraPosition}
 * in {@code double} precision and then write a small, precise {@link Matrix4f} whose translation
 * is already camera-relative. The pipeline then treats the camera as being at the origin.</p>
 */
public final class CameraRelativeTransform {

    private CameraRelativeTransform() {}

    /**
     * Build an axis-aligned, camera-relative model matrix.
     *
     * @param worldX planet centre X in absolute world units (double precision)
     * @param worldY planet centre Y in absolute world units
     * @param worldZ planet centre Z in absolute world units
     * @param camX   camera X (double)
     * @param camY   camera Y
     * @param camZ   camera Z
     * @param scale  uniform scale to apply
     * @return camera-relative model matrix (translation in float, computed from double subtraction)
     */
    public static Matrix4f translation(double worldX, double worldY, double worldZ,
                                       double camX, double camY, double camZ,
                                       float scale) {
        return new Matrix4f().translation(
                (float) (worldX - camX),
                (float) (worldY - camY),
                (float) (worldZ - camZ)).scale(scale);
    }

    /**
     * Build a camera-relative model matrix with a rotation applied before scaling.
     */
    public static Matrix4f translationRotated(double worldX, double worldY, double worldZ,
                                              double camX, double camY, double camZ,
                                              float rotDegX, float rotDegY, float rotDegZ,
                                              float scale) {
        Matrix4f m = new Matrix4f();
        m.translation(
                (float) (worldX - camX),
                (float) (worldY - camY),
                (float) (worldZ - camZ));
        m.rotateXYZ((float) Math.toRadians(rotDegX), (float) Math.toRadians(rotDegY), (float) Math.toRadians(rotDegZ));
        m.scale(scale);
        return m;
    }
}