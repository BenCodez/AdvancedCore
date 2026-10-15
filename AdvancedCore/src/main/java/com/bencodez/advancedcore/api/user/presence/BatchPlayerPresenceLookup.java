package com.bencodez.advancedcore.api.user.presence;

import java.util.List;
import java.util.UUID;

/** Internal cohort optimization; older/custom public lookup implementations remain compatible. */
interface BatchPlayerPresenceLookup extends PlayerPresenceLookup {
    record Query(UUID identity, boolean completeOnlineHistory) { }
    List<PlayerPresence> checkBatch(List<Query> queries) throws Exception;
}
