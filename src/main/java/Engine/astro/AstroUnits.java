package Engine.astro;

/**
 * Scale and unit conversion constants for astronomical rendering.
 *
 * <p>The simulation uses a single internal "render unit" (currently 1 unit = 1 AU at the
 * solar-system scale, see {@link Solar.Constants#AU}) but future Space Engine-style content
 * spans parsecs and kiloparsecs. These helpers centralize conversions so code never mixes
 * scales implicitly.</p>
 */
public final class AstroUnits {

    private AstroUnits() {}

    /**
     * Astronomical unit in metres (IAU definition). Used as the reference base unit.
     */
    public static final double AU_M = 149_597_870_700.0;

    /** One light-year in metres. */
    public static final double LIGHT_YEAR_M = 9_460_730_472_580_800.0;

    /**
     * One parsec in metres. A parsec is the distance at which one AU subtends one arcsecond.
     */
    public static final double PARSEC_M = 3.085_677_581_491_367_3e16;

    /** Kiloparsec in metres (galactic-scale unit). */
    public static final double KILOPARSEC_M = PARSEC_M * 1000.0;

    /** Light speed in metres/second. */
    public static final double SPEED_OF_LIGHT_MPS = 299_792_458.0;

    // --- AU / render-unit scale factory ----------------------------------
    // The existing engine renders in units where the solar system is scaled by Solar.Constants.AU.
    // We keep the conversion factor pluggable but read the engine's chosen AU scale once.

    /** Number of metres represented by one render unit. */
    public static double METRES_PER_UNIT;

    /**
     * Initialise the render-unit scale from the engine's AU constant.
     *
     * @param renderUnitsPerAU how many render units equal 1 AU (engine scale factor)
     */
    public static void init(double renderUnitsPerAU) {
        METRES_PER_UNIT = AU_M / renderUnitsPerAU;
        System.out.printf("AstroUnits initialised: 1 render unit = %.3e m%n", METRES_PER_UNIT);
    }

    // --- Conversions ------------------------------------------------------

    /** Convert AU to render units. */
    public static double auToUnits(double au) {
        return au * METRES_PER_UNIT / AU_M;
    }

    /** Convert light-years to render units. */
    public static double lyToUnits(double ly) {
        return ly * LIGHT_YEAR_M / METRES_PER_UNIT;
    }

    /** Convert parsecs to render units. */
    public static double pcToUnits(double pc) {
        return pc * PARSEC_M / METRES_PER_UNIT;
    }

    /** Convert kiloparsecs to render units. */
    public static double kpcToUnits(double kpc) {
        return kpc * KILOPARSEC_M / METRES_PER_UNIT;
    }

    /** Convert render units to AU. */
    public static double unitsToAU(double units) {
        return units * AU_M / METRES_PER_UNIT;
    }

    /** Convert render units to light-years. */
    public static double unitsToLy(double units) {
        return units * METRES_PER_UNIT / LIGHT_YEAR_M;
    }

    // --- Human-readable helpers ------------------------------------------

    /**
     * Pretty-print a distance in render units using the most appropriate astronomical scale.
     */
    public static String formatDistance(double units) {
        if (units < AU_M / METRES_PER_UNIT) {
            return String.format("%.2f units", units);
        }
        double au = unitsToAU(units);
        if (au < 1000.0) {
            return String.format("%.2f AU", au);
        }
        double ly = unitsToLy(units);
        if (ly < 1.0) {
            return String.format("%.2f AU", au);
        }
        if (ly < 1000.0) {
            return String.format("%.2f ly", ly);
        }
        return String.format("%.2f kly", ly / 1000.0);
    }
}
