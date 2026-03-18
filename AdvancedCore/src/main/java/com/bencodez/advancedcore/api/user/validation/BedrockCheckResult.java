package com.bencodez.advancedcore.api.user.validation;

public class BedrockCheckResult {

    private final boolean bedrock;
    private final boolean trusted;
    private final String normalizedName;
    private final String reason;

    public BedrockCheckResult(boolean bedrock, boolean trusted, String normalizedName, String reason) {
        this.bedrock = bedrock;
        this.trusted = trusted;
        this.normalizedName = normalizedName;
        this.reason = reason;
    }

    public boolean isBedrock() {
        return bedrock;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getReason() {
        return reason;
    }
}
