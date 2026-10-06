package Main;

import Engine.*;
import Engine.camera.Camera;
import Engine.hud.ARHUD;
import Engine.hud.ShapeRenderer;
import Engine.hud.TextRenderer;
import Engine.utils.FontLoader;
import Engine.utils.Time;
import Engine.utils.InputHandler;
import Solar.SolarSystem;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL32C.GL_PROGRAM_POINT_SIZE;

public class Main {

    private static boolean sunDebugEnabled = false;

    // Utility systems
    private static Time time;
    private static InputHandler input;

    // AR HUD
    private static ARHUD hud;

    public static void main(String[] args) {
        System.out.println("Starting Solar System Simulation...");

        try {
            // Initialize utility systems first
            time = Time.getInstance();
            input = InputHandler.getInstance();

            Window window = new Window(1200, 800, "Solar System LWJGL - Space Engine Style");
            window.init();

            // Connect input handler to window
            input.initialize(window.getWindowHandle());

            System.out.println("Window initialized successfully");

            float initialFov = 75f;
            Renderer renderer = new Renderer(initialFov, window.getWidth(), window.getHeight(), 0.1f, 50000f);
            renderer.init();

            glEnable(GL_PROGRAM_POINT_SIZE);
            glEnable(GL_DEPTH_TEST);

            // Shaders with error handling
            System.out.println("Loading shaders...");
            ShaderProgram planetShader;
            ShaderProgram starShader;

            try {
                planetShader = new ShaderProgram("vertex", "fragment");
                starShader = new ShaderProgram("star_vertex", "star_fragment");
                System.out.println("All shaders loaded successfully");
            } catch (Exception e) {
                System.err.println("Failed to load shaders: " + e.getMessage());
                e.printStackTrace();
                window.cleanup();
                return;
            }

            // Load ALL planet textures
            System.out.println("Loading textures...");
            int sunTex = TextureLoader.loadTexture("Planets/2k_sun.jpg");
            int mercuryTex = TextureLoader.loadTexture("Planets/2k_mercury.jpg");
            int venusTex = TextureLoader.loadTexture("Planets/2k_venus_surface.jpg");
            int earthTex = TextureLoader.loadTexture("Planets/2k_earth_daymap.jpg");
            int marsTex = TextureLoader.loadTexture("Planets/2k_mars.jpg");
            int jupiterTex = TextureLoader.loadTexture("Planets/2k_jupiter.jpg");
            int saturnTex = TextureLoader.loadTexture("Planets/2k_saturn.jpg");
            int saturnRingTex = TextureLoader.loadTexture("Planets/2k_saturn_ring_alpha.png");
            int uranusTex = TextureLoader.loadTexture("Planets/2k_uranus.jpg");
            int neptuneTex = TextureLoader.loadTexture("Planets/2k_neptune.jpg");
            int ceresTex = TextureLoader.loadTexture("Planets/2k_ceres_fictional.jpg");
            int plutoTex = TextureLoader.loadTexture("Planets/pluto_texture_map_remastered_by_neptuneprograming.dbvwi9r-fullview.jpg");
            int haumeaTex = TextureLoader.loadTexture("Planets/2k_haumea_fictional.jpg");
            int makemakeTex = TextureLoader.loadTexture("Planets/2k_makemake_fictional.jpg");
            int erisTex = TextureLoader.loadTexture("Planets/2k_eris_fictional.jpg");
            System.out.println("=== TEXTURE LOADING SUMMARY ===");
            System.out.println("Sun texture: " + (sunTex != 0 ? "SUCCESS (ID: " + sunTex + ")" : "FAILED"));

            // Meshes (multi-resolution LOD family, indexed by Engine.lod.LevelOfDetail)
            System.out.println("Creating LOD sphere meshes...");
            Mesh[] sphereLODs = MeshFactory.createLODSpheres();
            System.out.println("LOD sphere meshes created");

            // Skybox - temporarily disabled (designated for future replacement)

            // Solar system with ALL planets
            SolarSystem solarSystem = new SolarSystem(sphereLODs, planetShader,
                    mercuryTex, venusTex, earthTex, marsTex,
                    jupiterTex, saturnTex, saturnRingTex,
                    uranusTex, neptuneTex, sunTex,
                    ceresTex, plutoTex, haumeaTex, makemakeTex, erisTex);

            System.out.println("Solar System Initialized:");
            for (Solar.Planet planet : solarSystem.getPlanets()) {
                System.out.printf("  %s - Distance: %.1f, Radius: %.1f, Position: %s%n",
                        planet.getName(),
                        planet.getScaledDistance(),
                        planet.getScaledRadius(),
                        planet.getPosition().toString()
                );
            }

            // Camera setup
            Camera camera = new Camera(new Vector3f(0f, 10f, 50f), -90f, -10f);
            camera.setMovementSpeed(20f);

            // Stars
            StarRenderer starRenderer = new StarRenderer(starShader);
            starRenderer.loadFromResource(1000);

            // AR HUD: shader, font, and compositors.
            System.out.println("Initializing AR HUD...");
            ShaderProgram hudShader = new ShaderProgram("ar_vertex", "ar_fragment");
            FontLoader.Font hudFont = FontLoader.getInstance().loadFont("consola.ttf", 32);
            TextRenderer textRenderer = new TextRenderer(hudFont);
            ShapeRenderer shapeRenderer = new ShapeRenderer();
            hud = new ARHUD(hudShader, textRenderer, shapeRenderer);
            hud.setSolarSystem(solarSystem);
            System.out.println("AR HUD initialized");

            // Scroll callback (FOV / speed)
            GLFW.glfwSetScrollCallback(window.getWindowHandle(), (win, xoffset, yoffset) -> {
                boolean shift = input.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
                if (shift) {
                    float curr = camera.getMovementSpeed();
                    float factor = (yoffset > 0) ? 1.1f : 0.9f;
                    float next = Math.max(0.01f, Math.min(1000f, curr * factor));
                    camera.setMovementSpeed(next);
                    System.out.println("Movement speed: " + next);
                } else {
                    float fov = renderer.getFov();
                    fov += (float) (-yoffset * 2.5f);
                    fov = Math.max(15f, Math.min(100f, fov));
                    renderer.setFov(fov);
                    System.out.println("FOV: " + fov);
                }
            });

            // Set up error callback for GLFW
            GLFW.glfwSetErrorCallback((error, description) -> System.err.println("GLFW Error " + error + ": " + description));

            System.out.println("Initialization complete. Starting main loop...");
            printControls();

            // Create a timer for occasional debug output
            Time.Timer debugTimer = time.createTimer(5.0f); // Print every 5 seconds
            debugTimer.start();

            while (!window.shouldClose()) {
                try {
                    // Update timing and input systems
                    time.update();
                    input.update();
                    float dt = time.getDeltaTime();

                    // Handle fixed updates for physics
                    while (time.shouldDoFixedUpdate()) {
                        solarSystem.update(time.getFixedDeltaTime());
                    }

                    // Update debug timer
                    debugTimer.update(dt);
                    if (debugTimer.isFinished()) {
                        System.out.printf("System running - FPS: %.1f, Time: %.1fs\n",
                                time.getFPS(), time.getTime());
                        debugTimer.start(); // Restart timer
                    }

                    if (window.wasResized()) {
                        glViewport(0, 0, window.getWidth(), window.getHeight());
                        renderer.setViewportSize(window.getWidth(), window.getHeight());
                        window.clearResizedFlag();
                        System.out.println("Window resized to: " + window.getWidth() + "x" + window.getHeight());
                    }

                    // Poll OS events FIRST so isKeyPressed/Released detect edges this frame.
                    window.pollEvents();

                    handleInput(camera, dt, solarSystem, hud, window);

                    renderer.clear();

                    // Camera-relative (floating-origin) pipeline: the view matrix is rotation-only
                    // and every model matrix is offset relative to the camera in double precision
                    // (see Engine.astro.*). This keeps precision regardless of travel distance.
                    Vector3f camPos = camera.getPosition();
                    org.joml.Vector3d camPosD = new org.joml.Vector3d(camPos.x, camPos.y, camPos.z);
                    Matrix4f view = camera.getCameraRelativeViewMatrix();
                    Matrix4f proj = renderer.getProjectionMatrix();

                    // Sun visibility debugging
                    if (sunDebugEnabled && debugTimer.getProgress() > 0.5f) {
                        solarSystem.debugSunVisibility(camera.getPosition());
                    }

                    // Render stars
                    starRenderer.render(view, proj, camPosD);

                    // Then planets (including sun)
                    solarSystem.renderAll(view, proj, camPosD);

                    // AR overlay on top of the 3D scene.
                    hud.setFov(renderer.getFov());
                    hud.setMovementSpeed(camera.getMovementSpeed());
                    hud.render(camera, solarSystem, view, proj, camPosD,
                            window.getWidth(), window.getHeight());

                    window.swapBuffers();

                    // Use time-based delay instead of fixed sleep
                    float targetFrameTime = 1.0f / 60.0f; // Target 60 FPS
                    float actualFrameTime = time.getUnscaledDeltaTime();
                    if (actualFrameTime < targetFrameTime) {
                        try {
                            Thread.sleep((long) ((targetFrameTime - actualFrameTime) * 1000));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Error in main loop: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }

            // Cleanup
            System.out.println("Cleaning up resources...");
            cleanupResources(starRenderer, solarSystem, planetShader,
                    starShader, window, hudShader, sunTex, mercuryTex, venusTex,
                    earthTex, marsTex, jupiterTex, saturnTex, saturnRingTex,
                    uranusTex, neptuneTex, ceresTex, plutoTex, haumeaTex,
                    makemakeTex, erisTex);

        } catch (Exception e) {
            System.err.println("Fatal error during initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleInput(Camera camera, float dt, SolarSystem solarSystem, ARHUD hud, Window window) {
        // Feed cursor position to the HUD (needed for nav menu hover + reticle selection).
        Vector2f mouse = input.getMousePosition();
        hud.setMousePosition(mouse.x, mouse.y);

        // If the navigation menu is open, it captures all mouse/keys (modal).
        Engine.hud.NavigationMenu nav = hud.getNavigationMenu();
        if (nav.isOpen()) {
            // ESC / N closes the menu without selecting.
            if (input.isKeyPressed(GLFW.GLFW_KEY_ESCAPE) || input.isKeyPressed(GLFW.GLFW_KEY_N)) {
                nav.setOpen(false);
                input.setCursorVisible(false);
            }
            // Mouse click selects a row.
            if (input.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                int selected = nav.handleClick(mouse.x, mouse.y);
                if (selected >= 0) {
                    hud.selectIndex(selected);
                    Solar.Planet p = hud.getTargetPlanet();
                    System.out.println("Navigation: " + (p != null ? p.getName() : "index " + selected));
                }
                input.setCursorVisible(false);
            }
            return; // Modal: ignore all other movement/selection input.
        }

        // Movement controls using InputHandler
        if (input.isMovingForward()) camera.moveForward(dt);
        if (input.isMovingBackward()) camera.moveBackward(dt);
        if (input.isMovingLeft()) camera.moveLeft(dt);
        if (input.isMovingRight()) camera.moveRight(dt);
        if (input.isKeyDown(GLFW.GLFW_KEY_SPACE)) camera.moveUp(dt);
        if (input.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL)) camera.moveDown(dt);

        // Mouse look
        if (input.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            Vector2f mouseDelta = input.getMouseDelta();
            camera.rotate(mouseDelta.x, mouseDelta.y);
        }

        // Open the navigation menu (shows the cursor for clicking).
        if (input.isKeyPressed(GLFW.GLFW_KEY_N)) {
            nav.setOpen(true);
            input.setCursorVisible(true);
            System.out.println("Navigation menu opened");
        }

        // Number keys for camera presets
        if (input.isKeyPressed(GLFW.GLFW_KEY_1)) {
            camera.setPosition(new Vector3f(0f, 10f, 50f));
            camera.setRotation(-90f, -10f);
            System.out.println("Camera: Default view");
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_2)) {
            camera.setPosition(new Vector3f(0f, 5f, 30f));
            camera.setRotation(-90f, -5f);
            System.out.println("Camera: Inner planets view");
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_3)) {
            camera.setPosition(new Vector3f(0f, 50f, 300f));
            camera.setRotation(-90f, -15f);
            System.out.println("Camera: Outer planets view");
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_4)) {
            camera.setPosition(new Vector3f(0f, 200f, 0f));
            camera.setRotation(-90f, -89f);
            System.out.println("Camera: Top-down view");
        }

        // Navigation: the N menu is the only way to set a target (see modal block above).
        // Legacy per-body quick select / cycling keys are removed — selection happens in the menu.

        // Debug keys
        if (input.isKeyPressed(GLFW.GLFW_KEY_P)) {
            solarSystem.debugSunVisibility(camera.getPosition());
        }

        if (input.isKeyPressed(GLFW.GLFW_KEY_O)) {
            sunDebugEnabled = !sunDebugEnabled;
            System.out.println("Continuous sun debug: " + (sunDebugEnabled ? "ENABLED" : "DISABLED"));
        }

        if (input.isKeyPressed(GLFW.GLFW_KEY_I)) {
            System.out.println("=== SYSTEM INFORMATION ===");
            System.out.println("Camera Position: " + camera.getPosition());
            System.out.println("Camera State: " + camera.getCameraState());
            System.out.println("Movement Speed: " + camera.getMovementSpeed());
            System.out.println("Simulation Time: " + time.getTime() + "s");
            System.out.println("Time Scale: " + time.getTimeScale());
            System.out.println(solarSystem.getSystemInfo());
        }

        // Time scale controls
        if (input.isKeyPressed(GLFW.GLFW_KEY_EQUAL)) {
            float newScale = time.getTimeScale() * 2.0f;
            time.setTimeScale(newScale);
            System.out.println("Time scale: " + newScale + "x");
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_MINUS)) {
            float newScale = time.getTimeScale() * 0.5f;
            time.setTimeScale(newScale);
            System.out.println("Time scale: " + newScale + "x");
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_0)) {
            time.setTimeScale(1.0f);
            System.out.println("Time scale: 1.0x (normal)");
        }

