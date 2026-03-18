package com.bencodez.advancedcore.api.user.validation.impl;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.bedrock.BedrockNameResolver;
import com.bencodez.advancedcore.api.user.validation.BedrockCheckResult;
import com.bencodez.advancedcore.api.user.validation.interfaces.BedrockPrecheck;

public class AdvancedCoreBedrockPrecheck implements BedrockPrecheck {

	private final AdvancedCorePlugin plugin;

	public AdvancedCoreBedrockPrecheck(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public BedrockCheckResult check(String name) {
		if (name == null || name.isEmpty()) {
			return new BedrockCheckResult(false, false, "", "empty-name");
		}

		BedrockNameResolver.Result result = plugin.getBedrockHandle().resolveWithoutDb(name);

		boolean trusted = !"prefixed-only".equals(result.rationale)
				&& !"unknown-no-db".equals(result.rationale);

		return new BedrockCheckResult(result.isBedrock, trusted, result.finalName, result.rationale);
	}
}