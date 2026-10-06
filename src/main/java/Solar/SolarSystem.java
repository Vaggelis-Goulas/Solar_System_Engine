package Solar;

import Engine.Mesh;
import Engine.ShaderProgram;
import Engine.lod.LevelOfDetail;
import Engine.threading.Parallel;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.opengl.*;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.glActiveTexture;

public class SolarSystem {
    private final List<Planet> planets;
    private final List<Planet> asteroidBelt;
    private final List<Planet> kuiperBelt;
    private final Mesh[] sphereLODs;
    private Mesh ringMesh;
    private final ShaderProgram shader;

    // For statistics and debugging
    private float simulationTime = 0f;
    private int frameCount = 0;

    // LOD bookkeeping (set during renderAll, reported each stats interval).
    private int drawnBodies = 0;
    private int culledBodies = 0;

    // Set once per frame by renderAll; used by per-planet render methods to compute camera-relative matrices.
    private Vector3d cameraPos = new Vector3d();

    public SolarSystem(Mesh[] sphereLODs, ShaderProgram planetShader,
                       int mercuryTex, int venusTex, int earthTex, int marsTex,
                       int jupiterTex, int saturnTex, int saturnRingTex,
                       int uranusTex, int neptuneTex, int sunTex,
                       int ceresTex, int plutoTex, int haumeaTex, int makemakeTex, int erisTex) {
        this.sphereLODs = sphereLODs;
        this.shader = planetShader;
        this.planets = new ArrayList<>();
        this.asteroidBelt = new ArrayList<>();
        this.kuiperBelt = new ArrayList<>();

        createRingMesh();
        createPlanets(mercuryTex, venusTex, earthTex, marsTex, jupiterTex,
                saturnTex, saturnRingTex, uranusTex, neptuneTex, sunTex);
        createDwarfPlanets(ceresTex, plutoTex, haumeaTex, makemakeTex, erisTex);
        createAsteroidBelt();
        createKuiperBelt();

        printSolarSystemInfo();
    }

    private void createRingMesh() {
        // Create ring mesh with improved subdivisions for better appearance
        int segments = 128;  // Increased for smoother circumference
        int radialRings = 32;  // Increased for denser radial divisions (smoother rings)

        float innerRadius = 1.2f;
        float outerRadius = 2.3f;  // Slightly wider for more realistic proportions

        float[] vertices = new float[segments * radialRings * 8];
        int[] indices = new int[segments * (radialRings - 1) * 6];

        int vertexIndex = 0;
        int indexIndex = 0;

        for (int ring = 0; ring < radialRings; ring++) {
            float radius = innerRadius + (outerRadius - innerRadius) * (float) ring / (radialRings - 1);

            for (int i = 0; i < segments; i++) {
                float angle = (float) (2.0f * Math.PI * i / segments);
                float x = (float) (Math.cos(angle) * radius);
                float z = (float) (Math.sin(angle) * radius);

                vertices[vertexIndex++] = x;
                vertices[vertexIndex++] = 0.0f;
                vertices[vertexIndex++] = z;
                vertices[vertexIndex++] = 0.0f;
                vertices[vertexIndex++] = 1.0f;
                vertices[vertexIndex++] = 0.0f;
                vertices[vertexIndex++] = (float) i / segments;
                vertices[vertexIndex++] = (float) ring / (radialRings - 1);
            }
        }

        for (int ring = 0; ring < radialRings - 1; ring++) {
            for (int i = 0; i < segments; i++) {
                int current = ring * segments + i;
                int next = ring * segments + ((i + 1) % segments);
                int below = (ring + 1) * segments + i;
                int belowNext = (ring + 1) * segments + ((i + 1) % segments);

                indices[indexIndex++] = current;
                indices[indexIndex++] = below;
                indices[indexIndex++] = next;
                indices[indexIndex++] = next;
                indices[indexIndex++] = below;
                indices[indexIndex++] = belowNext;
            }
        }

        this.ringMesh = new Mesh(vertices, indices);
        System.out.println("Created ring mesh with " + segments + " segments and " + radialRings + " radial rings");
    }

