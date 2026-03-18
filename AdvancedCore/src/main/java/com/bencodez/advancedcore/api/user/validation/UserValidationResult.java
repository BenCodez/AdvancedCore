package com.bencodez.advancedcore.api.user.validation;

public class UserValidationResult {

    private final ValidationStatus status;
    private final String normalizedName;
    private final ValidationSource source;
    private final String reason;
    private final boolean bedrock;

    public UserValidationResult(ValidationStatus status,
                                String normalizedName,
                                ValidationSource source,
                                String reason,
                                boolean bedrock) {
        this.status = status;
        this.normalizedName = normalizedName;
        this.source = source;
        this.reason = reason;
        this.bedrock = bedrock;
    }

    public boolean isValid() {
        return status == ValidationStatus.VALID;
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public ValidationSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public boolean isBedrock() {
        return bedrock;
    }
}
