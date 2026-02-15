package com.bencodez.advancedcore.bungeeapi.globaldata;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.api.time.TimeType;

import lombok.Getter;
import lombok.Setter;

/**
 * Proxy handler for global data with time change support.
 */
public abstract class GlobalDataHandlerProxy extends GlobalDataHandler {

	/**
	 * @return whether time changed has happened
	 * @param timeChangedHappened whether time changed has happened
	 */
	@Getter
	@Setter
	private boolean timeChangedHappened = false;

	/**
	 * @return the list of time changes
	 */
	@Getter
	private ArrayList<TimeType> timeChanges = new ArrayList<>();

	/**
	 * @return the time changed timer
	 */
	@Getter
	private ScheduledExecutorService timeChangedTimer;

	private ArrayList<String> servers;

	private GlobalMySQL globalMysql;

	/**
	 * Constructor for GlobalDataHandlerProxy.
	 *
	 * @param globalMysql the global MySQL instance
	 * @param servers the list of servers
	 */
	public GlobalDataHandlerProxy(GlobalMySQL globalMysql, ArrayList<String> servers) {
		super(globalMysql);
		timeChangedTimer = Executors.newScheduledThreadPool(1);
		this.servers = servers;
		this.globalMysql = globalMysql;
		timeChangedTimer.schedule(new Runnable() {

			@Override
			public void run() {
				if (!timeChangedHappened) {
					for (String server : servers) {
						// boolean b = getBoolean(server, "FinishedProcessing");
						boolean processing = getBoolean(server, "Processing");
						boolean day = getBoolean(server, "DAY");
						boolean week = getBoolean(server, "WEEK");
						boolean month = getBoolean(server, "MONTH");
						if (processing) {
							if (day) {
								timeChanges.add(TimeType.DAY);
							}
							if (week) {
								timeChanges.add(TimeType.WEEK);
							}
							if (month) {
								timeChanges.add(TimeType.MONTH);
							}
							globalMysql.debugLog(
									"Detected time change that may have been before server start, finishing...");
							timeChangedHappened = true;
							return;
						}

					}
				}
			}
		}, 30, TimeUnit.SECONDS);
		timeChangedTimer.scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				checkForFinishedTimeChanges();
			}
		}, 60, 10, TimeUnit.SECONDS);
	}
	
	/**
	 * Shuts down the time changed timer.
	 */
	public void shutdown() {
		timeChangedTimer.shutdownNow();
	}

	/**
	 * Checks for finished time changes across servers.
	 */
	public void checkForFinishedTimeChanges() {
		try {
			if (timeChangedHappened) {
				globalMysql.info("Checking if backend servers completed time change...");
				for (String server : servers) {
					boolean b = getBoolean(server, "FinishedProcessing");
					if (!b) {

						boolean processing = getBoolean(server, "Processing");
						globalMysql.debugLog("Server " + server
								+ " hasn't finished processing time change yet, processing: " + processing);
						try {
							String str = getString(server, "LastUpdated");
							long lastUpdated = 0;
							if (!str.isEmpty()) {
								lastUpdated = Long.valueOf(str).longValue();
							}
							if (processing) {
								// 2 hours
								if (LocalDateTime.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
										- lastUpdated <= 1000 * 60 * 60 * 2) {
									return;
								}
								globalMysql.warning(
										"Been too long, either something happened or server is offline finishing time change anyway, server: "
												+ server);
								failedProcess(server);
							} else {
								// 30 minutes of plugin not processing time change
								if (LocalDateTime.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
										- lastUpdated <= 1000 * 60 * 30) {
									return;
								}
								globalMysql.warning(
										"Server must be offline, skipping time change on this specific server: "
												+ server);
								failedProcess(server);
							}

						} catch (NumberFormatException e) {
							e.printStackTrace();
							return;
						}
						return;
					}
				}
				globalMysql.debugLog("Finishing up time change processing...");
				timeChangedHappened = false;
				for (TimeType time : timeChanges) {
					globalMysql.debugLog("Time changed finished on all servers: " + time.toString());
					onTimeChangedFinished(time);
				}
				timeChanges.clear();

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void failedProcess(String server) {
		for (TimeType time : timeChanges) {
			onTimeChangedFailed(server, time);
		}
	}

	/**
	 * Called when a time change occurs.
	 *
	 * @param type the time type
	 */
	public void onTimeChange(TimeType type) {
		timeChangedHappened = true;
		timeChanges.add(type);
	}

	/**
	 * Called when a time change failed on a server.
	 *
	 * @param server the server name
	 * @param type the time type
	 */
	public abstract void onTimeChangedFailed(String server, TimeType type);

	/**
	 * Called when a time change finished successfully.
	 *
	 * @param type the time type
	 */
	public abstract void onTimeChangedFinished(TimeType type);

}
