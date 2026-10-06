package Engine;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.system.MemoryUtil;

/**
 * Generic OpenGL mesh abstraction.
 * Supports position/normal/uv vertex data and indexed drawing.
 */
public class Mesh {

    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private final int vertexCount;

    // Instancing support: a per-instance model-matrix VBO (4 vec4s per instance).
    private int instanceVboId;
    private int instanceCount;

    public Mesh(float[] vertices, int[] indices) {
        this.vertexCount = indices.length;
        this.instanceVboId = 0;
        this.instanceCount = 0;

        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // VBO
        vboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);

        FloatBuffer vertexBuffer = MemoryUtil.memAllocFloat(vertices.length);
        vertexBuffer.put(vertices).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vertexBuffer, GL30.GL_STATIC_DRAW);
        MemoryUtil.memFree(vertexBuffer);

        // Layout: position(3), normal(3), texcoord(2) = 8 floats
        int stride = 8 * Float.BYTES;

        // Position
        GL30.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
        GL30.glEnableVertexAttribArray(0);

        // Normal
        GL30.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GL30.glEnableVertexAttribArray(1);

        // TexCoord
        GL30.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 6 * Float.BYTES);
        GL30.glEnableVertexAttribArray(2);

        // EBO (Index Buffer)
        eboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, eboId);

        IntBuffer indexBuffer = MemoryUtil.memAllocInt(indices.length);
        indexBuffer.put(indices).flip();
        GL30.glBufferData(GL30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL30.GL_STATIC_DRAW);
        MemoryUtil.memFree(indexBuffer);

        GL30.glBindVertexArray(0);
    }

    /**
     * Uploads per-instance model matrices (column-major, 16 floats each) into a dedicated
     * instance VBO bound to vertex attribute locations 3-6. Call once before instanced draws.
     *
     * @param instanceMatrices flattened array of 4x4 matrices (16 floats per instance)
     * @param count number of instances
     */
    public void setInstances(float[] instanceMatrices, int count) {
        if (count <= 0) {
            return;
        }

        if (instanceVboId == 0) {
            GL30.glBindVertexArray(vaoId);
            instanceVboId = GL30.glGenBuffers();
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, instanceVboId);

            int stride = 16 * Float.BYTES;
            // mat4 occupies locations 3,4,5,6
            for (int i = 0; i < 4; i++) {
                GL30.glVertexAttribPointer(3 + i, 4, GL11.GL_FLOAT, false, stride, (long) i * 4 * Float.BYTES);
                GL33.glVertexAttribDivisor(3 + i, 1);
                GL30.glEnableVertexAttribArray(3 + i);
            }
            GL30.glBindVertexArray(0);
        }

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, instanceVboId);
        FloatBuffer buffer = MemoryUtil.memAllocFloat(instanceMatrices.length);
        buffer.put(instanceMatrices).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(buffer);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

        this.instanceCount = count;
    }

    /** Update only the instance transform data in place (layout must match a prior setInstances). */
    public void updateInstances(float[] instanceMatrices, int count) {
        if (instanceVboId == 0 || count <= 0) {
            return;
        }
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, instanceVboId);
        FloatBuffer buffer = MemoryUtil.memAllocFloat(instanceMatrices.length);
        buffer.put(instanceMatrices).flip();
        GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, buffer);
        MemoryUtil.memFree(buffer);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        this.instanceCount = count;
    }

    public boolean hasInstancing() {
        return instanceVboId != 0 && instanceCount > 0;
    }

    /**
     * Draws the mesh.
     */
    public void render() {
        GL30.glBindVertexArray(vaoId);
        if (hasInstancing()) {
            GL33.glDrawElementsInstanced(GL11.GL_TRIANGLES, vertexCount, GL11.GL_UNSIGNED_INT, 0, instanceCount);
        } else {
            GL11.glDrawElements(GL11.GL_TRIANGLES, vertexCount, GL11.GL_UNSIGNED_INT, 0);
        }
        GL30.glBindVertexArray(0);
    }

    /** Draws an explicit number of instances regardless of the stored instanceCount. */
    public void renderInstanced(int count) {
        GL30.glBindVertexArray(vaoId);
        GL33.glDrawElementsInstanced(GL11.GL_TRIANGLES, vertexCount, GL11.GL_UNSIGNED_INT, 0, count);
        GL30.glBindVertexArray(0);
    }

    /**
     * Frees GPU memory.
     */
    public void cleanup() {
        GL30.glDisableVertexAttribArray(0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glDeleteBuffers(vboId);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glDeleteBuffers(eboId);

        if (instanceVboId != 0) {
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
            GL30.glDeleteBuffers(instanceVboId);
            instanceVboId = 0;
        }

        GL30.glBindVertexArray(0);
        GL30.glDeleteVertexArrays(vaoId);
    }
}