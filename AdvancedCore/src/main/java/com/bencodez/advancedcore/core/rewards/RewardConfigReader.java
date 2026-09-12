package com.bencodez.advancedcore.core.rewards;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.bencodez.simpleapi.core.config.StructuredConfigView;
import com.bencodez.simpleapi.core.config.StructuredConfigView.Kind;

/**
 * Platform-neutral interpretation of reward settings. This class neither loads
 * files nor executes rewards. The supplied view owns casing, paths and typed
 * conversion rules; wrap it in SimpleAPI's case-insensitive view when required.
 *
 * <p>The supplier is evaluated on reads, not construction, so replacing a
 * configuration does not leave this reader bound to a stale section. A live
 * view is not made thread-safe by this reader.</p>
 */
public class RewardConfigReader {
    private final Supplier<? extends StructuredConfigView> source;

    public RewardConfigReader(Supplier<? extends StructuredConfigView> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    protected final StructuredConfigView config() {
        return Objects.requireNonNull(source.get(), "configuration");
    }

    /** Adapter seam for native getters with implicit default-tree semantics. */
    protected boolean readBoolean(String path) {
        return config().getBoolean(path, false);
    }

    protected int readInt(String path) {
        return config().getInt(path, 0);
    }

    protected double readDouble(String path) {
        return config().getDouble(path, 0);
    }

    protected boolean isList(String path) {
        return config().kind(path) == Kind.LIST;
    }

    /**
     * Plain-data lists retain order and value types, without string coercion or
     * filtering. The default implementation returns a detached, unmodifiable
     * list. A native compatibility adapter may preserve its existing live list.
     */
    protected List<?> readList(String path) {
        StructuredConfigView view = config();
        if (view.kind(path) != Kind.LIST) {
            return Collections.emptyList();
        }
        return (List<?>) view.value(path);
    }

    public double getChance() {
        return readDouble("Chance");
    }

    public Set<String> getChoices() {
        if (config().isConfigurationSection("Choices")) {
            return config().getConfigurationSection("Choices").getKeys(false);
        }
        return new HashSet<>();
    }

    public String getChoicesRewardsPath(String choice) {
        return "Choices." + choice + ".Rewards";
    }

    public List<?> getCommandsConsole() {
        if (isList("Commands")) {
            return readList("Commands");
        }
        return readList("Commands.Console");
    }

    public List<?> getCommandsPlayer() {
        return readList("Commands.Player");
    }

    public boolean getDelayedEnabled() {
        return readBoolean("Delayed.Enabled");
    }

    public int getDelayedHours() {
        return readInt("Delayed.Hours");
    }

    public int getDelayedMilliSeconds() {
        return readInt("Delayed.MilliSeconds");
    }

    public int getDelayedMinutes() {
        return readInt("Delayed.Minutes");
    }

    public int getDelayedSeconds() {
        return readInt("Delayed.Seconds");
    }

    public boolean getEnableChoices() {
        return readBoolean("EnableChoices");
    }

    public boolean getForceOffline() {
        return readBoolean("ForceOffline");
    }

    public String getPermission(String rewardName) {
        return config().getString("Permission", "AdvancedCore.Reward." + rewardName);
    }

    public List<?> getPriority() {
        return readList("Priority");
    }

    public boolean getRequirePermission() {
        return readBoolean("RequirePermission");
    }

    public String getRewardType() {
        String type = config().getString("RewardType", "BOTH");
        if ("online".equalsIgnoreCase(type)) {
            return "ONLINE";
        }
        if ("offline".equalsIgnoreCase(type)) {
            return "OFFLINE";
        }
        return "BOTH";
    }

    public String getServer() {
        return config().getString("Server", "");
    }

    public boolean getTimedEnabled() {
        return readBoolean("Timed.Enabled");
    }

    public int getTimedHour() {
        return readInt("Timed.Hour");
    }

    public int getTimedMinute() {
        return readInt("Timed.Minute");
    }

    public List<?> getWorlds() {
        return readList("Worlds");
    }

    public boolean isDirectlyDefinedReward() {
        return readBoolean("DirectlyDefinedReward");
    }

    /** Native objects remain in the adapter; these methods return read views. */
    public StructuredConfigView getChoicesItem(String choice) {
        return config().getConfigurationSection("Choices." + choice + ".DisplayItem");
    }

    public StructuredConfigView getChoiceRewards(String choice) {
        return config().getConfigurationSection(getChoicesRewardsPath(choice));
    }

    public StructuredConfigView getDisplayItem() {
        return config().getConfigurationSection("DisplayItem");
    }

    public StructuredConfigView getItemSection(String item) {
        return config().getConfigurationSection("Items." + item);
    }

    /** Literal keys allow definition names containing the path separator. */
    public StructuredConfigView definitionAt(String... keys) {
        return config().structuredAt(keys);
    }
}
