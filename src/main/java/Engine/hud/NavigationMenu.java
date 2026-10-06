package Engine.hud;

import Engine.ShaderProgram;
import Solar.Planet;
import Solar.SolarSystem;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Locale;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * Full-screen navigation overlay opened with the N key. Shows every celestial body as a
 * clickable row; clicking one selects it as the HUD target and closes the menu.
 *
 * <p>Unlike the always-on AR chrome this is a discrete modal panel rendered on top of the
 * scene with a translucent backdrop. It needs its own small buffer for filled quads (the
 * {@link ShapeRenderer} is wireframe-only).</p>
 */
public class NavigationMenu {

    public static final Vector4f BG        = new Vector4f(0.02f, 0.05f, 0.08f, 0.82f);
    public static final Vector4f PANEL_BG  = new Vector4f(0.04f, 0.10f, 0.14f, 0.94f);
    public static final Vector4f BORDER    = new Vector4f(0.20f, 0.95f, 1.00f, 0.90f);
    public static final Vector4f ROW       = new Vector4f(0.08f, 0.20f, 0.28f, 0.60f);
    public static final Vector4f ROW_HOVER = new Vector4f(0.12f, 0.40f, 0.50f, 0.85f);
    public static final Vector4f TXT       = new Vector4f(0.85f, 0.92f, 0.95f, 0.95f);
    public static final Vector4f TXT_DIM   = new Vector4f(0.45f, 0.60f, 0.65f, 0.90f);
    public static final Vector4f TXT_HOT   = new Vector4f(0.20f, 1.00f, 1.00f, 1.00f);
    public static final Vector4f HEADER    = new Vector4f(0.20f, 0.95f, 1.00f, 1.00f);

    private static final int MAX_QUAD_VERTS = 512 * 6;

    private final TextRenderer text;
    private final ShapeRenderer shape;
    private SolarSystem solarSystem;

    private boolean open = false;

    // Filled-quad rendering (the backdrop + row highlights use GL_TRIANGLES).
    private final FloatBuffer quadData;
    private int vaoId, vboId;

    private float screenW = 1920f;
    private float screenH = 1080f;

    // Layout geometry rebuilt each frame while open.
    private float panelX, panelY, panelW, panelH;
    private float rowH;
    private int hoverRow = -1;

