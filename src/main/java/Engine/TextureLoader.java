package Engine;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL46.*;

public class TextureLoader {

    public static int loadTexture(String resourcePath) {
        // Force path to be resource-local (no leading slash)
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;

        try (InputStream in = TextureLoader.class.getClassLoader().getResourceAsStream(normalized)) {
            if (in == null) {
                System.err.println("❌ Texture not found: " + resourcePath);
                return 0;
            }

            // Read all bytes
            byte[] bytes = in.readAllBytes();
            ByteBuffer imageBuffer = BufferUtils.createByteBuffer(bytes.length);
            imageBuffer.put(bytes);
            imageBuffer.flip();

            // Prepare STB buffers
            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);

            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer image = STBImage.stbi_load_from_memory(imageBuffer, width, height, channels, 4);

            if (image == null) {
                System.err.println("❌ STB failed to load image " + resourcePath + ": " + STBImage.stbi_failure_reason());
                return 0;
            }

            // Generate OpenGL texture
            int textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width.get(0), height.get(0),
                    0, GL_RGBA, GL_UNSIGNED_BYTE, image);

            glGenerateMipmap(GL_TEXTURE_2D);

            // Texture parameters
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

            // Cleanup
            STBImage.stbi_image_free(image);
            glBindTexture(GL_TEXTURE_2D, 0);

            System.out.println("✔ Loaded texture: " + resourcePath + "   -> ID: " + textureId);
            return textureId;

        } catch (Exception e) {
            System.err.println("❌ Failed loading texture: " + resourcePath);
            e.printStackTrace();
            return 0;
        }
    }

    public static void deleteTexture(int textureId) {
        glDeleteTextures(textureId);
    }
}
