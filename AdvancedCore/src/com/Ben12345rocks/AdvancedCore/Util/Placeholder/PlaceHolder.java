package com.Ben12345rocks.AdvancedCore.Util.Placeholder;

import org.bukkit.OfflinePlayer;

import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

import lombok.Getter;

public abstract class PlaceHolder<T> {
	@Getter
	private String identifier;
	@Getter
	private boolean useStartsWith = false;
	@Getter
	private String description;

	public PlaceHolder(String identifier) {
		this.identifier = identifier;
	}

	public PlaceHolder(String identifier, boolean useStartsWith) {
		this.identifier = identifier;
		this.useStartsWith = useStartsWith;
	}

	public boolean hasDescription() {
		return description != null;
	}

	public boolean matches(String identifier) {
		if (isUseStartsWith()) {
			if (StringUtils.getInstance().startsWithIgnoreCase(identifier, getIdentifier())) {
				return true;
			}
		} else {
			if (getIdentifier().equalsIgnoreCase(identifier)) {
				return true;
			}
		}
		return false;
	}

	public abstract String placeholderRequest(OfflinePlayer p, T user, String identifier);

	public PlaceHolder<T> useStartsWith() {
		useStartsWith = true;
		return this;
	}

	public PlaceHolder<T> withDescription(String desc) {
		description = desc;
		return this;
	}

}
