package Engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

public class Skybox {
    private final Mesh mesh;
    private final ShaderProgram shader;
    private final int textureId;
    private final float scale;

    public Skybox(Mesh sphereMesh, ShaderProgram skyShader, int textureId, float scale) {
        this.mesh = sphereMesh;
        this.shader = skyShader;
        this.textureId = textureId;
        this.scale = scale;
    }

    public void render(Vector3f cameraPos, Matrix4f view, Matrix4f projection) {
        glDepthFunc(GL_LEQUAL);
        shader.use();

        // Remove translation from view matrix for skybox
        Matrix4f skyboxView = new Matrix4f(view);
        skyboxView.m30(0);
        skyboxView.m31(0);
        skyboxView.m32(0);

        Matrix4f model = new Matrix4f()
                .translate(cameraPos)
                .scale(scale);

        shader.setUniformMat4("model", model);
        shader.setUniformMat4("view", skyboxView);
        shader.setUniformMat4("projection", projection);

        if (textureId != 0) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureId);
            shader.setUniform("skybox", 0);
        }

        mesh.render();
        shader.unbind();
        glDepthFunc(GL_LESS);
    }
}