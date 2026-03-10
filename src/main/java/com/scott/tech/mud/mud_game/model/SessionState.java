package com.scott.tech.mud.mud_game.model;

/**
 * Lifecycle states for a player session.
 *
 * Pre-game (authentication) states:
 *   AWAITING_USERNAME → AWAITING_PASSWORD or AWAITING_CREATION_CONFIRM
 *   AWAITING_PASSWORD → PLAYING (success) or back to AWAITING_USERNAME (locked)
 *   AWAITING_CREATION_CONFIRM → AWAITING_CREATION_PASSWORD or disconnect
 *   AWAITING_CREATION_PASSWORD → AWAITING_RACE_CLASS (for new characters)
 *   AWAITING_RACE_CLASS → AWAITING_PRONOUNS
 *   AWAITING_PRONOUNS → AWAITING_DESCRIPTION
 *   AWAITING_DESCRIPTION → PLAYING (can also skip to PLAYING)
 *
 * In-game:
 *   PLAYING → LOGOUT_CONFIRM (player typed logout)
 *   LOGOUT_CONFIRM → PLAYING (cancelled) or DISCONNECTED (confirmed)
 */
public enum SessionState {
    /** Waiting for the player to enter their username. */
    AWAITING_USERNAME,
    /** Username recognised; waiting for the correct password. */
    AWAITING_PASSWORD,
    /** Username unknown; player must choose to create a character or exit. */
    AWAITING_CREATION_CONFIRM,
    /** Player chose to create; waiting for them to choose a password. */
    AWAITING_CREATION_PASSWORD,
    /** New character: waiting for race and class selection. */
    AWAITING_RACE_CLASS,
    /** New character: waiting for pronoun selection. */
    AWAITING_PRONOUNS,
    /** New character: waiting for optional character description. */
    AWAITING_DESCRIPTION,
    /** Fully authenticated — player is active in the world. */
    PLAYING,
    /** Player typed logout; waiting for yes/no confirmation before disconnecting. */
    LOGOUT_CONFIRM,
    /** Connection closed; terminal state. */
    DISCONNECTED
}
