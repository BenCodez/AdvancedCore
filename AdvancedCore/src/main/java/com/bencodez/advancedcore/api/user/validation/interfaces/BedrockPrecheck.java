package com.bencodez.advancedcore.api.user.validation.interfaces;

import com.bencodez.advancedcore.api.user.validation.BedrockCheckResult;

public interface BedrockPrecheck {
    BedrockCheckResult check(String name);
}
