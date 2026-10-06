package Engine.hud;

import Engine.ShaderProgram;
import Engine.camera.Camera;
import Engine.utils.Time;
import Solar.Planet;
import Solar.SolarSystem;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen "Augmented Reality" HUD overlay for the solar system.
 *
 * <p>Everything is drawn in screen space (pixels, top-left origin) using an AR overlay shader.
 * The key AR feature is projecting every celestial body from world space onto the viewport:
 * bodies in front of the camera get an inline target marker + label, while off-screen bodies
 * get a dimmed edge marker with the bearing so the pilot always knows where to look.</p>
 *
 * <p>Rendering is done in a single layered pass over the 3D scene (after the scene, before the
 * swap) with depth test disabled and blending enabled.</p>
 */
public class ARHUD {

    // AR visor color scheme (cyan/teal).
    public static final Vector4f HUD_CYAN   = new Vector4f(0.20f, 0.95f, 1.00f, 0.95f);
    public static final Vector4f HUD_GREEN  = new Vector4f(0.40f, 1.00f, 0.55f, 0.95f);
    public static final Vector4f HUD_AMBER  = new Vector4f(1.00f, 0.72f, 0.25f, 1.00f);
    public static final Vector4f HUD_RED    = new Vector4f(1.00f, 0.35f, 0.35f, 1.00f);
    public static final Vector4f HUD_DIM    = new Vector4f(0.20f, 0.55f, 0.60f, 0.55f);
    public static final Vector4f HUD_WHITE  = new Vector4f(0.95f, 0.98f, 1.00f, 0.95f);

    private final ShaderProgram shader;
    private final TextRenderer text;
    private final ShapeRenderer shape;
    private final NavigationMenu navMenu;

    private SolarSystem solarSystem;
    private final Time time = Time.getInstance();

    // Identifiers for the occasional per-frame garbage-free color objects.
    private final Vector4f _c = new Vector4f();

    // Selection state.
    private int targetIndex = -1; // index into solarSystem.getPlanets() list, -1 = none
    private boolean targetLockedByReticle = false;

    private float fov;
    private float movementSpeed;
    private Vector3f camPosition = new Vector3f();

    // Cached projected bodies for the current frame.
    private final List<BodyView> bodyViews = new ArrayList<>();

    // Live cursor position (screen pixels) for the navigation menu hover feedback.
    private final Vector2f _mouse = new Vector2f();

    /** Feed the current cursor position in screen pixels (used by the nav menu hover). */
    public void setMousePosition(float x, float y) {
        _mouse.set(x, y);
    }

    private static final class BodyView {
        Planet planet;
        Projection.Projected proj = new Projection.Projected();
        String name;
    }

    public ARHUD(ShaderProgram arShader, TextRenderer textRenderer, ShapeRenderer shapeRenderer) {
        this.shader = arShader;
        this.text = textRenderer;
        this.shape = shapeRenderer;
        this.navMenu = new NavigationMenu(textRenderer, shapeRenderer);
    }

    public void setSolarSystem(SolarSystem solarSystem) {
        this.solarSystem = solarSystem;
        this.navMenu.setSolarSystem(solarSystem);
    }

    public NavigationMenu getNavigationMenu() {
        return navMenu;
    }

    // --- External state feed -------------------------------------------------

    public void setFov(float fov) {
        this.fov = fov;
    }

    public void setMovementSpeed(float speed) {
        this.movementSpeed = speed;
    }

    // --- Selection controls --------------------------------------------------

    /** Select the n-th body (0-based into the planet list). Returns the planet or null. */
    public Planet selectIndex(int index) {
        if (solarSystem == null) return null;
        List<Planet> planets = solarSystem.getPlanets();
        if (index < 0 || index >= planets.size()) {
            targetIndex = -1;
            targetLockedByReticle = false;
            return null;
        }
        targetIndex = index;
        targetLockedByReticle = false;
        return planets.get(index);
    }

    /** Cycle to the next numbered body (used by T and mouse). */
    public void cycleTarget(int direction) {
        if (solarSystem == null) return;
        int n = solarSystem.getPlanets().size();
        if (targetIndex < 0) targetIndex = 0;
        targetIndex = (targetIndex + direction + n) % n;
        targetLockedByReticle = false;
    }

    public void clearTarget() {
        targetIndex = -1;
        targetLockedByReticle = false;
    }

    public boolean hasTarget() {
        return targetIndex >= 0;
    }

