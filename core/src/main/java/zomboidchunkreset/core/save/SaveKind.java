package zomboidchunkreset.core.save;

/** Whether a save comes from a single-player game or a multiplayer server. */
public enum SaveKind {

    /** Single-player save ({@code Zomboid/Saves/<game mode>/<save name>/}). */
    SINGLE_PLAYER,

    /** Multiplayer server save ({@code Zomboid/Saves/Multiplayer/<server name>/}). */
    MULTIPLAYER_SERVER,

    /** No signal, or contradictory signals. The chunk layout is the same for both kinds. */
    UNKNOWN
}
