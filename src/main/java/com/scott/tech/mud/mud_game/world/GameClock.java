package com.scott.tech.mud.mud_game.world;

import java.time.Clock;
import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lightweight in-game clock that maps real wall-clock time onto atmospheric
 * day/night phases. Intentionally a stateless utility so it can be called
 * from anywhere (look output, ambient ticker, future UI badges) without
 * threading another bean through the command pipeline.
 *
 * <p>The mapping is deliberately simple — the player's local hour of day
 * drives the phase. A future iteration can swap {@link #currentPhase()}
 * for an accelerated game-time clock without touching call sites.</p>
 */
public final class GameClock {

    public enum Phase {
        DAWN("dawn"),
        MORNING("morning"),
        AFTERNOON("afternoon"),
        DUSK("dusk"),
        EVENING("evening"),
        NIGHT("night");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static volatile Clock clock = Clock.systemDefaultZone();

    private GameClock() {
    }

    /**
     * Override the underlying clock. Intended for tests; production code uses
     * the system clock and never calls this.
     */
    public static void setClock(Clock newClock) {
        clock = newClock == null ? Clock.systemDefaultZone() : newClock;
    }

    public static Phase currentPhase() {
        int hour = LocalTime.now(clock).getHour();
        if (hour >= 5 && hour < 7)   return Phase.DAWN;
        if (hour >= 7 && hour < 12)  return Phase.MORNING;
        if (hour >= 12 && hour < 17) return Phase.AFTERNOON;
        if (hour >= 17 && hour < 19) return Phase.DUSK;
        if (hour >= 19 && hour < 22) return Phase.EVENING;
        return Phase.NIGHT;
    }

    /**
     * A short atmospheric flavor line for the current phase. Multiple lines
     * per phase are kept so repeated looks in the same room don't read the
     * exact same banner every time.
     */
    public static String currentFlavor() {
        return flavorFor(currentPhase());
    }

    public static String flavorFor(Phase phase) {
        String[] options = switch (phase) {
            case DAWN -> new String[] {
                    "Pale dawn light, mist in the hollows.",
                    "The sky greys toward sunrise.",
                    "Birds stir; the world is waking."
            };
            case MORNING -> new String[] {
                    "Crisp morning light, clean shadows.",
                    "The sun is high and the day feels new.",
                    "Bright morning, busy air."
            };
            case AFTERNOON -> new String[] {
                    "Sun hangs heavy; the air is still.",
                    "Long afternoon shadows lean east.",
                    "Dust drifts in slanted light."
            };
            case DUSK -> new String[] {
                    "Copper sky; shadows stretch long.",
                    "Dusk paints the world amber.",
                    "First stars prick the failing light."
            };
            case EVENING -> new String[] {
                    "Evening hush; lamps glow against the dark.",
                    "A cool quiet has settled in.",
                    "The sky is deep bruised blue."
            };
            case NIGHT -> new String[] {
                    "Night lies thick; stars and torchlight push it back.",
                    "The dark is deep and watchful.",
                    "Shadows pool in every corner."
            };
        };
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }
}