    /** Select the body closest to the reticle (the AR "look-at" selection). */
    public void selectNearestToReticle(float screenW, float screenH) {
        if (solarSystem == null) return;
        float cx = screenW * 0.5f;
        float cy = screenH * 0.5f;
        float best = Float.MAX_VALUE;
        int bestIdx = -1;
        List<Planet> planets = solarSystem.getPlanets();
        for (int i = 0; i < planets.size(); i++) {
            BodyView bv = bodyViews.get(i);
            if (!bv.proj.inFront) continue;
            float d = (bv.proj.screenX - cx) * (bv.proj.screenX - cx)
                    + (bv.proj.screenY - cy) * (bv.proj.screenY - cy);
            if (d < best) {
                best = d;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            targetIndex = bestIdx;
            targetLockedByReticle = true;
        }
    }

    // --- Rendering -----------------------------------------------------------

    /**
     * Main render entry. Call after the scene is drawn and before the buffer swap.
     */
    public void render(Camera camera, SolarSystem solarSystem,
                       Matrix4f view, Matrix4f proj, Vector3d cameraPosD,
                       float screenW, float screenH) {
        if (solarSystem == null) return;

        this.camPosition.set(camera.getPosition());
        this.viewMatrix.set(view);
        this.projMatrix.set(proj);

        // Build body views.
        bodyViews.clear();
        List<Planet> planets = solarSystem.getPlanets();
        for (Planet p : planets) {
            Vector3d w = p.getWorldPositionD();
            BodyView bv = new BodyView();
            bv.planet = p;
            bv.name = p.getName();
            bv.proj = Projection.project(w.x, w.y, w.z, cameraPosD, view, proj, screenW, screenH);
            bodyViews.add(bv);
        }

        text.setScreenSize(screenW, screenH);
        shape.setScreenSize(screenW, screenH);

        glSetup();

        if (navMenu.isOpen()) {
            // Modal: only the navigation overlay is drawn; the sim chrome is hidden.
            // The nav menu manages its own shape batch internally.
            navMenu.render(shader, screenW, screenH, _mouse);
            navMenu.renderQuads(shader);
            glTeardown();
            return;
        }

        // Layer 1: reticle + static chrome.
        shape.begin();
        drawReticle(screenW, screenH);
        drawCornerBrackets(screenW, screenH);
        drawRadar(screenW, screenH);
        shape.render(shader);

        // Layer 2: projected body markers + labels.
        shape.begin();
        drawBodyIndicators(screenW, screenH);
        // Target lock brackets (drawn in line layer for crispness).
        drawTargetLock(screenW, screenH);
        shape.render(shader);

        // Layer 3: text (status bar, labels, panels, readouts).
        drawStatusBar(screenW, screenH);
        drawBodyLabels(screenW, screenH);
        drawTargetPanel(screenW, screenH);
        drawBottomReadout(screenW, screenH);
        drawControlsBar(screenW, screenH);

        glTeardown();
    }

    private void glSetup() {
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        shader.use();
    }

    private void glTeardown() {
        shader.unbind();
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
    }

    // --- Reticle & chrome ----------------------------------------------------

    private void drawReticle(float w, float h) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        shape.reticle(cx, cy, 8f, 14f, HUD_CYAN);
        // small center dot
        shape.circle(cx, cy, 1.2f, 12, HUD_CYAN);
    }

    private void drawCornerBrackets(float w, float h) {
        float m = 18f;
        float s = 26f;
        // Four corner L-brackets framing the play area.
        shape.line(m, m, m + s, m, HUD_DIM);
        shape.line(m, m, m, m + s, HUD_DIM);

        shape.line(w - m - s, m, w - m, m, HUD_DIM);
        shape.line(w - m, m, w - m, m + s, HUD_DIM);

        shape.line(m, h - m, m + s, h - m, HUD_DIM);
        shape.line(m, h - m, m, h - m - s, HUD_DIM);

        shape.line(w - m - s, h - m, w - m, h - m, HUD_DIM);
        shape.line(w - m, h - m - s, w - m, h - m, HUD_DIM);
    }

    // --- Body indicators ------------------------------------------------------

    private void drawBodyIndicators(float w, float h) {
        float margin = 26f;
        for (int i = 0; i < bodyViews.size(); i++) {
            BodyView bv = bodyViews.get(i);
            if (bv.proj.inFront) {
                // Inline marker: small diamond at projected position.
                float r = markerRadius(bv.planet);
                shape.line(bv.proj.screenX - r, bv.proj.screenY, bv.proj.screenX, bv.proj.screenY - r, HUD_CYAN);
                shape.line(bv.proj.screenX, bv.proj.screenY - r, bv.proj.screenX + r, bv.proj.screenY, HUD_CYAN);
                shape.line(bv.proj.screenX + r, bv.proj.screenY, bv.proj.screenX, bv.proj.screenY + r, HUD_CYAN);
                shape.line(bv.proj.screenX, bv.proj.screenY + r, bv.proj.screenX - r, bv.proj.screenY, HUD_CYAN);
            } else {
                // Off-screen: draw an edge arrow pointing toward the body.
                drawEdgeMarker(bv, w, h, margin);
            }
        }
    }

