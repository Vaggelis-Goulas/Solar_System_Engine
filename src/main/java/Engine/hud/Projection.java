package Engine.hud;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Helpers to project world-space points into screen pixels using the engine's camera-relative
 * floating-origin pipeline (rotation-only view matrix, camera at origin).
 */
public final class Projection {

    private Projection() {}

    /** Result of projecting a world point: screen position and whether it's in front. */
    public static final class Projected {
        public boolean inFront;
        public float screenX;
        public float screenY;
        public float depth; // positive distance (world units) from camera, for label scaling
    }

    /**
     * Project an absolute world position to screen pixels.
     *
     * @param worldX,worldY,worldZ absolute world position (double, authoritative)
     * @param cameraPos camera world position
     * @param view rotation-only camera-relative view matrix
     * @param proj projection matrix
     * @param screenW,screenH window size (pixels)
     * @return projected result
     */
    public static Projected project(double worldX, double worldY, double worldZ,
                                    Vector3d cameraPos,
                                    Matrix4f view, Matrix4f proj,
                                    float screenW, float screenH) {
        Projected p = new Projected();

        // Camera-relative position (double subtract keeps precision).
        double rx = worldX - cameraPos.x;
        double ry = worldY - cameraPos.y;
        double rz = worldZ - cameraPos.z;

        p.depth = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);

        // Transform through view (rotation only) then projection.
        Vector4f v = new Vector4f((float) rx, (float) ry, (float) rz, 1.0f);
        view.transform(v);
        proj.transform(v);

        if (v.w <= 0.0001f) {
            p.inFront = false;
            return p;
        }

        float nx = v.x / v.w;
        float ny = v.y / v.w;

        p.inFront = true;
        p.screenX = (nx * 0.5f + 0.5f) * screenW;
        p.screenY = (1.0f - (ny * 0.5f + 0.5f)) * screenH;
        return p;
    }
}
