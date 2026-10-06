package Engine.utils;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.joml.Vector2f;

public class InputHandler {

    private static InputHandler instance;
    private long windowHandle;

    // Keyboard state
    private final boolean[] currentKeys;
    private final boolean[] previousKeys;
    private final int totalKeys = 512; // GLFW key range

    // Mouse state
    private final boolean[] currentMouseButtons;
    private final boolean[] previousMouseButtons;
    private final Vector2f mousePosition;
    private final Vector2f previousMousePosition;
    private float mouseScroll;
    private float previousMouseScroll;

    // Mouse button constants
    public static final int MOUSE_LEFT = 0;
    public static final int MOUSE_RIGHT = 1;
    public static final int MOUSE_MIDDLE = 2;

    private InputHandler() {
        currentKeys = new boolean[totalKeys];
        previousKeys = new boolean[totalKeys];
        currentMouseButtons = new boolean[8]; // 8 mouse buttons should be enough
        previousMouseButtons = new boolean[8];
        mousePosition = new Vector2f();
        previousMousePosition = new Vector2f();

        System.out.println("InputHandler initialized");
    }

    public static InputHandler getInstance() {
        if (instance == null) {
            instance = new InputHandler();
        }
        return instance;
    }

    public void initialize(long window) {
        this.windowHandle = window;
        setupCallbacks();
        System.out.println("InputHandler connected to window: " + window);
    }

    private void setupCallbacks() {
        // Keyboard callback
        GLFW.glfwSetKeyCallback(windowHandle, new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key >= 0 && key < totalKeys) {
                    currentKeys[key] = action != GLFW.GLFW_RELEASE;

                    // Print key events for debugging (you might want to remove this in production)
                    if (action == GLFW.GLFW_PRESS) {
                        System.out.println("Key pressed: " + GLFW.glfwGetKeyName(key, scancode));
                    }
                }
            }
        });

        GLFW.glfwSetCursorPosCallback(windowHandle, new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                previousMousePosition.set(mousePosition);
                mousePosition.set((float) xpos, (float) ypos);
            }
        });

        // Mouse button callback
        GLFW.glfwSetMouseButtonCallback(windowHandle, new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button >= 0 && button < currentMouseButtons.length) {
                    currentMouseButtons[button] = action != GLFW.GLFW_RELEASE;
                }
            }
        });

        // Mouse scroll callback
        GLFW.glfwSetScrollCallback(windowHandle, new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double xoffset, double yoffset) {
                previousMouseScroll = mouseScroll;
                mouseScroll += (float) yoffset;
            }
        });

        System.out.println("GLFW input callbacks registered");
    }

    public void update() {
        // Save previous states
        System.arraycopy(currentKeys, 0, previousKeys, 0, totalKeys);
        System.arraycopy(currentMouseButtons, 0, previousMouseButtons, 0, currentMouseButtons.length);
        previousMouseScroll = mouseScroll;

        // Reset scroll for frame-based input
        mouseScroll = 0;
    }

    // Keyboard query methods

    public boolean isKeyDown(int keyCode) {
        return keyCode >= 0 && keyCode < totalKeys && currentKeys[keyCode];
    }

    public boolean isKeyPressed(int keyCode) {
        return keyCode >= 0 && keyCode < totalKeys && currentKeys[keyCode] && !previousKeys[keyCode];
    }

    public boolean isKeyReleased(int keyCode) {
        return keyCode >= 0 && keyCode < totalKeys && !currentKeys[keyCode] && previousKeys[keyCode];
    }

    // Mouse query methods

    public boolean isMouseButtonDown(int button) {
        return button >= 0 && button < currentMouseButtons.length && currentMouseButtons[button];
    }

    public boolean isMouseButtonPressed(int button) {
        return button >= 0 && button < currentMouseButtons.length &&
                currentMouseButtons[button] && !previousMouseButtons[button];
    }

    public boolean isMouseButtonReleased(int button) {
        return button >= 0 && button < currentMouseButtons.length &&
                !currentMouseButtons[button] && previousMouseButtons[button];
    }

    public Vector2f getMousePosition() {
        return new Vector2f(mousePosition);
    }

    public Vector2f getMouseDelta() {
        return new Vector2f(mousePosition).sub(previousMousePosition);
    }

    public float getMouseScroll() {
        return mouseScroll;
    }

    public boolean isScrollingUp() {
        return mouseScroll > 0;
    }

    public boolean isScrollingDown() {
        return mouseScroll < 0;
    }

    public void setCursorVisible(boolean visible) {
        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR,
                visible ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_DISABLED);
    }

    public void setCursorPosition(float x, float y) {
        GLFW.glfwSetCursorPos(windowHandle, x, y);
        mousePosition.set(x, y);
    }

    public boolean isMovingForward() {
        return isKeyDown(GLFW.GLFW_KEY_W) || isKeyDown(GLFW.GLFW_KEY_UP);
    }

    public boolean isMovingBackward() {
        return isKeyDown(GLFW.GLFW_KEY_S) || isKeyDown(GLFW.GLFW_KEY_DOWN);
    }

    public boolean isMovingLeft() {
        return isKeyDown(GLFW.GLFW_KEY_A) || isKeyDown(GLFW.GLFW_KEY_LEFT);
    }

    public boolean isMovingRight() {
        return isKeyDown(GLFW.GLFW_KEY_D) || isKeyDown(GLFW.GLFW_KEY_RIGHT);
    }

    public void cleanup() {
        // Callbacks are automatically destroyed when the window is destroyed
        System.out.println("InputHandler cleaned up");
    }
}