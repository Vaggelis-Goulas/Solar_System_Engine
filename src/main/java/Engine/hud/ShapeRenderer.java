package Engine.hud;

import Engine.ShaderProgram;
import org.joml.Vector4f;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * Immediate-mode batcher for AR vector primitives drawn in screen space (pixels, top-left
 * origin). All shapes share one VBO and a single GL_LINES draw call for performance and a tidy
 * wireframe holographic look.
 */
public class ShapeRenderer {

    public static final int MAX_VERTICES = 1 << 16;

    private final FloatBuffer vertexData;
    private int vaoId, vboId;
    private int vertexCount = 0;
    private float screenWidth = 1920f;
    private float screenHeight = 1080f;

    public ShapeRenderer() {
        vertexData = BufferUtils.createFloatBuffer(MAX_VERTICES * 8);

        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        vboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, (long) MAX_VERTICES * 8 * Float.BYTES, GL30.GL_DYNAMIC_DRAW);

        int stride = 8 * Float.BYTES;
        GL30.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0);
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, stride, 2 * Float.BYTES);
        GL30.glEnableVertexAttribArray(1);
        GL30.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 6 * Float.BYTES);
        GL30.glEnableVertexAttribArray(2);

        GL30.glBindVertexArray(0);
    }

    public void setScreenSize(float w, float h) {
        this.screenWidth = w;
        this.screenHeight = h;
    }

    private void vert(float x, float y, Vector4f c) {
        if (vertexCount >= MAX_VERTICES) return;
        vertexData.put(x).put(y);
        vertexData.put(c.x).put(c.y).put(c.z).put(c.w);
        vertexData.put(0).put(0);
        vertexCount++;
    }

    public void begin() {
        vertexData.clear();
        vertexCount = 0;
    }

    /** Draw a single line segment. */
    public void line(float x0, float y0, float x1, float y1, Vector4f c) {
        vert(x0, y0, c);
        vert(x1, y1, c);
    }

    /** Draw a rectangle outline. */
    public void rect(float x, float y, float w, float h, Vector4f c) {
        line(x, y, x + w, y, c);
        line(x + w, y, x + w, y + h, c);
        line(x + w, y + h, x, y + h, c);
        line(x, y + h, x, y, c);
    }

    /** Filled rectangle (drawn as line loop with thick vertices is not possible; approximated
     *  with a simple filled quad via GL_TRIANGLES would need a separate path, so we offer this
     *  as a translucent frame: draws both outline and diagonal cross for a glassy look). */
    public void glassRect(float x, float y, float w, float h, Vector4f c) {
        rect(x, y, w, h, c);
        // corner accent
        float a = Math.min(w, h) * 0.08f;
        line(x, y + h, x, y + h - a, c);
        line(x, y + h, x + a, y + h, c);
    }

    /** Circle outline approximated by a polygon. centerX/centerY top-left origin. */
    public void circle(float cx, float cy, float radius, int segments, Vector4f c) {
        float prevX = cx + radius;
        float prevY = cy;
        for (int i = 1; i <= segments; i++) {
            double ang = 2.0 * Math.PI * i / segments;
            float nx = (float) (cx + Math.cos(ang) * radius);
            float ny = (float) (cy + Math.sin(ang) * radius);
            line(prevX, prevY, nx, ny, c);
            prevX = nx;
            prevY = ny;
        }
    }

    /** A small "L" bracket centered at (px,py), typical AR targeting corner. */
    public void bracket(float px, float py, float size, Vector4f c) {
        float h = size * 0.5f;
        float arm = size * 0.3f;
        // top-left
        line(px - h, py - h, px - h + arm, py - h, c);
        line(px - h, py - h, px - h, py - h + arm, c);
        // top-right
        line(px + h - arm, py - h, px + h, py - h, c);
        line(px + h, py - h, px + h, py - h + arm, c);
        // bottom-left
        line(px - h, py + h, px - h + arm, py + h, c);
        line(px - h, py + h, px - h, py + h - arm, c);
        // bottom-right
        line(px + h - arm, py + h, px + h, py + h, c);
        line(px + h, py + h, px + h, py + h - arm, c);
    }

    /** Crosshair reticle at (px,py). */
    public void reticle(float px, float py, float gap, float len, Vector4f c) {
        line(px - gap - len, py, px - gap, py, c);
        line(px + gap, py, px + gap + len, py, c);
        line(px, py - gap - len, px, py - gap, c);
        line(px, py + gap, px, py + gap + len, c);
        circle(px, py, gap, 32, c);
    }

    /** Draw everything accumulated. */
    public void render(ShaderProgram shader) {
        if (vertexCount == 0) return;

        vertexData.flip();
        shader.setUniform("screenSize", new org.joml.Vector3f(screenWidth, screenHeight, 0));
        shader.setUniform("useTexture", 0);

        GL30.glBindVertexArray(vaoId);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertexData);
        GL11.glDrawArrays(GL11.GL_LINES, 0, vertexCount);
        GL30.glBindVertexArray(0);

        // Restore write state for the next batch (flip left limit shrunk to last frame's data).
        vertexData.clear();
    }

    public void cleanup() {
        GL30.glDeleteVertexArrays(vaoId);
        GL30.glDeleteBuffers(vboId);
    }
}
