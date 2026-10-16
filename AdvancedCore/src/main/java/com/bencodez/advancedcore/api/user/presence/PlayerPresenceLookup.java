package com.bencodez.advancedcore.api.user.presence;

import java.util.UUID;

/**
 * A read-only lookup of an already resolved identity on a persistence worker.
 * Implementations must return UNKNOWN for incomplete identity/history evidence.
 * Absence from a plugin's storage is insufficient evidence for NEVER_JOINED.
 */
@FunctionalInterface
public interface PlayerPresenceLookup {
    PlayerPresence check(UUID identity) throws Exception;

    /**
     * Additionally certify absence of unmapped offline history for an online identity.
     * Older providers retain positive evidence but cannot certify the wider scope.
     */
    default PlayerPresence checkOnlineHistory(UUID identity) throws Exception {
        PlayerPresence evidence = check(identity);
        return evidence == PlayerPresence.NEVER_JOINED ? PlayerPresence.UNKNOWN : evidence;
    }
}
