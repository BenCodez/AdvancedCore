package com.Ben12345rocks.AdvancedCore.CommandAPI;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

public abstract class TabCompleteHandle {
	@Getter
	@Setter
	private String toReplace;
	@Getter
	@Setter
	private ArrayList<String> replace;

	public TabCompleteHandle(String toReplace, ArrayList<String> replace) {
		this.toReplace = toReplace;
		this.replace = replace;
	}

	public abstract void reload();

	public abstract void updateReplacements();
}