    private float markerRadius(Planet p) {
        if (p.getName().equals("Sun")) return 7f;
        return isTargetLocked(p) ? 6f : 4f;
    }

    private boolean isTargetLocked(Planet p) {
        return targetIndex >= 0 && targetIndex < bodyViews.size()
                && bodyViews.get(targetIndex).planet == p;
    }

    private void drawEdgeMarker(BodyView bv, float w, float h, float margin) {
        // Use a large fake depth so it's "behind", then clamp to edge using the NDC sign.
        // We derive bearing from the camera-relative position directly.
        Vector3d world = bv.planet.getWorldPositionD();
        Vector3d rv = new Vector3d(world.x - camPosition.x, world.y - camPosition.y, world.z - camPosition.z);

        // Convert relative dir to NDC by projecting an extremely far point along the direction.
        double len = Math.sqrt(rv.x * rv.x + rv.y * rv.y + rv.z * rv.z);
        if (len < 1e-3) return;
        double ux = rv.x / len, uy = rv.y / len, uz = rv.z / len;

        // Rebuild the projection with a far point (uses the engine matrices via the stored view/proj
        // would need them; we approximate using the already-computed projected point if behind,
        // else fall back to a directional projection). Simpler: use Projection insanely far point.
        // We keep a cached far-direction projection done in render() — but for simplicity redo here
        // with default matrices passed via a stored copy.
        Projection.Projected far = Projection.project(
                camPosition.x + ux * 1e8, camPosition.y + uy * 1e8, camPosition.z + uz * 1e8,
                toVec3d(camPosition), viewMatrix, projMatrix, w, h);

        float ex, ey;
        if (far.inFront) {
            float sx = clamp(far.screenX, margin, w - margin);
            float sy = clamp(far.screenY, margin, h - margin);
            ex = sx;
            ey = sy;
        } else {
            // Behind camera: point back toward center-ish edge (fallback).
            ex = w * 0.5f;
            ey = margin;
        }

        // Store for label drawing.
        bv.proj.screenX = ex;
        bv.proj.screenY = ey;
        bv.proj.inFront = false;

        // Draw a dim arrow/triangle at the edge.
        float s = 5f;
        shape.line(ex, ey - s, ex + s, ey + s, HUD_DIM);
        shape.line(ex + s, ey + s, ex - s, ey + s, HUD_DIM);
        shape.line(ex - s, ey + s, ex, ey - s, HUD_DIM);
    }

