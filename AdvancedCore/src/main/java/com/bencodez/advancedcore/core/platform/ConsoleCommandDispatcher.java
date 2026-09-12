package com.bencodez.advancedcore.core.platform;

import java.util.Objects;

/** The legacy console-list handoff policy, independent of Bukkit and reward storage. */
public final class ConsoleCommandDispatcher {
    private final PlatformServices platform;

    public ConsoleCommandDispatcher(PlatformServices platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    /**
     * Removes exactly one leading slash, then preserves the legacy stagger rule:
     * only a positive delay with staggering enabled uses the delayed scheduler.
     * Input has already been rendered by the caller. Null/empty lines are not
     * filtered here; native dispatch retains responsibility for rejecting them.
     * Returning from this method does not mean the command has executed.
     */
    public void dispatch(String command, long delaySeconds, boolean stagger) {
        String line = command != null && command.startsWith("/") ? command.substring(1) : command;
        Runnable dispatch = () -> platform.dispatchConsoleCommand(line);
        if (stagger && delaySeconds > 0) {
            platform.scheduler().runServerLater(dispatch, delaySeconds);
        } else {
            platform.scheduler().runServer(dispatch);
        }
    }
}
