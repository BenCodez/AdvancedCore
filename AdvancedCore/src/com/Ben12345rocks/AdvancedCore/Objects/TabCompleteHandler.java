package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.ArrayList;
import java.util.HashMap;

import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

public class TabCompleteHandler {
	static TabCompleteHandler instance = new TabCompleteHandler();

	public static TabCompleteHandler getInstance() {
		return instance;
	}

	// private AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	private ArrayList<TabCompleteHandle> tabCompletes;

	public void addTabCompleteOption(TabCompleteHandle handle) {
		tabCompletes.add(handle);
		loadTabCompleteOptions();
	}

	public ArrayList<String> getTabCompleteReplaces() {
		ArrayList<String> list = new ArrayList<String>();
		for (TabCompleteHandle h : tabCompletes) {
			list.add(h.getToReplace());
		}
		return list;
	}

	public void loadTabCompleteOptions() {
		for (TabCompleteHandle h : tabCompletes) {
			h.updateReplacements();
		}
		tabCompleteOptions.clear();
		for (TabCompleteHandle h : tabCompletes) {
			tabCompleteOptions.put(h.getToReplace(), h.getReplace());
		}
	}

	private HashMap<String, ArrayList<String>> tabCompleteOptions = new HashMap<String, ArrayList<String>>();

	public HashMap<String, ArrayList<String>> getTabCompleteOptions() {
		return tabCompleteOptions;
	}

	public void addTabCompleteOption(String toReplace, ArrayList<String> options) {
		addTabCompleteOption(new TabCompleteHandle(toReplace, options) {

			@Override
			public void updateReplacements() {
			}
		});
	}

	public void addTabCompleteOption(String toReplace, String... options) {
		addTabCompleteOption(toReplace, ArrayUtils.getInstance().convert(options));
	}

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
		}

		public abstract void updateReplacements();
	}
}
