package Engine;

import Engine.lod.LevelOfDetail;
import Engine.threading.Parallel;

public class MeshFactory {

    /**
     * Creates a family of UV sphere meshes, one per non-culled {@link LevelOfDetail} tier,
     * indexed by `LevelOfDetail.ordinal()`. Finer tiers use denser tessellations; the
     * {@link LevelOfDetail#CULLED} slot is null. All meshes are built through the existing
     * parallel path.
     */
    public static Mesh[] createLODSpheres() {
        Mesh[] meshes = new Mesh[LevelOfDetail.values().length];
        meshes[LevelOfDetail.ULTRA.ordinal()] = createSphere(32, 32);
        meshes[LevelOfDetail.HIGH.ordinal()] = createSphere(24, 24);
        meshes[LevelOfDetail.MEDIUM.ordinal()] = createSphere(16, 16);
        meshes[LevelOfDetail.LOW.ordinal()] = createSphere(10, 10);
        meshes[LevelOfDetail.DISTANT.ordinal()] = createSphere(4, 4);
        meshes[LevelOfDetail.CULLED.ordinal()] = null;
        return meshes;
    }

    /**
     * Creates a UV sphere mesh.
     */
    public static Mesh createSphere(int stacks, int slices) {

        int vertexCount = (stacks + 1) * (slices + 1);
        float[] vertices = new float[vertexCount * 8]; // pos3, normal3, UV2
        int index = 0;

        // Position vertices are per (i,j) independent -> parallel fill.
        Parallel.forEach(0, vertexCount, v -> {
            int i = v / (slices + 1);
            int j = v % (slices + 1);
            float u = (float) j / slices;
            float vv = (float) i / stacks;
            float phi = (float) (vv * Math.PI);
            float theta = (float) (u * Math.PI * 2);

            // Position
            float x = (float) (Math.sin(phi) * Math.cos(theta));
            float y = (float) Math.cos(phi);
            float z = (float) (Math.sin(phi) * Math.sin(theta));

            int base = v * 8;
            vertices[base]     = x; // pos.x
            vertices[base + 1] = y; // pos.y
            vertices[base + 2] = z; // pos.z
            vertices[base + 3] = x; // normal x
            vertices[base + 4] = y; // normal y
            vertices[base + 5] = z; // normal z
            vertices[base + 6] = u; // uv.u
            vertices[base + 7] = vv; // uv.v
        });

        int faceCount = stacks * slices * 6;
        int[] indices = new int[faceCount];

        // Index generation is trivially indexable too.
        Parallel.forEach(0, stacks, i -> {
            int rowBase = i * slices * 6;
            for (int j = 0; j < slices; j++) {
                int first = (i * (slices + 1)) + j;
                int second = first + slices + 1;
                int f = rowBase + j * 6;
                indices[f]     = first;
                indices[f + 1] = second;
                indices[f + 2] = first + 1;
                indices[f + 3] = second;
                indices[f + 4] = second + 1;
                indices[f + 5] = first + 1;
            }
        });

        return new Mesh(vertices, indices);
    }
}