    private void createPlanets(int mercuryTex, int venusTex, int earthTex, int marsTex,
                               int jupiterTex, int saturnTex, int saturnRingTex,
                               int uranusTex, int neptuneTex, int sunTex) {
        // SUN FIRST - with detailed debugging
        System.out.println("=== CREATING SUN ===");
        System.out.println("Sun texture ID: " + sunTex);
        System.out.println("Sun radius: " + Constants.SUN_RADIUS);
        if (sunTex == 0) {
            System.err.println("WARNING: Sun texture failed to load! Using fallback color.");
        }
        planets.add(new Planet("Sun", Constants.SUN_RADIUS, 0f, 0f, Constants.MERCURY_ROTATION, sunTex, 0f));

        // Other planets
        planets.add(new Planet("Mercury", Constants.MERCURY_RADIUS, Constants.MERCURY_ORBIT,
                Constants.MERCURY_PERIOD, Constants.MERCURY_ROTATION, mercuryTex, Constants.MERCURY_TILT));
        planets.add(new Planet("Venus", Constants.VENUS_RADIUS, Constants.VENUS_ORBIT,
                Constants.VENUS_PERIOD, Constants.VENUS_ROTATION, venusTex, Constants.VENUS_TILT));
        planets.add(new Planet("Earth", Constants.EARTH_RADIUS, Constants.EARTH_ORBIT,
                Constants.EARTH_PERIOD, Constants.EARTH_ROTATION, earthTex, Constants.EARTH_TILT));
        planets.add(new Planet("Mars", Constants.MARS_RADIUS, Constants.MARS_ORBIT,
                Constants.MARS_PERIOD, Constants.MARS_ROTATION, marsTex, Constants.MARS_TILT));
        planets.add(new Planet("Jupiter", Constants.JUPITER_RADIUS, Constants.JUPITER_ORBIT,
                Constants.JUPITER_PERIOD, Constants.JUPITER_ROTATION, jupiterTex, Constants.JUPITER_TILT));
        planets.add(new Planet("Saturn", Constants.SATURN_RADIUS, Constants.SATURN_ORBIT,
                Constants.SATURN_PERIOD, Constants.SATURN_ROTATION, saturnTex, Constants.SATURN_TILT, true, saturnRingTex));
        planets.add(new Planet("Uranus", Constants.URANUS_RADIUS, Constants.URANUS_ORBIT,
                Constants.URANUS_PERIOD, Constants.URANUS_ROTATION, uranusTex, Constants.URANUS_TILT));
        planets.add(new Planet("Neptune", Constants.NEPTUNE_RADIUS, Constants.NEPTUNE_ORBIT,
                Constants.NEPTUNE_PERIOD, Constants.NEPTUNE_ROTATION, neptuneTex, Constants.NEPTUNE_TILT));
    }

    private void createDwarfPlanets(int ceresTex, int plutoTex, int haumeaTex, int makemakeTex, int erisTex) {
        planets.add(new Planet("Ceres", Constants.CERES_RADIUS, Constants.CERES_ORBIT,
                Constants.CERES_PERIOD, Constants.CERES_ROTATION, ceresTex, Constants.CERES_TILT));
        planets.add(new Planet("Pluto", Constants.PLUTO_RADIUS, Constants.PLUTO_ORBIT,
                Constants.PLUTO_PERIOD, Constants.PLUTO_ROTATION, plutoTex, Constants.PLUTO_TILT));
        planets.add(new Planet("Haumea", Constants.HAUMEA_RADIUS, Constants.HAUMEA_ORBIT,
                Constants.HAUMEA_PERIOD, Constants.HAUMEA_ROTATION, haumeaTex, Constants.HAUMEA_TILT));
        planets.add(new Planet("Makemake", Constants.MAKEMAKE_RADIUS, Constants.MAKEMAKE_ORBIT,
                Constants.MAKEMAKE_PERIOD, Constants.MAKEMAKE_ROTATION, makemakeTex, Constants.MAKEMAKE_TILT));
        planets.add(new Planet("Eris", Constants.ERIS_RADIUS, Constants.ERIS_ORBIT,
                Constants.ERIS_PERIOD, Constants.ERIS_ROTATION, erisTex, Constants.ERIS_TILT));
    }

