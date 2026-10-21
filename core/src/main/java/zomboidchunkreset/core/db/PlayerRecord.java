package zomboidchunkreset.core.db;

import zomboidchunkreset.core.model.ChunkPos;

/**
 * A player read from {@code players.db}. This program never modifies that database.
 *
 * @param source        table the player comes from
 * @param accountName   account name ({@code networkPlayers.username}); {@code null} in single-player saves
 * @param characterName character name ({@code name} column), may be {@code null}
 * @param x             in-game X (square coordinate)
 * @param y             in-game Y (square coordinate)
 * @param z             level
 * @param dead          {@code isDead} column
 * @param storedChunk   chunk stored in the {@code wx}/{@code wy} columns ({@code localPlayers} only),
 *                      {@code null} when the table has no such columns
 */
public record PlayerRecord(Source source, String accountName, String characterName,
                           double x, double y, double z, boolean dead, ChunkPos storedChunk) {

    /** Table a player record was read from. */
    public enum Source {
        /** {@code localPlayers}: used by single-player saves. */
        LOCAL,
        /** {@code networkPlayers}: used by multiplayer server saves. */
        NETWORK
    }

    /** The chunk containing the player's position. */
    public ChunkPos chunk() {
        return ChunkPos.ofSquare(x, y);
    }

    /** A readable name: the account name when known, otherwise the character name. */
    public String displayName() {
        if (accountName != null && !accountName.isBlank()) {
            return accountName;
        }
        return characterName != null ? characterName : "?";
    }
}
