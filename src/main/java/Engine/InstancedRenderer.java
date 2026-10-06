package Engine;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable helper for rendering large batches of similar objects with a single instanced draw
 * call. This is the groundwork for procedural generation (star fields, nebulae, asteroid fields,
 * remote galaxies) where thousands of small objects share one mesh.
 *
 * <p>Instance data is stored as flattened 4x4 model matrices. Callers can mutate the backing
 * list and call {@link #reupload()} to push changes to the GPU. The mesh is shared and owned by
 * the caller; this class only manages the instance buffer and the draw call.</p>
 */
public class InstancedRenderer {

    private final Mesh mesh;
    private final List<float[]> instanceMatrices;
    private final List<org.joml.Vector3f> instanceColors;
    private boolean dirty;

    public InstancedRenderer(Mesh mesh) {
        this.mesh = mesh;
        this.instanceMatrices = new ArrayList<>();
        this.instanceColors = new ArrayList<>();
        this.dirty = true;
    }

    /**
     * Add a new instance with the given model matrix.
     */
    public void addInstance(Matrix4f model) {
        float[] arr = new float[16];
        model.get(arr);
        instanceMatrices.add(arr);
        instanceColors.add(new org.joml.Vector3f(1f));
        dirty = true;
    }

    /**
     * Add a new instance with a model matrix and a per-instance color.
     */
    public void addInstance(Matrix4f model, org.joml.Vector3f color) {
        float[] arr = new float[16];
        model.get(arr);
        instanceMatrices.add(arr);
        instanceColors.add(new org.joml.Vector3f(color));
        dirty = true;
    }

    /**
     * Replace the transform of an existing instance.
     */
    public void updateInstance(int index, Matrix4f model) {
        if (index < 0 || index >= instanceMatrices.size()) return;
        float[] arr = instanceMatrices.get(index);
        model.get(arr);
        dirty = true;
    }

    public void clear() {
        instanceMatrices.clear();
        instanceColors.clear();
        dirty = true;
    }

    public int size() {
        return instanceMatrices.size();
    }

    public List<org.joml.Vector3f> getColors() {
        return instanceColors;
    }

    public List<float[]> getInstanceMatrices() {
        return instanceMatrices;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    /** Flatten instance matrices into the mesh's instance buffer if anything changed. */
    public void reupload() {
        if (!dirty || instanceMatrices.isEmpty()) return;
        float[] flat = new float[instanceMatrices.size() * 16];
        for (int i = 0; i < instanceMatrices.size(); i++) {
            System.arraycopy(instanceMatrices.get(i), 0, flat, i * 16, 16);
        }
        mesh.setInstances(flat, instanceMatrices.size());
        dirty = false;
    }

    /**
     * Draw the whole batch. Callers must bind their shader, set view/projection and per-batch
     * uniforms, then invoke this. Shader must support the instanced matrix attribute at
     * locations 3-6 (see the "instanceMatrix" layout in the rendering shaders).
     */
    public void render() {
        if (!dirty) {
            mesh.renderInstanced(instanceMatrices.size());
        } else {
            reupload();
            mesh.renderInstanced(instanceMatrices.size());
        }
    }

    public void cleanup() {
        // The mesh is owned by the caller; only drop our references.
        instanceMatrices.clear();
        instanceColors.clear();
        dirty = false;
    }
}