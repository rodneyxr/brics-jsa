package dk.brics.string;

import org.apache.log4j.*;

/**
 * Debug mode enabled/disabled.
 * Enable debug info with <code>-Ddk.brics.string.debug=&lt;LEVEL&gt;</code>.
 */
public class Debug {


    private static boolean test_mode;
    private static boolean isInitialized;

    static {
        init();
    }

    public static void init() {
        if (isInitialized) {
            return;
        }
        isInitialized = true;
        String debug = System.getProperty("dk.brics.string.debug");
        if (debug != null && !Logger.getRootLogger().getAllAppenders().hasMoreElements()) {
            int debug_level;
            try {
                debug_level = Integer.parseInt(debug);
            } catch (NumberFormatException e) {
                debug_level = 1;
            }
            BasicConfigurator.configure(new ConsoleAppender(new PatternLayout("%m%n"), "System.err"));
            Logger log = Logger.getRootLogger();
            if (debug_level == 0) {
                log.setLevel(Level.WARN);
            } else if (debug_level == 1) {
                log.setLevel(Level.INFO);
            } else if (debug_level == 2) {
                log.setLevel(Level.DEBUG);
            } else {
                log.setLevel(Level.ALL);
            }
        }
        test_mode = System.getProperty("dk.brics.string.test") != null;
    }

    private Debug() {
    }


    /**
     * Returns true if in test mode.
     * This is often used to ensure that unordered collections are traversed in a particular order.
     */
    public static boolean isTest() {
        return test_mode;
    }

    /**
     * Sets test mode.
     */
    public static void setTest(boolean test_mode) {
        Debug.test_mode = test_mode;
    }
}
