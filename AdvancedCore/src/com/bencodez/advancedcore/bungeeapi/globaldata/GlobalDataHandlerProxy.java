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

public abstract class GlobalDataHandlerProxy extends GlobalDataHandler {

	@Getter
	@Setter
	private boolean timeChangedHappened = false;

	@Getter
	private ArrayList<TimeType> timeChanges = new ArrayList<TimeType>();

	@Getter
	private ScheduledExecutorService timeChangedTimer;

	public void onTimeChange(TimeType type) {
		timeChangedHappened = true;
		timeChanges.add(type);
	}

	private void failedProcess(String server) {
		timeChangedHappened = false;
		for (TimeType time : timeChanges) {
			onTimeChangedFailed(server, time);
		}
		timeChanges.clear();
	}

	public GlobalDataHandlerProxy(GlobalMySQL globalMysql, ArrayList<String> servers) {
		super(globalMysql);
		timeChangedTimer = Executors.newScheduledThreadPool(1);
		timeChangedTimer.scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				if (timeChangedHappened) {
					for (String server : servers) {
						boolean b = getBoolean(server, "FinishedProcessing");
						if (!b) {

							boolean processing = getBoolean(server, "Processing");
							globalMysql.debug("Server " + server
									+ " hasn't finished processing time change yet, processing: " + processing);
							try {
								long lastUpdated = Long.valueOf(getString(server, "LastUpdated")).longValue();
								if (processing) {
									// 2 hours
									if (LocalDateTime.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
											- lastUpdated > 1000 * 60 * 60 * 2) {
										globalMysql.warning(
												"Been too long, either something happened or server is offline finishing time change anyway, server: "
														+ server);
										failedProcess(server);
									} else {
										return;
									}
								} else {
									// 30 minutes of plugin not processing time change
									if (LocalDateTime.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
											- lastUpdated > 1000 * 60 * 30) {
										globalMysql.warning(
												"Server must be offline, skipping time change on this specific server: "
														+ server);
										failedProcess(server);
									} else {
										return;
									}
								}

							} catch (NumberFormatException e) {
								e.printStackTrace();
								return;
							}
						}
					}
					timeChangedHappened = false;
					for (TimeType time : timeChanges) {
						globalMysql.debug("Time changed finished on all servers: " + time.toString());
						onTimeChangedFinished(time);
					}
					timeChanges.clear();
				}
			}
		}, 30, 10, TimeUnit.SECONDS);
	}

	public abstract void onTimeChangedFinished(TimeType type);

	public abstract void onTimeChangedFailed(String server, TimeType type);

}
