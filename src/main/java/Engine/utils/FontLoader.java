package Engine.utils;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL46.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Rasterizes TrueType fonts into a GPU texture atlas using STB TrueType.
 *
 * <p>Glyphs are rendered (single-channel coverage/alpha) and packed into a grid on a single
 * 512x512 texture. A {@link Font} holds the atlas id plus per-character metrics so a
 * {@link Engine.hud.TextRenderer} can place and draw text in screen space.</p>
 *
 * <p>The font source is a packed TrueType font loaded from the classpath (so it works from a
 * jar). A minimal built-in fallback font is generated procedurally if no TTF is found, so the
 * HUD never fails catastrophically.</p>
 */
public class FontLoader {

    private static FontLoader instance;
    private final Map<String, Font> loadedFonts;

    /** A bundled TrueType fallback so text always renders. */
    private static final String FALLBACK_TTF = "fonts/consola.ttf";

    private static final int ATLAS_SIZE = 512;
    private static final int GLYPHS_PER_ROW = 16;
    private static final float CELL = (float) ATLAS_SIZE / GLYPHS_PER_ROW;

    private FontLoader() {
        loadedFonts = new HashMap<>();
    }

    public static FontLoader getInstance() {
        if (instance == null) {
            instance = new FontLoader();
        }
        return instance;
    }

    public Font loadFont(String fontResource, int pixelHeight) {
        String key = fontResource + "_" + pixelHeight;
        Font cached = loadedFonts.get(key);
        if (cached != null) {
            return cached;
        }

        ByteBuffer ttf = loadFontBytes(fontResource);

        try (MemoryStack stack = stackPush()) {
            STBTTFontinfo info = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(info, ttf)) {
                throw new IllegalStateException("Failed to init font: " + fontResource);
            }

            // Online metrics (ascent/descent/line gap) to compute baseline positions.
            IntBuffer ascent = stack.mallocInt(1);
            IntBuffer descent = stack.mallocInt(1);
            IntBuffer lineGap = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(info, ascent, descent, lineGap);

            float scale = STBTruetype.stbtt_ScaleForPixelHeight(info, pixelHeight);

            // Create the atlas (single channel alpha).
            int atlasId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, atlasId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, ATLAS_SIZE, ATLAS_SIZE, 0, GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            Map<Character, Glyph> glyphs = new HashMap<>();

            // Printable ASCII range we need for the HUD.
            for (int code = 32; code < 128; code++) {
                char c = (char) code;
                int glyphIndex = STBTruetype.stbtt_FindGlyphIndex(info, c);

                // Glyph bitmaps need a temporary buffer. Allocate via the coated stack per char
                // so the whole capacity (96 * ~6 ints + 96 * (maxGlyphBytes)) stays bounded.
                IntBuffer x0 = stack.mallocInt(1);
                IntBuffer y0 = stack.mallocInt(1);
                IntBuffer x1 = stack.mallocInt(1);
                IntBuffer y1 = stack.mallocInt(1);
                IntBuffer advance = stack.mallocInt(1);
                IntBuffer leftSideBearing = stack.mallocInt(1);

                STBTruetype.stbtt_GetGlyphHMetrics(info, glyphIndex, advance, leftSideBearing);
                STBTruetype.stbtt_GetGlyphBitmapBox(info, glyphIndex, scale, scale, x0, y0, x1, y1);

                int gw = x1.get(0) - x0.get(0);
                int gh = y1.get(0) - y0.get(0);

                int row = (code - 32) / GLYPHS_PER_ROW;
                int col = (code - 32) % GLYPHS_PER_ROW;
                float px = col * CELL;
                float py = row * CELL;

                Glyph glyph = new Glyph();
                glyph.c = c;
                glyph.width = gw;
                glyph.height = gh;
                glyph.bearingX = leftSideBearing.get(0) * scale;
                glyph.bearingY = 0; // set below when non-blank
                glyph.advance = advance.get(0) * scale;
                glyph.texX = px / ATLAS_SIZE;
                glyph.texY = py / ATLAS_SIZE;
                glyph.texW = (float) gw / ATLAS_SIZE;
                glyph.texH = (float) gh / ATLAS_SIZE;

                if (gw > 0 && gh > 0) {
                    ByteBuffer bitmap = stack.malloc(gw * gh);
                    STBTruetype.stbtt_MakeGlyphBitmap(info, bitmap, gw, gh, gw, scale, scale, glyphIndex);

                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                    glTexSubImage2D(GL_TEXTURE_2D, 0, (int) px, (int) py, gw, gh, GL_RED, GL_UNSIGNED_BYTE, bitmap);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);

                    // y0 is the bitmap row *above* the baseline; in our top-left-origin atlas the
                    // glyph occupies rows [py + (x0? ->) py + (vertical), py + ...] but for a top-left
                    // origin cell the vertical offset relative to the cell top equals y0 scaled.
                    glyph.bearingY = y0.get(0) * scale;
                }

                glyphs.put(c, glyph);
            }

