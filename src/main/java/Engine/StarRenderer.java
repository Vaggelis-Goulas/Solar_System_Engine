package Engine;

import Engine.threading.Parallel;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class StarRenderer {
    private final ShaderProgram shader;
    private final List<Vector3f> starPositions;
    private final List<Vector3f> starColors;
    private final List<Float> starScales;
    private Mesh starMesh;

    public StarRenderer(ShaderProgram starShader) {
        this.shader = starShader;
        this.starPositions = new ArrayList<>();
        this.starColors = new ArrayList<>();
        this.starScales = new ArrayList<>();
        createStarMesh();
    }

    private void createStarMesh() {
        // Create a proper mesh with the expected layout: position(3), normal(3), texcoord(2)
        float[] vertices = {
                // positions          // normals           // texcoords
                -0.5f, -0.5f, 0.0f,  0.0f, 0.0f, 1.0f,   0.0f, 0.0f,
                0.5f, -0.5f, 0.0f,  0.0f, 0.0f, 1.0f,   1.0f, 0.0f,
                0.5f,  0.5f, 0.0f,  0.0f, 0.0f, 1.0f,   1.0f, 1.0f,
                -0.5f,  0.5f, 0.0f,  0.0f, 0.0f, 1.0f,   0.0f, 1.0f
        };

        int[] indices = {
                0, 1, 2,
                2, 3, 0
        };

        this.starMesh = new Mesh(vertices, indices);
    }

    public void loadFromResource(int count) {
        long startNanos = System.nanoTime();

        // Parallel: pre-size the arrays and let each worker fill its own disjoint
        // slots (Vector3f allocation and ThreadLocalRandom are thread-safe); merge once done.
        Vector3f[] positions = new Vector3f[count];
        Vector3f[] colors = new Vector3f[count];
        float[] scales = new float[count];
        Parallel.forEach(0, count, i -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            // Generate points on a sphere surface
            double theta = rnd.nextDouble() * 2 * Math.PI;
            double phi = Math.acos(2 * rnd.nextDouble() - 1);
            double r = 500 + rnd.nextDouble() * 1500; // Distance from center

            float x = (float) (r * Math.sin(phi) * Math.cos(theta));
            float y = (float) (r * Math.sin(phi) * Math.sin(theta));
            float z = (float) (r * Math.cos(phi));

            positions[i] = new Vector3f(x, y, z);

            // Random color with slight variations (mostly white with some color tint).
            // Brighter base than before so the field no longer reads as "too dark".
            float base = 0.95f + (float) rnd.nextDouble() * 0.4f;
            float rColor = base * (0.85f + (float) rnd.nextDouble() * 0.15f);
            float gColor = base * (0.85f + (float) rnd.nextDouble() * 0.15f);
            float bColor = base * (0.85f + (float) rnd.nextDouble() * 0.15f);

            colors[i] = new Vector3f(rColor, gColor, bColor);

            // Per-star size (0.07 - 0.25 render units) for brighter, jittered pinpoints.
            scales[i] = 0.07f + (float) rnd.nextDouble() * 0.18f;
        });

        starPositions.addAll(Arrays.asList(positions));
        starColors.addAll(Arrays.asList(colors));
        for (float s : scales) {
            starScales.add(s);
        }

        double ms = (System.nanoTime() - startNanos) / 1_000_000.0;
        System.out.printf("Generated %d stars in %.2f ms (parallel)%n", count, ms);
    }

    /**
     * Renders stars using the camera-relative (floating-origin) pipeline. Because the view
     * matrix is rotation-only (camera at origin), each star is drawn offset by the camera
     * position so that constellations are stable in space regardless of where the camera is.
     */
    public void render(Matrix4f view, Matrix4f projection, Vector3d cameraPos) {
        shader.use();
        shader.setUniformMat4("view", view);
        shader.setUniformMat4("projection", projection);

        for (int i = 0; i < starPositions.size(); i++) {
            Vector3f pos = starPositions.get(i);
            Vector3f color = starColors.get(i);
            float scale = starScales.get(i);

            // Offset the stored position by the camera to keep it stable in relative space.
            Matrix4f model = new Matrix4f()
                    .translate(
                            pos.x - (float) cameraPos.x,
                            pos.y - (float) cameraPos.y,
                            pos.z - (float) cameraPos.z)
                    .scale(scale); // Small size for stars

            shader.setUniformMat4("model", model);
            shader.setUniform("color", color);

            starMesh.render();
        }

        shader.unbind();
    }

    public void cleanup() {
        if (starMesh != null) {
            starMesh.cleanup();
        }
    }
}