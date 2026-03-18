package com.bencodez.advancedcore.api.user.validation;

import com.bencodez.advancedcore.api.user.validation.interfaces.*;

public class UserValidationService {

	private final OnlinePlayerLookup online;
	private final StoredUserLookup storage;
	private final ServerHistoryLookup serverHistory;
	private final BedrockPrecheck bedrock;

	public UserValidationService(OnlinePlayerLookup online, StoredUserLookup storage, ServerHistoryLookup serverHistory,
			BedrockPrecheck bedrock) {
		this.online = online;
		this.storage = storage;
		this.serverHistory = serverHistory;
		this.bedrock = bedrock;
	}

	public UserValidationResult validate(String inputName, boolean checkServerHistory) {
		if (inputName == null) {
			return new UserValidationResult(ValidationStatus.INVALID, "", ValidationSource.UNKNOWN, "null-name", false);
		}

		String name = inputName.trim();
		if (name.isEmpty()) {
			return new UserValidationResult(ValidationStatus.INVALID, "", ValidationSource.UNKNOWN, "empty-name",
					false);
		}

		if (online.isOnlineExact(name)) {
			return new UserValidationResult(ValidationStatus.VALID, name, ValidationSource.ONLINE_PLAYER,
					"online-player", false);
		}

		if (storage.userExistsStored(name)) {
			return new UserValidationResult(ValidationStatus.VALID, name, ValidationSource.STORAGE, "stored-user",
					false);
		}

		var bedrockResult = bedrock.check(name);

		if (bedrockResult.isBedrock() && bedrockResult.isTrusted()) {
			return new UserValidationResult(ValidationStatus.VALID, bedrockResult.getNormalizedName(),
					ValidationSource.BEDROCK_TRUSTED, bedrockResult.getReason(), true);
		}

		if (checkServerHistory && serverHistory.hasJoinedBefore(name)) {
			return new UserValidationResult(ValidationStatus.VALID, name, ValidationSource.SERVER_HISTORY,
					"server-history", false);
		}

		if (bedrockResult.isBedrock()) {
			return new UserValidationResult(ValidationStatus.INVALID, bedrockResult.getNormalizedName(),
					ValidationSource.BEDROCK_UNTRUSTED, "untrusted-bedrock", true);
		}

		return new UserValidationResult(ValidationStatus.INVALID, name, ValidationSource.UNKNOWN, "unknown-user",
				false);
	}
}
