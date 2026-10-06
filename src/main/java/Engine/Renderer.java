package Engine;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class Renderer {

    private final Matrix4f projectionMatrix;
    private float fov;
    private float width;
    private float height;
    private final float near;
    private final float far;

    public Renderer(float fov, float width, float height, float near, float far) {
        this.fov = fov;
        this.width = width;
        this.height = height;
        this.near = near;
        this.far = far;
        this.projectionMatrix = new Matrix4f().perspective(
                (float) Math.toRadians(fov),
                width / height,
                near,
                far
        );
    }

    public void clear() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void init() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        // Deep-space blue instead of pure black - subtle cool tint so the sky never reads
        // as "screen-off". Kept very low to retain the Space Engine darkness.
        GL11.glClearColor(0.004f, 0.010f, 0.022f, 1f);
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }

    public void setFov(float fov) {
        this.fov = fov;
        updateProjectionMatrix();
    }

    public float getFov() {
        return fov;
    }

    public void setViewportSize(float width, float height) {
        this.width = width;
        this.height = height;
        updateProjectionMatrix();
    }

    private void updateProjectionMatrix() {
        projectionMatrix.identity().perspective(
                (float) Math.toRadians(fov),
                width / height,
                near,
                far
        );
    }
}