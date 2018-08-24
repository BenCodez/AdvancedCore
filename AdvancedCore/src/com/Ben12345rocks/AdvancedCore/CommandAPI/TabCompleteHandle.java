package com.Ben12345rocks.AdvancedCore.CommandAPI;

import java.util.ArrayList;

public abstract class TabCompleteHandle {
	private String toReplace;
	private ArrayList<String> replace;

	public TabCompleteHandle(String toReplace, ArrayList<String> replace) {
		this.toReplace = toReplace;
		this.replace = replace;
	}

	public ArrayList<String> getReplace() {
		return replace;
	}

	public String getToReplace() {
		return toReplace;
	}

	public abstract void reload();

	public void setReplace(ArrayList<String> replace) {
		this.replace = replace;
	}

	public void setToReplace(String toReplace) {
		this.toReplace = toReplace;
	}

	public abstract void updateReplacements();
}
