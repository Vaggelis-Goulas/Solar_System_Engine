package Engine.lod;

/**
 * Distance/size based level-of-detail tiers.
 *
 * <p>Detail is chosen by angular size (radius / distance to camera): the projection-space
 * solid angle is the physically correct driver for how much geometry the eye can resolve.
 * A body subtending a larger angle gets a finer tessellation; once it collapses below a
 * perceived pixel the tier falls to {@link #CULLED} and the body is skipped entirely.</p>
 *
 * <p>This is the shared plumbing for upcoming content (surface detail up-close, billboard
 * impostors at distance, LOD batching) - the {@link #select} methods are the single source
 * of a body's visual fidelity.</p>
 */
public enum LevelOfDetail {

    /** Largest angular size - highest-fidelity rendering (fine surface detail). */
    ULTRA(0.05),
    /** Large on screen - good detail. */
    HIGH(0.02),
    /** Medium on screen - moderate tessellation. */
    MEDIUM(0.008),
    /** Small on screen - coarse geometry, mostly silhouette. */
    LOW(0.0025),
    /** Tiny on screen - minimal mesh; future slot for billboard impostors. */
    DISTANT(0.0006),
    /** Sub-pixel or fully out of range - not rendered. */
    CULLED(0);

    /** Minimum angular size (radians) required for a body to sit at (or above) this tier. */
    private final double minAngularSize;

    LevelOfDetail(double minAngularSize) {
        this.minAngularSize = minAngularSize;
    }

    /**
     * Pick the finest tier whose minimum angular size is satisfied by the given solid angle
     * fraction (radius / distance, in radians). Below {@link #DISTANT} the body is culled.
     *
     * @param angularSize radius / distance in radians
     */
    public static LevelOfDetail select(double angularSize) {
        for (LevelOfDetail lod : values()) {
            if (angularSize >= lod.minAngularSize) {
                return lod;
            }
        }
        return CULLED;
    }

    /**
     * As {@link #select} but never returns {@link #CULLED}: navigation targets (planets)
     * always remain visible, degrading only to {@link #DISTANT} at extreme range.
     */
    public static LevelOfDetail forPlanet(double angularSize) {
        LevelOfDetail lod = select(angularSize);
        return lod == CULLED ? DISTANT : lod;
    }

    public double getMinAngularSize() {
        return minAngularSize;
    }
}