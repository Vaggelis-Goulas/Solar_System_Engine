package Engine.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import Engine.utils.InputHandler;
import org.lwjgl.glfw.GLFW;

public class Camera {
    private Vector3f position;
    private Vector3f front;
    private Vector3f up;
    private Vector3f right;
    private Vector3f worldUp;

    private float yaw;
    private float pitch;

    private float movementSpeed;
    private float mouseSensitivity;

    // Track movement state for HUD
    private boolean isMoving = false;
    private Vector3f lastPosition;

    public Camera(Vector3f position, float yaw, float pitch) {
        this.position = new Vector3f(position);
        this.lastPosition = new Vector3f(position);
        this.worldUp = new Vector3f(0, 1, 0);
        this.yaw = yaw;
        this.pitch = pitch;
        this.movementSpeed = 50f;
        this.mouseSensitivity = 0.1f;

        updateVectors();
    }

    private void updateVectors() {
        // Calculate the new front vector
        Vector3f newFront = new Vector3f();
        newFront.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        newFront.y = (float) Math.sin(Math.toRadians(pitch));
        newFront.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        this.front = newFront.normalize();

        // Also re-calculate the right and up vector
        this.right = new Vector3f(front).cross(worldUp).normalize();
        this.up = new Vector3f(right).cross(front).normalize();
    }

    public Matrix4f getViewMatrix() {
        Vector3f center = new Vector3f(position).add(front);
        return new Matrix4f().lookAt(position, center, up);
    }

    /**
     * Returns a rotation-only view matrix suitable for the camera-relative (floating-origin)
     * pipeline. Because all model matrices are already offset relative to the camera
     * (see Engine.astro.CameraRelativeTransform), the view matrix must NOT translate the scene:
     * the camera is treated as sitting at the origin looking along {@code front}.
     */
    public Matrix4f getCameraRelativeViewMatrix() {
        return new Matrix4f().lookAt(new Vector3f(0, 0, 0), front, up);
    }

    public void rotate(float dx, float dy) {
        yaw += dx * mouseSensitivity;
        pitch -= dy * mouseSensitivity;

        // Constrain pitch to avoid flipping
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;

        updateVectors();
    }

    // Movement methods now update movement state
    public void moveForward(float dt) {
        Vector3f move = new Vector3f(front).mul(movementSpeed * dt);
        position.add(move);
        updateMovementState();
    }

    public void moveBackward(float dt) {
        Vector3f move = new Vector3f(front).mul(movementSpeed * dt);
        position.sub(move);
        updateMovementState();
    }

    public void moveLeft(float dt) {
        Vector3f move = new Vector3f(right).mul(movementSpeed * dt);
        position.sub(move);
        updateMovementState();
    }

    public void moveRight(float dt) {
        Vector3f move = new Vector3f(right).mul(movementSpeed * dt);
        position.add(move);
        updateMovementState();
    }

    public void moveUp(float dt) {
        Vector3f move = new Vector3f(up).mul(movementSpeed * dt);
        position.add(move);
        updateMovementState();
    }

    public void moveDown(float dt) {
        Vector3f move = new Vector3f(up).mul(movementSpeed * dt);
        position.sub(move);
        updateMovementState();
    }

    private void updateMovementState() {
        // Check if position has changed significantly
        isMoving = position.distance(lastPosition) > 0.001f;
        lastPosition.set(position);
    }

    // Enhanced movement state tracking
    public boolean isMoving() {
        return isMoving;
    }

    public Vector3f getFront() {
        return new Vector3f(front);
    }

    public Vector3f getRight() {
        return new Vector3f(right);
    }

    public Vector3f getUp() {
        return new Vector3f(up);
    }

    public Vector3f getWorldUp() {
        return new Vector3f(worldUp);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getCurrentSpeed() {
        return movementSpeed;
    }

    public Vector3f getVelocityDirection() {
        return new Vector3f(front);
    }

    public Vector3f getLookAtPoint(float distance) {
        return new Vector3f(position).add(front.mul(distance, new Vector3f()));
    }

    public Matrix4f getOrientationMatrix() {
        return new Matrix4f().lookAt(new Vector3f(0, 0, 0), front, up);
    }

    public float getFOV() {
        return 75.0f;
    }

    public float getDistanceTo(Vector3f point) {
        return position.distance(point);
    }

    public boolean isInFront(Vector3f point) {
        Vector3f toPoint = new Vector3f(point).sub(position);
        return toPoint.dot(front) > 0;
    }

    public float getAltitude(Vector3f referencePoint) {
        return position.y - referencePoint.y;
    }

    public float getHorizontalDistanceTo(Vector3f point) {
        Vector3f horizontalPos = new Vector3f(position.x, 0, position.z);
        Vector3f horizontalPoint = new Vector3f(point.x, 0, point.z);
        return horizontalPos.distance(horizontalPoint);
    }

    public float getSpeedMagnitude() {
        return movementSpeed;
    }

    public float getRoll() {
        return 0.0f;
    }

    // Setters
    public void setPosition(Vector3f position) {
        this.position.set(position);
        updateMovementState();
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        if (this.pitch > 89.0f) this.pitch = 89.0f;
        if (this.pitch < -89.0f) this.pitch = -89.0f;
        updateVectors();
    }

    public void setOrientation(Vector3f front, Vector3f up) {
        this.front.set(front).normalize();
        this.up.set(up).normalize();
        this.right.set(front).cross(up).normalize();

        this.yaw = (float) Math.toDegrees(Math.atan2(front.z, front.x));
        this.pitch = (float) Math.toDegrees(Math.asin(front.y));
    }

    public void lookAt(Vector3f target) {
        Vector3f direction = new Vector3f(target).sub(position).normalize();
        setOrientation(direction, worldUp);
    }

    // Getters
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public float getMovementSpeed() {
        return movementSpeed;
    }

    public void setMovementSpeed(float movementSpeed) {
        this.movementSpeed = movementSpeed;
    }

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    public void setMouseSensitivity(float sensitivity) {
        this.mouseSensitivity = sensitivity;
    }

    public String getCameraState() {
        return String.format(
                "Pos: [%.1f, %.1f, %.1f] | Yaw: %.1f° | Pitch: %.1f° | Speed: %.1f | Moving: %s",
                position.x, position.y, position.z,
                yaw, pitch,
                movementSpeed,
                isMoving ? "YES" : "NO"
        );
    }

    public Camera copy() {
        Camera copy = new Camera(new Vector3f(position), yaw, pitch);
        copy.movementSpeed = movementSpeed;
        copy.mouseSensitivity = mouseSensitivity;
        copy.isMoving = isMoving;
        return copy;
    }
}