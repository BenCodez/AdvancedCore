package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.VaultHandler;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

@Timeout(10)
class MoneyRewardIdentityTest {
    @Test void depositWithoutNameRemainsPending() throws Exception { missing(2, null, false); }
    @Test void withdrawalWithoutNameRemainsPending() throws Exception { missing(-2, null, false); }
    @Test void blankDepositRemainsPending() throws Exception { missing(2, "", false); }
    @Test void blankWithdrawalRemainsPending() throws Exception { missing(-2, " ", false); }
    @Test void depositRechecksIdentityAtDispatch() throws Exception { missing(2, null, true); }
    @Test void withdrawalRechecksIdentityAtDispatch() throws Exception { missing(-2, null, true); }
    @Test void validDepositKeepsAmountAndCompletesOnce() throws Exception { healthy(2); }
    @Test void validWithdrawalKeepsAmountAndCompletesOnce() throws Exception { healthy(-2); }

    private void missing(double amount, String name, boolean lostAfterScheduling) throws Exception {
        try (Fixture f = new Fixture()) {
            f.name.set(lostAfterScheduling ? "Synthetic" : name);
            var completion = f.schedule(amount);
            assertFalse(completion.isDone());
            if (lostAfterScheduling) f.name.set(name);
            f.dispatch();
            var failed = assertThrows(ExecutionException.class, () -> completion.get(1, TimeUnit.SECONDS));
            assertTrue(AdvancedCoreUser.isReplayActionNotStarted(failed));
            verifyNoInteractions(f.economy);
            assertNull(Reward.replayMetadata(f.placeholders, f.state, Reward.legacyActionReplayKey("money-test")),
                    "no money action may be checkpointed without an account");

            f.name.set("Synthetic");
            var retry = f.schedule(amount); f.dispatch(); retry.get(1, TimeUnit.SECONDS);
            f.verifyPayment(amount);
            assertNotNull(Reward.replayMetadata(f.placeholders, f.state, Reward.legacyActionReplayKey("money-test")));
            var replay = f.schedule(amount); replay.get(1, TimeUnit.SECONDS);
            assertTrue(f.tasks.isEmpty(), "completed action must not be dispatched again");
            f.verifyPayment(amount);
        }
    }

    private void healthy(double amount) throws Exception {
        try (Fixture f = new Fixture()) {
            var completion = f.schedule(amount); f.dispatch(); completion.get(1, TimeUnit.SECONDS);
            f.verifyPayment(amount);
            assertNotNull(Reward.replayMetadata(f.placeholders, f.state, Reward.legacyActionReplayKey("money-test")));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final Economy economy = mock(Economy.class);
        final OfflinePlayer account = mock(OfflinePlayer.class);
        final AdvancedCoreUser user;
        final Reward.ReplayState state = Reward.replayStateFor(new RewardOptions());
        final HashMap<String, String> placeholders = new HashMap<>();
        final AtomicReference<String> name = new AtomicReference<>("Synthetic");
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);

        Fixture() {
            var config = mock(AdvancedCoreConfigOptions.class);
            when(config.isOnlineMode()).thenReturn(true); when(plugin.getOptions()).thenReturn(config);
            when(plugin.isEnabled()).thenReturn(true);
            var vault = mock(VaultHandler.class); when(vault.getEcon()).thenReturn(economy); when(plugin.getVaultHandler()).thenReturn(vault);
            var scheduler = mock(BukkitScheduler.class); when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            doAnswer(call -> { tasks.add(call.getArgument(1)); return null; }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
            UUID id = UUID.randomUUID(); user = spy(new AdvancedCoreUser(plugin, id, false, false));
            doAnswer(call -> name.get()).when(user).getPlayerName();
            bukkit.when(() -> Bukkit.getOfflinePlayer(id)).thenReturn(account);
            when(economy.depositPlayer(nullable(OfflinePlayer.class), anyDouble())).thenAnswer(call -> response(call.getArgument(0)));
            when(economy.withdrawPlayer(nullable(OfflinePlayer.class), anyDouble())).thenAnswer(call -> response(call.getArgument(0)));
        }
        private EconomyResponse response(OfflinePlayer player) {
            return new EconomyResponse(2, 5, player == null ? EconomyResponse.ResponseType.FAILURE : EconomyResponse.ResponseType.SUCCESS,
                    player == null ? "account unavailable" : null);
        }
        CompletableFuture<Void> schedule(double amount) {
            var collection = user.beginAsyncActionCollection(state, placeholders, "money-test");
            user.giveMoney(amount);
            return user.endAsyncActionCollection(collection).toCompletableFuture();
        }
        void dispatch() { assertFalse(tasks.isEmpty()); tasks.remove().run(); }
        void verifyPayment(double amount) {
            if (amount > 0) { verify(economy).depositPlayer(account, amount); verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble()); }
            else { verify(economy).withdrawPlayer(account, -amount); verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble()); }
        }
        public void close() { tasks.clear(); bukkit.close(); }
    }
}
