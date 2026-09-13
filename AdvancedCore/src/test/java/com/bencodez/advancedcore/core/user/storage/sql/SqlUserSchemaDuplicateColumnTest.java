package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.simpleapi.sql.DataType;

class SqlUserSchemaDuplicateColumnTest {
    @Test void builderRejectsCaseFoldedDuplicateNamesInsteadOfSilentlyOverwriting() {
        var builder = SqlUserSchema.builder().column("Points", "INTEGER", DataType.INTEGER);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> builder.column("points", "VARCHAR(30)", DataType.STRING));
        assertTrue(failure.getMessage().contains("Points"));
    }

    @Test void fromKeysRejectsCaseFoldedDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> SqlUserSchema.fromKeys(List.of(
                new UserDataKeyInt("Votes"), new UserDataKeyInt("votes"))));
    }

    @Test void uniqueColumnsKeepTheirOriginalSpelling() {
        SqlUserSchema schema = SqlUserSchema.builder().column("Points", "INTEGER", DataType.INTEGER).build();
        assertEquals("Points", schema.column("points").name());
    }
}
