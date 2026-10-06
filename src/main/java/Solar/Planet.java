package Solar;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

public class Planet {
    private final String name;
    private final float radius;
    private final float distanceFromSun; // in AU
    private final float orbitalPeriod; // in Earth years
    double orbitalAngle; // stored in double for future precision at scale
    private final int textureId;
    private final float axialTilt;
    private final boolean hasRings;
    private final int ringTextureId;

    // Rotation angles for day/night cycle
    private float rotationAngle;
    private final float rotationSpeed;

    public float yOffset = 0f;

    public Planet(String name, float radius, float distance, float orbitalPeriod,
                  float rotationPeriod, int textureId, float axialTilt) {
        this(name, radius, distance, orbitalPeriod, rotationPeriod, textureId, axialTilt, false, 0);
    }

    public Planet(String name, float radius, float distance, float orbitalPeriod,
                  float rotationPeriod, int textureId, float axialTilt, boolean hasRings, int ringTextureId) {
        this.name = name;
        this.radius = radius;
        this.distanceFromSun = distance;
        this.orbitalPeriod = orbitalPeriod;
        // in Earth days
        this.textureId = textureId;
        this.axialTilt = axialTilt;
        this.hasRings = hasRings;
        this.ringTextureId = ringTextureId;
        this.orbitalAngle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        this.rotationAngle = 0f;

        // Calculate rotation speed using constants - fixed float casting
        this.rotationSpeed = ((2.0f * (float) Math.PI) / (rotationPeriod * Constants.SECONDS_PER_DAY)) * Constants.TIME_SCALE;
    }

    public void update(float deltaTime) {
        // Update orbital position using real orbital period and time constants - fixed float casting
        if (orbitalPeriod > 0) {
            float orbitalSpeed = (2.0f * (float) Math.PI) / (orbitalPeriod * Constants.SECONDS_PER_DAY * 365.25f);
            orbitalAngle += (double) orbitalSpeed * deltaTime * Constants.TIME_SCALE;
        }

        // Update rotation for day/night cycle using calculated rotation speed
        rotationAngle += rotationSpeed * deltaTime;
    }

    // --- Camera-relative model matrices ----------------------------------
    //
    // These compute the absolute world position in double precision, then produce a model
    // matrix whose translation is offset relative to the camera (also double). This is the
    // floating-origin pipeline (see Engine.astro.*): the vertex shader receives rotation-only
    // view matrices, so every draw stays small and precise regardless of absolute distance.

    /**
     * Absolute world-space position of this body, as doubles. The authoritative coordinate.
     */
    public Vector3d getWorldPositionD() {
        double renderDistance = distanceFromSun * Constants.AU;
        double x = Math.cos(orbitalAngle) * renderDistance;
        double z = Math.sin(orbitalAngle) * renderDistance;
        return new Vector3d(x, yOffset, z);
    }

    public Matrix4f getModelMatrix(Vector3d cameraPos) {
        Vector3d world = getWorldPositionD();
        return new Matrix4f()
                .translate(
                        (float) (world.x - cameraPos.x),
                        (float) (world.y - cameraPos.y),
                        (float) (world.z - cameraPos.z))
                .rotate((float) Math.toRadians(axialTilt), 0, 0, 1) // Apply axial tilt
                .rotateY(rotationAngle) // Apply rotation for day/night cycle
                .scale(radius);
    }

    public Matrix4f getRingModelMatrix(Vector3d cameraPos) {
        if (!hasRings) return null;

        Vector3d world = getWorldPositionD();
        return new Matrix4f()
                .translate(
                        (float) (world.x - cameraPos.x),
                        (float) (world.y - cameraPos.y),
                        (float) (world.z - cameraPos.z))
                .rotate((float) Math.toRadians(axialTilt), 0, 0, 1)
                .scale(radius * 2.5f); // Rings are larger than the planet
    }

    // Get scaled distance in render units using Constants.AU
    public float getScaledDistance() {
        return distanceFromSun * Constants.AU;
    }

    // Get scaled radius in render units - CHANGED: return direct radius
    public float getScaledRadius() {
        return radius;
    }

    // Get current orbital position in 3D space (single-precision view of the authoritative double position)
    public Vector3f getPosition() {
        Vector3d world = getWorldPositionD();
        return new Vector3f((float) world.x, (float) world.y, (float) world.z);
    }

    // Get distance from another planet using position calculation
    public float getDistanceFrom(Planet other) {
        Vector3f pos1 = this.getPosition();
        Vector3f pos2 = other.getPosition();
        return pos1.distance(pos2);
    }

    // Get scaled AU distance (for UI display)
    public float getAUDistance() {
        return distanceFromSun;
    }

    // Get orbital period in Earth years
    public float getOrbitalPeriodYears() {
        return orbitalPeriod;
    }

    // Getters
    public String getName() { return name; }
    public int getTextureId() { return textureId; }
    public boolean hasRings() { return hasRings; }
    public int getRingTextureId() { return ringTextureId; }

}