    public NavigationMenu(TextRenderer textRenderer, ShapeRenderer shapeRenderer) {
        this.text = textRenderer;
        this.shape = shapeRenderer;

        this.quadData = BufferUtils.createFloatBuffer(MAX_QUAD_VERTS * 8);
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);
        vboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, (long) MAX_QUAD_VERTS * 8 * Float.BYTES, GL30.GL_DYNAMIC_DRAW);

        int stride = 8 * Float.BYTES;
        GL30.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0);
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, stride, 2 * Float.BYTES);
        GL30.glEnableVertexAttribArray(1);
        GL30.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 6 * Float.BYTES);
        GL30.glEnableVertexAttribArray(2);
        GL30.glBindVertexArray(0);
    }

    public void setSolarSystem(SolarSystem solarSystem) {
        this.solarSystem = solarSystem;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        this.hoverRow = -1;
    }

    public void toggle() {
        setOpen(!open);
    }

    /**
     * Handle a left-button press at screen (mx,my). If it hits a row, select it and close.
     *
     * @return the index (into {@code SolarSystem.getPlanets()}) of the selected body, or -1
     */
    public int handleClick(float mx, float my) {
        if (!open) return -1;
        int row = rowAt(mx, my);
        if (row < 0) return -1;
        if (solarSystem == null) return -1;
        List<Planet> planets = solarSystem.getPlanets();
        if (row >= planets.size()) return -1;
        open = false;
        hoverRow = -1;
        return row;
    }

    /** Current hovered planet index (for the cursor highlight), or -1. */
    public int getHoverRow() {
        return hoverRow;
    }

    // --- Geometry ----------------------------------------------------------

    private int rowAt(float mx, float my) {
        if (!open) return -1;
        if (mx < panelX || mx > panelX + panelW) return -1;
        float relY = my - panelY;
        if (relY < 0) return -1;
        int row = (int) (relY / rowH);
        if (row < 0 || row >= solarSystem.getPlanets().size()) return -1;
        return row;
    }

    // --- Rendering ---------------------------------------------------------

    /**
     * Recompute layout and draw the overlay. The caller must have the AR shader bound and
     * blending + depth settings configured for HUD rendering beforehand.
     */
    public void render(ShaderProgram shader, float screenW, float screenH, Vector2f mouse) {
        if (!open || solarSystem == null) return;
        this.screenW = screenW;
        this.screenH = screenH;

        // Self-contained shape batch: never leaves the shared ShapeRenderer buffer dirty.
        shape.begin();

        List<Planet> planets = solarSystem.getPlanets();
        int n = planets.size();

        float titleH = Math.min(screenW, screenH) * 0.05f;
        float pad = Math.min(screenW, screenH) * 0.03f;
        float rowPad = Math.min(screenW, screenH) * 0.012f;

        // Measure the widest name to size the panel.
        float nameH = Math.min(screenW, screenH) * 0.024f;
        float maxNameW = 0f;
        for (Planet p : planets) {
            maxNameW = Math.max(maxNameW, text.measure(p.getName().toUpperCase(Locale.ROOT), nameH));
        }

        rowH = nameH + rowPad * 2f;
        panelW = maxNameW + Math.min(screenW, screenH) * 0.34f;
        panelH = titleH + pad * 0.5f + n * rowH + pad;

        panelX = (screenW - panelW) * 0.5f;
        panelY = (screenH - panelH) * 0.5f;

        // 1) Fills are accumulated in the quad buffer (backdrop + panel + rows) and flushed
        //    immediately, so text below is always drawn on top.
        drawQuad(0, 0, screenW, screenH, BG);
        drawQuad(panelX, panelY, panelW, panelH, PANEL_BG);
        renderQuads(shader);

        // Recompute hover row given current mouse.
        hoverRow = rowAt(mouse.x, mouse.y);

        // Title.
        text.drawText(shader, "NAVIGATION", panelX + panelW * 0.5f,
                panelY + pad * 0.5f + titleH * 0.7f, titleH, HEADER, 0.5f);

        // Rows: highlight quads first, then labels on top.
        for (int i = 0; i < n; i++) {
            Planet p = planets.get(i);
            boolean hot = (i == hoverRow);
            Vector4f rowCol = hot ? ROW_HOVER : ROW;
            drawQuad(panelX + 4f, i * rowH + panelY + pad * 0.5f + titleH, panelW - 8f, rowH - 4f, rowCol);
        }
        renderQuads(shader);

        float rowY = panelY + pad * 0.5f + titleH;
        for (int i = 0; i < n; i++) {
            Planet p = planets.get(i);
            boolean hot = (i == hoverRow);
            if (hot) {
                shape.line(panelX + 4f, rowY, panelX + 4f, rowY + rowH - 4f, BORDER);
            }

            String name = p.getName().toUpperCase(Locale.ROOT);
            Vector4f nameCol = hot ? TXT_HOT : TXT;
            text.drawText(shader, name, panelX + pad, rowY + rowH * 0.5f + nameH * 0.4f,
                    nameH, nameCol, 0f);

            float infoX = panelX + panelW - pad;
            String dist = String.format(Locale.ROOT, "%.2f AU", p.getAUDistance());
            String per = String.format(Locale.ROOT, "%.2f y", p.getOrbitalPeriodYears());
            float infoH = nameH * 0.8f;
            text.drawText(shader, per, infoX - 80f, rowY + rowH * 0.5f + infoH * 0.4f,
                    infoH, TXT_DIM, 1f);
            text.drawText(shader, dist, infoX, rowY + rowH * 0.5f + infoH * 0.4f,
                    infoH, hot ? HEADER : TXT_DIM, 1f);

            rowY += rowH;
        }

        // Bottom hint.
        float hintH = Math.min(screenW, screenH) * 0.016f;
        text.drawText(shader, "CLICK TO SELECT  |  N / ESC TO CLOSE", screenW * 0.5f,
                panelY + panelH + hintH * 2f, hintH, TXT_DIM, 0.5f);

        text.setScreenSize(screenW, screenH);
        shape.setScreenSize(screenW, screenH);

        // Flush the hover border line(s) on top of the text.
        shape.render(shader);
    }

    // --- Filled quads ------------------------------------------------------

    private void drawQuad(float x, float y, float w, float h, Vector4f c) {
        if (quadData.position() + 6 * 8 > quadData.capacity()) return;
        // Two triangles (CCW), same layout as the text renderer: pos2, color4, uv2.
        putV(x, y, c);
        putV(x + w, y, c);
        putV(x, y + h, c);
        putV(x + w, y, c);
        putV(x + w, y + h, c);
        putV(x, y + h, c);
    }

    private void putV(float x, float y, Vector4f c) {
        quadData.put(x).put(y);
        quadData.put(c.x).put(c.y).put(c.z).put(c.w);
        quadData.put(0).put(0);
    }

    /** Flush all accumulated filled quads with the AR shader (alpha blending on). */
    public void renderQuads(ShaderProgram shader) {
        if (quadData.position() == 0) return;
        quadData.flip();

        shader.setUniform("screenSize", new org.joml.Vector3f(screenW, screenH, 0));
        shader.setUniform("useTexture", 0);

        GL30.glBindVertexArray(vaoId);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, quadData);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, quadData.limit() / 8);
        GL30.glBindVertexArray(0);

        quadData.clear();
    }

    public void cleanup() {
        GL30.glDeleteVertexArrays(vaoId);
        GL30.glDeleteBuffers(vboId);
    }
}
