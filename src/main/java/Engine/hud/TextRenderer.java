package Engine.hud;

import Engine.ShaderProgram;
import Engine.utils.FontLoader;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * Pure screen-space text renderer that draws strings with a {@link FontLoader.Font} atlas.
 *
 * <p>Vertices are expressed directly in pixel coordinates (x right, y down from top-left) and
 * written to a dynamic VBO; the AR shader converts them to NDC using a resolution uniform. Each
 * glyph quad carries its own per-vertex color so strings can be tinted/highlighted.</p>
 */
public class TextRenderer {

    private static final int MAX_GLYPHS = 4096;
    private static final int FLOATS_PER_GLYPH = 6 * 6; // 2 verts*x2 pos +x4 color +x2 uv = 8 floats/vert *6 verts

    private final FontLoader.Font font;
    private int vaoId, vboId;
    private final FloatBuffer vertexData;
    private float screenWidth = 1920f;
    private float screenHeight = 1080f;

    public TextRenderer(FontLoader.Font font) {
        this.font = font;
        this.vertexData = BufferUtils.createFloatBuffer(MAX_GLYPHS * FLOATS_PER_GLYPH);

        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        vboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, (long) MAX_GLYPHS * FLOATS_PER_GLYPH * Float.BYTES, GL30.GL_DYNAMIC_DRAW);

        // Layout: pos(2), color(4), uv(2)
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

    /**
     * Draw a single line of text.
     *
     * @param shader the AR overlay shader program
     * @param text the string to draw
     * @param x left edge in pixels (top-left origin)
     * @param baselineY the baseline in pixels (top-left origin), around ascent/descent
     * @param height desired pixel height (approx; scaled to font metrics)
     * @param color RGBA
     * @param align 0=left, 0.5=center, 1=right relative to x
     */
    public void drawText(ShaderProgram shader, String text, float x, float baselineY,
                         float height, Vector4f color, float align) {
        if (text == null || text.isEmpty()) return;

        float scale = height / font.getLineHeight();
        float lineWidth = font.measureWidth(text) * scale;
        float startX = x - lineWidth * align;

        vertexData.clear();
        float baseline = baselineY;

        float ascent = font.getAscent() * scale;
        float top = baseline - ascent;

        int quadCount = 0;
        float cursor = startX;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            FontLoader.Glyph glyph = font.getGlyph(c);
            if (glyph.advance <= 0 && glyph.width <= 0) continue;

            float gx = cursor + glyph.bearingX * scale;
            float gy = top + (ascent - glyph.bearingY * scale);
            float gw = glyph.width * scale;
            float gh = glyph.height * scale;

            if (gw > 0 && gh > 0) {
                pushQuad(vertexData, gx, gy, gw, gh, color, glyph);
                quadCount++;
            }
            cursor += glyph.advance * scale;

            if (quadCount >= MAX_GLYPHS) break;
        }

        if (quadCount == 0) return;

        vertexData.flip();

        // Set per-draw uniforms
        shader.setUniform("screenSize", new org.joml.Vector3f(screenWidth, screenHeight, 0));
        shader.setUniform("useTexture", 1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, font.getTextureId());
        shader.setUniform("texture1", 0);

        GL30.glBindVertexArray(vaoId);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertexData);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, quadCount * 6);
        GL30.glBindVertexArray(0);
    }

    /** Helper for measuring a string width at a given pixel height. */
    public float measure(String text, float height) {
        if (text == null) return 0;
        float scale = height / font.getLineHeight();
        return font.measureWidth(text) * scale;
    }

    private void pushQuad(FloatBuffer buf, float x, float y, float w, float h,
                          Vector4f color, FontLoader.Glyph glyph) {
        float r = color.x, g = color.y, b = color.z, a = color.w;
        float u0 = glyph.texX, v0 = glyph.texY, u1 = glyph.texX + glyph.texW, v1 = glyph.texY + glyph.texH;

        // Triangle 1
        putV(buf, x, y, r, g, b, a, u0, v0);
        putV(buf, x + w, y, r, g, b, a, u1, v0);
        putV(buf, x, y + h, r, g, b, a, u0, v1);
        // Triangle 2
        putV(buf, x + w, y, r, g, b, a, u1, v0);
        putV(buf, x + w, y + h, r, g, b, a, u1, v1);
        putV(buf, x, y + h, r, g, b, a, u0, v1);
    }

    private void putV(FloatBuffer buf, float x, float y, float r, float g, float b, float a,
                      float u, float v) {
        buf.put(x).put(y);
        buf.put(r).put(g).put(b).put(a);
        buf.put(u).put(v);
    }

    public void cleanup() {
        GL30.glDeleteVertexArrays(vaoId);
        GL30.glDeleteBuffers(vboId);
    }
}
