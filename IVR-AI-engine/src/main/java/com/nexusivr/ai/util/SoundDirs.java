package com.nexusivr.ai.util;

/**
 * Resolves the directories the voice-prompt servlets write to / read from.
 *
 * <p>The first candidate comes from the {@code NEXUSIVR_SOUNDS_DIR} environment
 * variable (used by Docker/deployment); the remaining entries are runtime
 * locations plus a working-directory-relative fallback that works when the
 * engine is run from a checkout of the repo.
 */
public final class SoundDirs {

    private SoundDirs() {
    }

    public static String[] resolveBaseSoundDirs() {
        String custom = System.getenv("NEXUSIVR_SOUNDS_DIR");
        if (custom != null && !custom.isBlank()) {
            return new String[] {
                custom,
                "/var/lib/asterisk/sounds",
                "/tmp/nexusivr/sounds",
            };
        }
        return new String[] {
            "/var/lib/asterisk/sounds",
            "/tmp/nexusivr/sounds",
            System.getProperty("user.dir") + "/assets/custom voice prompts",
        };
    }
}
