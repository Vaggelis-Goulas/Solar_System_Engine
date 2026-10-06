package Engine;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.util.Objects;

public class Window {

    private long windowHandle;
    private int width;
    private int height;
    private final String title;
    private boolean resized = false;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;
        this.title = title;
    }

    public void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        windowHandle = GLFW.glfwCreateWindow(width, height, title, 0, 0);
        if (windowHandle == 0) throw new RuntimeException("Failed to create GLFW window");

        GLFW.glfwSetFramebufferSizeCallback(windowHandle, (win, w, h) -> {
            this.width = w;
            this.height = h;
            this.resized = true;
        });

        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        if (vidMode != null) {
            GLFW.glfwSetWindowPos(windowHandle,
                    (vidMode.width() - width) / 2,
                    (vidMode.height() - height) / 2);
        }

        GLFW.glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();

        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(windowHandle);

        GL11.glViewport(0, 0, width, height);
    }

    /** Poll OS input events (GLFW callbacks fire here). Call before reading input state. */
    public void pollEvents() {
        GLFW.glfwPollEvents();
    }

    /** Swap front/back buffers. Call after rendering is complete. */
    public void swapBuffers() {
        GLFW.glfwSwapBuffers(windowHandle);
    }

    /** Convenience: poll + swap in one call (legacy). */
    public void update() {
        GLFW.glfwSwapBuffers(windowHandle);
        GLFW.glfwPollEvents();
    }

    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(windowHandle);
    }

    public void cleanup() {
        GLFW.glfwDestroyWindow(windowHandle);
        GLFW.glfwTerminate();
        Objects.requireNonNull(GLFW.glfwSetErrorCallback(null)).free();
    }

    public boolean wasResized() {
        return resized;
    }

    public void clearResizedFlag() {
        resized = false;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getWindowHandle() { return windowHandle; }
}