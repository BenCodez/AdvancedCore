package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.ArrayList;

public abstract class TabCompleteHandle {
	private String toReplace;
	private ArrayList<String> replace;

	public String getToReplace() {
		return toReplace;
	}

	public void setToReplace(String toReplace) {
		this.toReplace = toReplace;
	}

	public ArrayList<String> getReplace() {
		return replace;
	}

	public void setReplace(ArrayList<String> replace) {
		this.replace = replace;
	}

	public TabCompleteHandle(String toReplace, ArrayList<String> replace) {
		this.toReplace = toReplace;
		this.replace = replace;
	}

	public abstract void updateReplacements();

	public abstract void reload();
}
