package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.bencodez.simpleapi.sql.DataType;

class SqlUserSchemaReservedColumnTest {
    @Test
    void canonicalUuidCannotBeOverriddenByAnyCaseAlias() {
        for (String alias : new String[] { "uuid", "UUID", "Uuid", "uUiD" }) {
            assertThrows(IllegalArgumentException.class,
                    () -> SqlUserSchema.builder().column(alias, "TEXT", DataType.STRING));
        }
        SqlUserSchema schema = SqlUserSchema.builder().column("Points", "INT DEFAULT '0'", DataType.INTEGER).build();
        assertEquals("uuid", schema.columns().get(0).name());
        assertEquals("VARCHAR(37)", schema.columns().get(0).sqlType());
    }
}
