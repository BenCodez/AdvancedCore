package com.bencodez.advancedcore.scheduler;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

@SuppressWarnings("deprecation")
public class BukkitScheduler {

    public static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
    }

    public static void executeOrScheduleSync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            if (Bukkit.isGlobalTickThread()) {
                task.run();
            } else {
                Bukkit.getGlobalRegionScheduler().run(plugin, st -> task.run());
            }
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    public static void executeOrScheduleSync(Plugin plugin, Runnable task, Entity entity) {
        if (FOLIA) {
            if (Bukkit.isOwnedByCurrentRegion(entity)) {
                task.run();
            } else {
                entity.getScheduler().run(plugin, st -> task.run(), null);
            }
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

	public static void executeOrScheduleSync(Plugin plugin, Runnable task, Location location) {
        if (FOLIA) {
            if (Bukkit.isOwnedByCurrentRegion(location)) {
                task.run();
            } else {
                Bukkit.getRegionScheduler().execute(plugin, location, task);
            }
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    public static ScheduledTask runTask(Plugin plugin, Runnable task, Entity entity) {
        if (FOLIA) {
            return new ScheduledTask(entity.getScheduler().run(plugin, st -> task.run(), null));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTask(plugin, task));
        }
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delay, Entity entity) {
        if (FOLIA) {
            return new ScheduledTask(entity.getScheduler().runDelayed(plugin, st -> task.run(), null, delay));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
        }
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period, Entity entity) {
        if (FOLIA) {
            return new ScheduledTask(entity.getScheduler().runAtFixedRate(plugin, st -> task.run(), null, Math.max(1, delay), period));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
        }
    }

    public static ScheduledTask runTask(Plugin plugin, Runnable task, Location location) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getRegionScheduler().run(plugin, location, st -> task.run()));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTask(plugin, task));
        }
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delay, Location location) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getRegionScheduler().runDelayed(plugin, location, st -> task.run(), delay));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
        }
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period, Location location) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, st -> task.run(), Math.max(1, delay), period));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
        }
    }

    public static ScheduledTask runTask(Plugin plugin, Runnable task) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getGlobalRegionScheduler().run(plugin, st -> task.run()));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTask(plugin, task));
        }
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, st -> task.run(), delay));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
        }
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, st -> task.run(), Math.max(1, delay), period));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
        }
    }

    public static ScheduledTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getAsyncScheduler().runNow(plugin, st -> task.run()));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        }
    }

    public static ScheduledTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getAsyncScheduler().runDelayed(plugin, st -> task.run(), delay * 50, TimeUnit.MILLISECONDS));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay));
        }
    }

    public static ScheduledTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        if (FOLIA) {
            return new ScheduledTask(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, st -> task.run(), Math.max(1, delay * 50), period * 50, TimeUnit.MILLISECONDS));
        } else {
            return new ScheduledTask(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period));
        }
    }

    public static class ScheduledTask {

        private final Object task;

        public ScheduledTask(Object task) {
            this.task = task;
        }

        public boolean isCancelled() {
            if (FOLIA) {
                return ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).isCancelled();
            } else {
                return ((BukkitTask) task).isCancelled();
            }
        }

        public void cancel() {
            if (FOLIA) {
                ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).cancel();
            } else {
                ((BukkitTask) task).cancel();
            }
        }

        public Plugin getOwner() {
            if (FOLIA) {
                return ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).getOwningPlugin();
            } else {
                return ((BukkitTask) task).getOwner();
            }
        }

    }

}