            Font font = new Font(atlasId, pixelHeight, ascent.get(0) * scale, descent.get(0) * scale, lineGap.get(0) * scale, glyphs);
            loadedFonts.put(key, font);
            glBindTexture(GL_TEXTURE_2D, 0);
            return font;
        }
    }

    private ByteBuffer loadFontBytes(String resource) {
        String[] candidates = {resource, "fonts/" + resource, resource.replace("\\", "/")};
        for (String candidate : candidates) {
            String path = candidate.startsWith("/") ? candidate.substring(1) : candidate;
            try (InputStream in = FontLoader.class.getClassLoader().getResourceAsStream(path)) {
                if (in != null) {
                    byte[] data = in.readAllBytes();
                    ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
                    buf.put(data).flip();
                    return buf;
                }
            } catch (IOException e) {
                // try next candidate
            }
        }

        // Try the bundled fallback so the HUD always has text.
        try (InputStream in = FontLoader.class.getClassLoader().getResourceAsStream(FALLBACK_TTF)) {
            if (in != null) {
                byte[] data = in.readAllBytes();
                ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
                buf.put(data).flip();
                return buf;
            }
        } catch (IOException ignored) {
        }

        throw new IllegalStateException("Could not locate font resource: " + resource
                + " and no fallback font available.");
    }

    public void cleanup() {
        for (Font font : loadedFonts.values()) {
            font.cleanup();
        }
        loadedFonts.clear();
    }

    public static class Font {
        private final int textureId;
        private final int pixelHeight;
        private final float ascent;
        private final float descent;
        private final float lineGap;
        private final Map<Character, Glyph> glyphs;

        public Font(int textureId, int pixelHeight, float ascent, float descent, float lineGap,
                    Map<Character, Glyph> glyphs) {
            this.textureId = textureId;
            this.pixelHeight = pixelHeight;
            this.ascent = ascent;
            this.descent = descent;
            this.lineGap = lineGap;
            this.glyphs = glyphs;
        }

        public Glyph getGlyph(char c) {
            Glyph g = glyphs.get(c);
            if (g != null) return g;
            if (glyphs.containsKey('?')) return glyphs.get('?');
            Glyph blank = new Glyph();
            blank.c = c;
            blank.advance = pixelHeight * 0.6f;
            return blank;
        }

        public float measureWidth(String s) {
            float w = 0;
            for (int i = 0; i < s.length(); i++) {
                w += getGlyph(s.charAt(i)).advance;
            }
            return w;
        }

        public int getTextureId() {
            return textureId;
        }

        public float getAscent() {
            return ascent;
        }

        public float getDescent() {
            return descent;
        }

        public float getLineGap() {
            return lineGap;
        }

        public float getLineHeight() {
            return ascent - descent + lineGap;
        }

        public void cleanup() {
            glDeleteTextures(textureId);
        }
    }

    public static class Glyph {
        public char c;
        public float width;
        public float height;
        public float bearingX;
        public float bearingY;
        public float advance;
        public float texX, texY, texW, texH;
    }
}