    private void createAsteroidBelt() {
        int asteroidCount = 2500;
        float minDistance = 2.5f;
        float maxDistance = 3.1f; // Narrower radial spread for a thinner-looking belt

        long startNanos = System.nanoTime();

        // Parallel: each worker writes its own slice into a disjoint array slot; the list
        // is only touched after every element is built (never from a worker thread).
        Planet[] rocks = new Planet[asteroidCount];
        Parallel.forEach(0, asteroidCount, i -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            float distance = minDistance + (float) rnd.nextDouble() * (maxDistance - minDistance);
            float angle = (float) rnd.nextDouble() * (float) Math.PI * 2f;
            float radius = 0.008f + (float) rnd.nextDouble() * 0.04f;
            float rotationPeriod = 0.5f + (float) rnd.nextDouble() * 2f;

            Planet asteroid = new Planet("Asteroid", radius, distance, 3.0f + (float) rnd.nextDouble() * 2f,
                    rotationPeriod, 0, 0f);
            asteroid.orbitalAngle = angle;

            // Add small vertical scatter for 3D thickness (requires yOffset in Planet - see note below)
            asteroid.yOffset = (float) (rnd.nextDouble() - 0.5f) * 0.18f;

            rocks[i] = asteroid;
        });
        asteroidBelt.addAll(Arrays.asList(rocks));