        // ESC to close window
        if (input.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
            GLFW.glfwSetWindowShouldClose(window.getWindowHandle(), true);
        }

        // Mouse cursor toggle
        if (input.isKeyPressed(GLFW.GLFW_KEY_C)) {
            boolean visible = !input.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            input.setCursorVisible(visible);
            System.out.println("Cursor: " + (visible ? "VISIBLE" : "HIDDEN"));
        }
    }

    private static void printControls() {        System.out.println("Controls:");
        System.out.println("  WASD + Space/Ctrl: Move camera");
        System.out.println("  Right Mouse: Look around");
        System.out.println("  Scroll: Zoom (Hold Shift for speed control)");
        System.out.println("  1,2,3,4: Camera presets");
        System.out.println("  N: Open navigation menu (click a body to select it)");
        System.out.println("  P: Sun visibility debug");
        System.out.println("  O: Toggle continuous sun debug");
        System.out.println("  I: System information");
        System.out.println("  +/-: Increase/decrease time scale");
        System.out.println("  0: Reset time scale to normal");
        System.out.println("  C: Toggle mouse cursor");
        System.out.println("  ESC: Exit");
    }

    private static void cleanupResources(StarRenderer starRenderer, SolarSystem solarSystem,
                                         ShaderProgram planetShader, ShaderProgram starShader,
                                         Window window, ShaderProgram hudShader, int... textureIds) {
        starRenderer.cleanup();
        solarSystem.cleanup();

        // AR HUD cleanup (textures + shapes + font atlas).
        if (hud != null) {
            hud.cleanup();
            hudShader.delete();
            FontLoader.getInstance().cleanup();
        }

        // Delete all textures
        for (int texId : textureIds) {
            if (texId != 0) TextureLoader.deleteTexture(texId);
        }

        // Delete shaders
        planetShader.delete();
        starShader.delete();

        // Cleanup utility systems
        if (time != null) time.cleanup();
        if (input != null) input.cleanup();

        // Stop background worker threads (wait for any outstanding generation work).
        Engine.threading.TaskPool.getInstance().shutdown();

        window.cleanup();

        System.out.println("Cleanup complete. Exiting.");
    }
}