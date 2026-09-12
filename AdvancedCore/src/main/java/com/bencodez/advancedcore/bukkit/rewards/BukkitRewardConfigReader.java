package com.bencodez.advancedcore.bukkit.rewards;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.core.rewards.RewardConfigReader;
import com.bencodez.simpleapi.bukkit.config.BukkitStructuredConfigView;

/**
 * Keeps native Bukkit getter/default and raw-list behavior while the reward
 * paths and interpretation live in the shared reader. It deliberately does not
 * add a case-insensitive wrapper: the supplied section already owns that policy.
 */
public final class BukkitRewardConfigReader extends RewardConfigReader {
    private final Supplier<? extends ConfigurationSection> source;

    public BukkitRewardConfigReader(Supplier<? extends ConfigurationSection> source) {
        super(() -> new BukkitStructuredConfigView(source.get()));
        this.source = Objects.requireNonNull(source, "source");
    }

    private ConfigurationSection section() {
        return Objects.requireNonNull(source.get(), "configuration");
    }

    // Explicit fallback overloads do not have the same default-tree behavior
    // as these legacy no-fallback Bukkit getters.
    @Override
    protected boolean readBoolean(String path) {
        return section().getBoolean(path);
    }

    @Override
    protected int readInt(String path) {
        return section().getInt(path);
    }

    @Override
    protected double readDouble(String path) {
        return section().getDouble(path);
    }

    @Override
    protected boolean isList(String path) {
        return section().isList(path);
    }

    @Override
    protected List<?> readList(String path) {
        // No copying, filtering, coercion or change to the legacy ArrayList
        // cast in RewardFileData. The empty fallback is fresh on every read.
        return section().getList(path, new ArrayList<>());
    }
}
