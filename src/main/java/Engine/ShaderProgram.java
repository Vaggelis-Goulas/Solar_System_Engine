package Engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL46.*;

public class ShaderProgram {

    private final int programId;
    private final Map<String, Integer> uniformLocations;
    private final Map<String, Integer> uniformBlocks;

    public ShaderProgram(String vertexResourcePath, String fragmentResourcePath) {
        programId = glCreateProgram();
        uniformLocations = new HashMap<>();
        uniformBlocks = new HashMap<>();

        System.out.println("Compiling vertex shader: " + vertexResourcePath);
        int vertId = createShader(loadResource(vertexResourcePath), GL_VERTEX_SHADER);

        System.out.println("Compiling fragment shader: " + fragmentResourcePath);
        int fragId = createShader(loadResource(fragmentResourcePath), GL_FRAGMENT_SHADER);

        glAttachShader(programId, vertId);
        glAttachShader(programId, fragId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String error = glGetProgramInfoLog(programId);
            System.err.println("Program linking failed: " + error);
            throw new IllegalStateException("Program linking failed: " + error);
        }

        glDeleteShader(vertId);
        glDeleteShader(fragId);

        // Cache common uniform locations
        cacheCommonUniforms();

        System.out.println("Shader program created successfully: " + programId);
    }

    private void cacheCommonUniforms() {
        // Cache commonly used uniform locations for performance
        String[] commonUniforms = {
                "model", "view", "projection", "mvp", "modelView", "normalMatrix",
                "color", "tintColor", "alpha",
                "texture1", "texture2", "texture3", "useTexture", "textureEnabled",
                "screenPosition", "screenSize", "elementColor", "uiColor",
                "isSun", "lightPos", "lightColor", "viewPos", "cameraPos",
                "materialColor", "material.diffuse", "material.specular", "material.shininess",
                "light.position", "light.color", "light.ambient", "light.diffuse", "light.specular",
                "time", "sinTime", "cosTime", "deltaTime",
                "resolution", "aspectRatio", "nearPlane", "farPlane"
        };

        for (String uniform : commonUniforms) {
            cacheUniformLocation(uniform);
        }
    }

    private int createShader(String src, int type) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);

        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(id);
            System.err.println("Shader compilation failed: " + error);
            System.err.println("Shader type: " + getShaderTypeName(type));
            System.err.println("Shader source:\n" + src);
            throw new IllegalStateException("Shader compile failed: " + error);
        }

        // Print compilation success message
        String shaderType = getShaderTypeName(type);
        System.out.println(shaderType + " shader compiled successfully");

        return id;
    }

    private String getShaderTypeName(int type) {
        return switch (type) {
            case GL_VERTEX_SHADER -> "Vertex";
            case GL_FRAGMENT_SHADER -> "Fragment";
            case GL_GEOMETRY_SHADER -> "Geometry";
            case GL_COMPUTE_SHADER -> "Compute";
            default -> "Unknown";
        };
    }

    private String loadResource(String path) {
        // Load from shaders directory without any extension
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String fullPath = "shaders/" + normalized;

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(fullPath)) {
            if (in == null) {
                throw new IllegalStateException("Shader resource not found: " + fullPath + " (tried: " + normalized + ")");
            }
            String source = new String(in.readAllBytes());
            System.out.println("Loaded shader: " + fullPath + " (" + source.length() + " chars)");
            return source;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load shader resource: " + fullPath, e);
        }
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    /**
     * Alias for bind() - follows common OpenGL naming convention
     */
    public void use() {
        bind();
    }

    /**
     * Cache uniform location for better performance
     */
    private int cacheUniformLocation(String name) {
        int loc = glGetUniformLocation(programId, name);
        if (loc == -1) {
            System.err.println("Warning: Uniform '" + name + "' not found in shader program (might be optimized out)");
        } else {
            System.out.println("Cached uniform: " + name + " at location " + loc);
        }
        uniformLocations.put(name, loc);
        return loc;
    }

    public int getUniformLocation(String name) {
        // Return cached location if available
        Integer cached = uniformLocations.get(name);
        if (cached != null) {
            return cached;
        }
        // Otherwise cache and return
        return cacheUniformLocation(name);
    }

    // Enhanced uniform setting methods with better error handling

    public void setUniform(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform1i(location, value);
        }
    }

    public void setUniform(String name, Vector3f v) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform3f(location, v.x, v.y, v.z);
        }
    }

    public void setUniform(String name, Vector4f v) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform4f(location, v.x, v.y, v.z, v.w);
        }
    }

    public void setUniformMat4(String name, Matrix4f mat) {
        int location = getUniformLocation(name);
        if (location != -1) {
            FloatBuffer fb = BufferUtils.createFloatBuffer(16);
            mat.get(fb);
            glUniformMatrix4fv(location, false, fb);
        }
    }


    public void delete() {
        if (programId != 0) {
            glDeleteProgram(programId);
            uniformLocations.clear();
            uniformBlocks.clear();
            System.out.println("Shader program " + programId + " deleted");
        }
    }

    public void cleanup() {
        delete();
    }

}