        double ms = (System.nanoTime() - startNanos) / 1_000_000.0;
        System.out.printf("Created thinner asteroid belt with %d asteroids in %.2f ms (parallel)%n",
                asteroidCount, ms);
    }

    private void createKuiperBelt() {
        int kboCount = 600;
        float minDistance = Constants.NEPTUNE_ORBIT + 5f;
        float maxDistance = minDistance + 20f;

        long startNanos = System.nanoTime();

        Planet[] rocks = new Planet[kboCount];
        Parallel.forEach(0, kboCount, i -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            float distance = minDistance + (float) rnd.nextDouble() * (maxDistance - minDistance);
            float angle = (float) rnd.nextDouble() * (float) Math.PI * 2f;
            float radius = 0.04f + (float) rnd.nextDouble() * 0.09f;
            float rotationPeriod = 1f + (float) rnd.nextDouble() * 3f;

            Planet kbo = new Planet("KBO", radius, distance, 200f + (float) rnd.nextDouble() * 100f,
                    rotationPeriod, 0, 0f);
            kbo.orbitalAngle = angle;

            // Larger vertical scatter for Kuiper belt thickness
            kbo.yOffset = (float) (rnd.nextDouble() - 0.5f) * 1.5f;

            rocks[i] = kbo;
        });
        kuiperBelt.addAll(Arrays.asList(rocks));

        double ms = (System.nanoTime() - startNanos) / 1_000_000.0;
        System.out.printf("Created Kuiper belt with %d objects in %.2f ms (parallel)%n",
                kboCount, ms);
    }

    public void update(float deltaTime) {
        simulationTime += deltaTime;
        frameCount++;

        for (Planet planet : planets) {
            planet.update(deltaTime);
        }
        for (Planet asteroid : asteroidBelt) {
            asteroid.update(deltaTime);
        }
        for (Planet kbo : kuiperBelt) {
            kbo.update(deltaTime);
        }

        if (frameCount % 60 == 0) {
            printPlanetStatistics();
        }
    }

    public void renderAll(Matrix4f view, Matrix4f proj, Vector3d cameraPos) {
        this.cameraPos.set(cameraPos);
        shader.use();

        // Global uniforms (set once per frame)
        shader.setUniformMat4("view", view);
        shader.setUniformMat4("projection", proj);
        shader.setUniform("lightPos", new Vector3f(0f, 0f, 0f));
        shader.setUniform("lightColor", new Vector3f(3.2f, 3.0f, 2.8f)); // Warm, intense sunlight
        shader.setUniform("viewPos", new Vector3f((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z));

        drawnBodies = 0;
        culledBodies = 0;

        renderPlanets();
        renderAsteroidBelt();
        renderKuiperBelt();
    }

    private void renderPlanets() {
        for (Planet planet : planets) {
            renderPlanet(planet);
        }
    }

    /**
     * Picks the sphere mesh for a body from its angular size (radius / distance). Navigation
     * targets degrade at worst to {@link LevelOfDetail#DISTANT}; the Sun is always ULTRA since
     * it is the visual anchor and light source.
     */
    private Mesh selectMesh(Planet planet, Vector3d worldPos) {
        double dist = worldPos.distance(cameraPos);
        // Near plane clamp avoids div-by-zero on exact overlap.
        double angular = planet.getScaledRadius() / Math.max(dist, Constants.NEAR_PLANE_DIST);
        LevelOfDetail lod = planet.getName().equals("Sun")
                ? LevelOfDetail.ULTRA
                : LevelOfDetail.forPlanet(angular);
        return sphereLODs[lod.ordinal()];
    }

    private void renderPlanet(Planet planet) {
        Vector3d world = planet.getWorldPositionD();
        Mesh mesh = selectMesh(planet, world);
        if (mesh == null) {
            culledBodies++;
            return;
        }
        drawnBodies++;

        Matrix4f model = planet.getModelMatrix(cameraPos);
        shader.setUniformMat4("model", model);

        if (planet.getTextureId() != 0) {
            shader.setUniform("useTexture", 1);
            glActiveTexture(GL13C.GL_TEXTURE0);
            glBindTexture(ARBInternalformatQuery2.GL_TEXTURE_2D, planet.getTextureId());
            shader.setUniform("texture1", 0);
        } else {
            shader.setUniform("useTexture", 0);
            shader.setUniform("materialColor", new Vector3f(0.8f, 0.8f, 0.8f));
        }

        shader.setUniform("isSun", planet.getName().equals("Sun") ? 1 : 0);

        glDisable(GL11C.GL_BLEND);
        glDepthMask(true);

        mesh.render();

        if (planet.hasRings() && ringMesh != null && planet.getRingTextureId() != 0) {
            renderPlanetRings(planet);
        }
    }

    private void renderPlanetRings(Planet planet) {
        Matrix4f ringModel = planet.getRingModelMatrix(cameraPos);
        if (ringModel != null) {
            shader.setUniformMat4("model", ringModel);
            shader.setUniform("useTexture", 1);
            shader.setUniform("isSun", 0);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            glBindTexture(GL_TEXTURE_2D, planet.getRingTextureId());
            shader.setUniform("texture1", 0);

            ringMesh.render();

            glDisable(GL_BLEND);
        }
    }

    private void renderBelt(List<Planet> belt, Vector3f fallbackColor) {
        shader.setUniform("useTexture", 0);
        shader.setUniform("isSun", 0);
        shader.setUniform("materialColor", fallbackColor);

        for (Planet rock : belt) {
            Vector3d world = rock.getWorldPositionD();
            Mesh mesh = selectBeltMesh(rock, world);
            if (mesh == null) {
                culledBodies++;
                continue;
            }
            drawnBodies++;
            shader.setUniformMat4("model", rock.getModelMatrix(cameraPos));
            glDisable(GL_BLEND);
            glDepthMask(true);
            mesh.render();
        }
    }

    /**
     * Belt rocks are not navigation targets, so they use the raw {@link LevelOfDetail#select}
     * rule: sub-pixel rocks are culled outright (huge draw-call savings at range). The
     * {@link LevelOfDetail#DISTANT} tier is the future seat of billboard impostors.
     */
    private Mesh selectBeltMesh(Planet rock, Vector3d worldPos) {
        double dist = worldPos.distance(cameraPos);
        double angular = rock.getScaledRadius() / Math.max(dist, Constants.NEAR_PLANE_DIST);
        LevelOfDetail lod = LevelOfDetail.select(angular);
        return sphereLODs[lod.ordinal()];
    }

    private void renderAsteroidBelt() {
        renderBelt(asteroidBelt, new Vector3f(0.7f, 0.6f, 0.4f));
    }

    private void renderKuiperBelt() {
        renderBelt(kuiperBelt, new Vector3f(0.5f, 0.5f, 0.7f));
    }

    private void printSolarSystemInfo() {
        System.out.println("=== SOLAR SYSTEM INFORMATION ===");
        System.out.println("Total planets: " + planets.size());
        System.out.println("Total asteroids: " + asteroidBelt.size() + " (DENSE BELT)");
        System.out.println("Total KBOs: " + kuiperBelt.size());

        Planet sun = planets.get(0);
        System.out.println("SUN - Radius: " + sun.getScaledRadius() + " units, Texture: " +
                (sun.getTextureId() != 0 ? "LOADED" : "MISSING"));

        for (int i = 1; i < planets.size(); i++) {
            Planet planet = planets.get(i);
            System.out.printf("%-10s | Distance: %6.2f AU | Radius: %5.2f units%n",
                    planet.getName(),
                    planet.getAUDistance(),
                    planet.getScaledRadius());
        }
        System.out.println("================================\n");
    }

    private void printPlanetStatistics() {
        System.out.println("=== REAL-TIME PLANET STATISTICS (Frame: " + frameCount + ") ===");
        Planet sun = planets.get(0);

        for (int i = 1; i < planets.size(); i++) {
            Planet planet = planets.get(i);
            float distanceFromSun = planet.getDistanceFrom(sun);
            System.out.printf("%-10s | Distance from Sun: %6.1f units%n",
                    planet.getName(),
                    distanceFromSun);
        }
        System.out.println("Sun position: " + sun.getPosition());
        System.out.printf("LOD: %d bodies drawn, %d culled%n", drawnBodies, culledBodies);
        System.out.println("=========================\n");
    }

    // Debug method to check if sun is in view
    public void debugSunVisibility(Vector3f cameraPos) {
        Planet sun = planets.get(0);
        Vector3f sunPos = sun.getPosition();
        float sunRadius = sun.getScaledRadius();

        System.out.println("=== SUN VISIBILITY DEBUG ===");
        System.out.println("Sun position: " + sunPos);
        System.out.println("Sun radius: " + sunRadius);
        System.out.println("Camera position: " + cameraPos);
        System.out.println("Distance from camera to sun: " + sunPos.distance(cameraPos));
        System.out.println("Sun texture ID: " + sun.getTextureId());
        System.out.println("============================\n");
    }

    // Public methods
    public List<Planet> getPlanets() { return new ArrayList<>(planets); }

    public Planet getSun() { return planets.get(0); }

    /**
     * Method to get planetary system info for UI
     */
    public String getSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== SOLAR SYSTEM STATUS ===\n");
        info.append("Planets: ").append(planets.size()).append("\n");
        info.append("Asteroids: ").append(asteroidBelt.size()).append(" objects\n");
        info.append("KBOs: ").append(kuiperBelt.size()).append(" objects\n");
        info.append("Total: ").append(getTotalCelestialBodies()).append(" celestial bodies\n");
        info.append("Simulation Time: ").append(String.format("%.1f", simulationTime)).append("s\n");
        info.append("Frame: ").append(frameCount).append("\n");

        // Sun information
        Planet sun = getSun();
        info.append("\n--- SUN ---\n");
        info.append("Position: ").append(sun.getPosition().toString()).append("\n");
        info.append("Radius: ").append(String.format("%.1f", sun.getScaledRadius())).append(" units\n");
        info.append("Texture: ").append(sun.getTextureId() != 0 ? "LOADED" : "MISSING").append("\n");

        // Planet distances from sun
        info.append("\n--- PLANET DISTANCES FROM SUN ---\n");
        for (int i = 1; i < Math.min(planets.size(), 6); i++) { // Show first 5 planets
            Planet planet = planets.get(i);
            float distance = planet.getDistanceFrom(sun);
            info.append(String.format("%-8s: %6.1f units\n", planet.getName(), distance));
        }

        if (planets.size() > 6) {
            info.append("... and ").append(planets.size() - 6).append(" more planets/dwarf planets\n");
        }

        return info.toString();
    }

    // Method to get total number of celestial bodies
    public int getTotalCelestialBodies() {
        return planets.size() + asteroidBelt.size() + kuiperBelt.size();
    }

    public void cleanup() {
        for (Mesh mesh : sphereLODs) {
            if (mesh != null) {
                mesh.cleanup();
            }
        }
        if (ringMesh != null) {
            ringMesh.cleanup();
        }
    }

    /** Last-frame LOD activity, for the stats HUD/debug output. */
    public int getDrawnBodies() { return drawnBodies; }
    public int getCulledBodies() { return culledBodies; }
}