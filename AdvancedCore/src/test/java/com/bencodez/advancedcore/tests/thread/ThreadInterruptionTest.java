package com.bencodez.advancedcore.tests.thread;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.thread.FileThread;

public class ThreadInterruptionTest {

	@Test
	public void fileReadThreadStopsWhenInterrupted() throws InterruptedException {
		FileThread.ReadThread readThread = FileThread.getInstance().new ReadThread();

		readThread.start();
		readThread.interrupt();
		readThread.join(1_000L);

		assertFalse(readThread.isAlive());
	}

	@Test
	@SuppressWarnings("deprecation")
	public void legacyReadThreadStopsWhenInterrupted() throws InterruptedException {
		com.bencodez.advancedcore.thread.Thread.ReadThread readThread = com.bencodez.advancedcore.thread.Thread
				.getInstance().new ReadThread();

		readThread.start();
		readThread.interrupt();
		readThread.join(1_000L);

		assertFalse(readThread.isAlive());
	}
}
