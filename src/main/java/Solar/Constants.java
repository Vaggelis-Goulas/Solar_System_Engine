package Solar;

public final class Constants {

    private Constants() {}

    // CHANGED: Much larger AU scale for visibility
    public static final float AU = 500f;

    // Guard for angular-size LOD math when the camera grazes a body's center.
    public static final double NEAR_PLANE_DIST = 0.001;

    // Time - CHANGED: Faster time scale for visible movement
    public static final float SECONDS_PER_DAY = 86400f;
    public static final float TIME_SCALE = 100f;

    // SUN - CHANGED: Made much larger (centerpiece)
    public static final float SUN_RADIUS = 15.0f; // Large enough to be visible

    // Planet radii (VISIBLE SCALE)
    public static final float MERCURY_RADIUS = 2.0f;
    public static final float VENUS_RADIUS   = 3.0f;
    public static final float EARTH_RADIUS   = 3.2f;
    public static final float MARS_RADIUS    = 2.5f;
    public static final float JUPITER_RADIUS = 8.0f;
    public static final float SATURN_RADIUS  = 7.0f;
    public static final float URANUS_RADIUS  = 5.0f;
    public static final float NEPTUNE_RADIUS = 5.0f;

    // Dwarf Planets
    public static final float CERES_RADIUS   = 1.0f;
    public static final float PLUTO_RADIUS   = 1.2f;
    public static final float HAUMEA_RADIUS  = 1.0f;
    public static final float MAKEMAKE_RADIUS = 1.0f;
    public static final float ERIS_RADIUS    = 1.2f;

    // Orbital distances (AU)
    public static final float MERCURY_ORBIT = 0.39f;
    public static final float VENUS_ORBIT   = 0.72f;
    public static final float EARTH_ORBIT   = 1.00f;
    public static final float MARS_ORBIT    = 1.52f;
    public static final float JUPITER_ORBIT = 5.20f;
    public static final float SATURN_ORBIT  = 9.58f;
    public static final float URANUS_ORBIT  = 19.22f;
    public static final float NEPTUNE_ORBIT = 30.05f;
    public static final float CERES_ORBIT   = 2.77f;
    public static final float PLUTO_ORBIT   = 39.48f;
    public static final float HAUMEA_ORBIT  = 43.13f;
    public static final float MAKEMAKE_ORBIT = 45.79f;
    public static final float ERIS_ORBIT    = 67.78f;

    // Orbital periods (Earth years) - USED in Planet.java for orbital movement
    public static final float MERCURY_PERIOD = 0.24f;
    public static final float VENUS_PERIOD   = 0.62f;
    public static final float EARTH_PERIOD   = 1.00f;
    public static final float MARS_PERIOD    = 1.88f;
    public static final float JUPITER_PERIOD = 11.86f;
    public static final float SATURN_PERIOD  = 29.46f;
    public static final float URANUS_PERIOD  = 84.01f;
    public static final float NEPTUNE_PERIOD = 164.8f;
    public static final float CERES_PERIOD   = 4.60f;
    public static final float PLUTO_PERIOD   = 248.0f;
    public static final float HAUMEA_PERIOD  = 285.0f;
    public static final float MAKEMAKE_PERIOD = 309.0f;
    public static final float ERIS_PERIOD    = 557.0f;

    // Rotation periods (Earth days) - USED in Planet.java for day/night cycle
    public static final float MERCURY_ROTATION = 58.6f;
    public static final float VENUS_ROTATION   = 243f;
    public static final float EARTH_ROTATION   = 1f;
    public static final float MARS_ROTATION    = 1.03f;
    public static final float JUPITER_ROTATION = 0.41f;
    public static final float SATURN_ROTATION  = 0.45f;
    public static final float URANUS_ROTATION  = 0.72f;
    public static final float NEPTUNE_ROTATION = 0.67f;
    public static final float CERES_ROTATION   = 0.38f;
    public static final float PLUTO_ROTATION   = 6.39f;
    public static final float HAUMEA_ROTATION  = 0.16f;
    public static final float MAKEMAKE_ROTATION = 0.32f;
    public static final float ERIS_ROTATION    = 1.08f;

    // Axial tilts (degrees) - USED in Planet.java for planet orientation
    public static final float MERCURY_TILT = 0.034f;
    public static final float VENUS_TILT   = 177.4f;
    public static final float EARTH_TILT   = 23.44f;
    public static final float MARS_TILT    = 25.19f;
    public static final float JUPITER_TILT = 3.13f;
    public static final float SATURN_TILT  = 26.73f;
    public static final float URANUS_TILT  = 97.77f;
    public static final float NEPTUNE_TILT = 28.32f;
    public static final float CERES_TILT   = 3.0f;
    public static final float PLUTO_TILT   = 122.53f;
    public static final float HAUMEA_TILT  = 0f;
    public static final float MAKEMAKE_TILT = 0f;
    public static final float ERIS_TILT    = 0f;
}