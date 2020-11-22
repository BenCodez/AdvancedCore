package com.bencodez.advancedcore.api.valuerequest.listeners;

import org.bukkit.entity.Player;

public abstract class Listener<T> {

	public abstract void onInput(Player player, T value);

}
