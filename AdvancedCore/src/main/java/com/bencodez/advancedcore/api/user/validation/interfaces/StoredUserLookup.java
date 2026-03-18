package com.bencodez.advancedcore.api.user.validation.interfaces;

public interface StoredUserLookup {
    boolean userExistsStored(String name);
}