    private Vector3d toVec3d(Vector3f v) {
        return new Vector3d(v.x, v.y, v.z);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // --- The engine matrices are needed for edge projection; stored here per frame ----------
    private Matrix4f viewMatrix = new Matrix4f();
    private Matrix4f projMatrix = new Matrix4f();

    // --- Labels ----------------------------------------------------------------

    private void drawBodyLabels(float w, float h) {
        float baseHeight = Math.min(w, h) * 0.017f;
        for (int i = 0; i < bodyViews.size(); i++) {
            BodyView bv = bodyViews.get(i);
            boolean targeted = (i == targetIndex);
            Vector4f col = targeted ? HUD_GREEN : HUD_CYAN;

            float distAU = bv.planet.getAUDistance();
            String label = bv.name.toUpperCase(Locale.ROOT);
            float hgt = targeted ? baseHeight * 1.2f : baseHeight;
            float width = text.measure(label, hgt);

            float lx = bv.proj.screenX;
            float ly = bv.proj.inFront ? (bv.proj.screenY - 16f - hgt) : (bv.proj.screenY + 10f);

            // Distance readout under the name.
            String distText = String.format(Locale.ROOT, "%.2f AU  %.0f u",
                    distAU, bv.planet.getScaledRadius());

            // Keep labels on-screen.
            lx = clamp(lx - width * 0.5f, 6f, w - width - 6f);

            text.drawText(shader, label, lx, ly + hgt * 0.6f, hgt, col, 0f);
            if (bv.proj.inFront) {
                text.drawText(shader, distText,
                        clamp(bv.proj.screenX - text.measure(distText, hgt * 0.55f) * 0.5f, 6f, w - 120f),
                        ly + hgt * 1.4f, hgt * 0.55f, HUD_DIM, 0f);
            }
        }
    }

    private void drawTargetLock(float w, float h) {
        if (targetIndex < 0 || targetIndex >= bodyViews.size()) return;
        BodyView bv = bodyViews.get(targetIndex);
        if (!bv.proj.inFront) return;
        float size = 16f;
        float px = bv.proj.screenX;
        float py = bv.proj.screenY;
        // four corner brackets around the target (like a lock-on).
        shape.bracket(px, py, size, HUD_GREEN);
    }

    // --- Status bar (top) ------------------------------------------------------

    private void drawStatusBar(float w, float h) {
        float barH = h * 0.055f;
        float pad = 16f;
        float x = pad;
        float baseline = pad + barH * 0.6f;

        // Title block
        text.drawText(shader, "SOLAR // AR", x, baseline, barH * 0.7f, HUD_CYAN, 0f);
        float titleW = text.measure("SOLAR // AR", barH * 0.7f) + 18f;
        x += titleW;

        // Separator
        text.drawText(shader, "|", x, baseline, barH * 0.7f, HUD_DIM, 0f);
        x += 16f;

        // Sim time / scale
        String tClock = String.format(Locale.ROOT, "T+%06.1fs", time.getTime());
        text.drawText(shader, tClock, x, baseline, barH * 0.7f, HUD_GREEN, 0f);
        x += text.measure(tClock, barH * 0.7f) + 14f;

        String ts = String.format(Locale.ROOT, "SPD x%.1f", time.getTimeScale());
        text.drawText(shader, ts, x, baseline, barH * 0.7f, HUD_AMBER, 0f);
        x += text.measure(ts, barH * 0.7f) + 14f;

        // FPS right-aligned
        String fps = String.format(Locale.ROOT, "%.0f FPS", time.getFPS());
        float fpsW = text.measure(fps, barH * 0.7f);
        text.drawText(shader, fps, w - pad - fpsW, baseline, barH * 0.7f, HUD_WHITE, 0f);

        // Little underline
        shape.begin();
        shape.line(pad, barH + 6f, w - pad, barH + 6f, HUD_DIM);
        shape.render(shader);
    }

    // --- Radar minimap ---------------------------------------------------------

    private void drawRadar(float w, float h) {
        float size = Math.min(w, h) * 0.16f;
        float cx = 46f + size * 0.5f;
        float cy = h - 46f - size * 0.5f;

        // Ring
        _c.set(HUD_DIM);
        shape.circle(cx, cy, size * 0.5f, 64, _c);
        shape.circle(cx, cy, size * 0.25f, 48, _c);
        // cross hair
        shape.line(cx - size * 0.5f, cy, cx + size * 0.5f, cy, _c);
        shape.line(cx, cy - size * 0.5f, cx, cy + size * 0.5f, _c);

        // Range: use the outermost planet distance to autoscale.
        float maxAU = 1f;
        List<Planet> planets = solarSystem.getPlanets();
        for (BodyView bv : bodyViews) {
            if (bv.planet.getAUDistance() > maxAU) maxAU = bv.planet.getAUDistance();
        }
        maxAU *= 1.15f;

        float half = size * 0.45f;
        // Center = Sun at origin.
        for (int i = 0; i < bodyViews.size(); i++) {
            BodyView bv = bodyViews.get(i);
            float au = bv.planet.getAUDistance();
            if (au <= 0) continue; // skip sun center dot drawn separately
            Vector3d world = bv.planet.getWorldPositionD();
            // x,z offset relative to sun (sun at origin), scaled to AU.
            float rx = (float) world.x / ConstantsAU();
            float rz = (float) world.z / ConstantsAU();
            float sx = cx + (rx / maxAU) * half;
            float sy = cy - (rz / maxAU) * half;
            _c.set(targetIndex == i ? HUD_GREEN : HUD_CYAN);
            shape.circle(sx, sy, targetIndex == i ? 3.5f : 2.5f, 12, _c);
        }
        // Sun center marker.
        _c.set(HUD_AMBER);
        shape.circle(cx, cy, 4f, 16, _c);
    }

    private float ConstantsAU() {
        try {
            return Solar.Constants.AU;
        } catch (Throwable t) {
            return 500f;
        }
    }

    // --- Target panel ----------------------------------------------------------

    private void drawTargetPanel(float w, float h) {
        if (targetIndex < 0 || targetIndex >= bodyViews.size()) return;
        Planet t = bodyViews.get(targetIndex).planet;

        float pw = Math.min(w, h) * 0.24f;
        float ph = Math.min(w, h) * 0.20f;
        float x = w - pw - 22f;
        float y = 22f + h * 0.055f; // below status bar

        // Translucent framing (drawn as lines in the line layer for crispness).
        shape.begin();
        shape.rect(x, y, pw, ph, HUD_GREEN);
        shape.line(x, y, x + pw * 0.12f, y, HUD_GREEN);
        shape.line(x + pw - pw * 0.12f, y, x + pw, y, HUD_GREEN);
        shape.line(x, y + ph, x + pw * 0.12f, y + ph, HUD_GREEN);
        shape.line(x + pw - pw * 0.12f, y + ph, x + pw, y + ph, HUD_GREEN);
        shape.render(shader);

        // Panel contents.
        float bx = x + 14f;
        float lineH = ph * 0.16f;
        float baseline = y + 10f + lineH * 0.7f;

        text.drawText(shader, "TARGET", bx, baseline, lineH * 0.6f, HUD_DIM, 0f);
        baseline += lineH;

        text.drawText(shader, t.getName().toUpperCase(Locale.ROOT), bx, baseline, lineH * 0.95f, HUD_GREEN, 0f);
        baseline += lineH * 1.15f;

        String dAU = String.format(Locale.ROOT, "DIST  %.2f AU", t.getAUDistance());
        text.drawText(shader, dAU, bx, baseline, lineH * 0.55f, HUD_CYAN, 0f);
        baseline += lineH * 0.85f;

        String rad = String.format(Locale.ROOT, "RAD   %.2f u", t.getScaledRadius());
        text.drawText(shader, rad, bx, baseline, lineH * 0.55f, HUD_CYAN, 0f);
        baseline += lineH * 0.85f;

        String per = String.format(Locale.ROOT, "ORBIT %.2f y", t.getOrbitalPeriodYears());
        text.drawText(shader, per, bx, baseline, lineH * 0.55f, HUD_CYAN, 0f);
        baseline += lineH * 0.85f;

        String both = "TRACK  LOCKED";
        text.drawText(shader, both, bx, baseline, lineH * 0.55f, HUD_AMBER, 0f);
    }

    // --- Bottom readout --------------------------------------------------------

    private void drawBottomReadout(float w, float h) {
        float barH = h * 0.05f;
        float baseline = yBottom(h) - barH * 1.2f;
        float pad = 16f;

        // Camera position + speed + FOV.
        String pos = String.format(Locale.ROOT, "POS [%6.1f,%6.1f,%6.1f]", camPosition.x, camPosition.y, camPosition.z);
        String spd = String.format(Locale.ROOT, "VEL %.1f u/s", movementSpeed);
        String fovS = String.format(Locale.ROOT, "FOV %.0f\u00B0", fov);

        float hgt = barH * 0.7f;
        float x = pad;
        text.drawText(shader, pos, x, baseline, hgt, HUD_CYAN, 0f);
        x += text.measure(pos, hgt) + 20f;
        text.drawText(shader, spd, x, baseline, hgt, HUD_GREEN, 0f);
        x += text.measure(spd, hgt) + 20f;
        text.drawText(shader, fovS, x, baseline, hgt, HUD_AMBER, 0f);

        // Right side: target hint / system status.
        String sys = hasTarget()
                ? bodyViews.get(targetIndex).name.toUpperCase(Locale.ROOT) + " <TARGET>"
                : "NO TARGET";
        Vector4f sysCol = hasTarget() ? HUD_GREEN : HUD_DIM;
        float sysW = text.measure(sys, hgt);
        text.drawText(shader, sys, w - pad - sysW, baseline, hgt, sysCol, 0f);
    }

    private float yBottom(float h) {
        return h - 16f;
    }

    private void drawControlsBar(float w, float h) {
        String ctrl = "WASD MOVE  RMB LOOK  SCROLL ZOOM  SHIFT+SCR SPEED  N NAVIGATE  +/- TIME";
        float hgt = Math.min(w, h) * 0.014f;
        float y = yBottom(h) - 4f;
        // Center near bottom.
        float cw = text.measure(ctrl, hgt);
        text.drawText(shader, ctrl, w * 0.5f, y, hgt, HUD_DIM, 0.5f);
    }

    public Planet getTargetPlanet() {
        if (targetIndex < 0 || solarSystem == null) return null;
        List<Planet> planets = solarSystem.getPlanets();
        if (targetIndex >= planets.size()) return null;
        return planets.get(targetIndex);
    }

    public void cleanup() {
        navMenu.cleanup();
        text.cleanup();
        shape.cleanup();
    }
}
