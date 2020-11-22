package com.bencodez.advancedcore.api.misc.animation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class AnimationAPI {
	public interface Animation {
		public void onAnimate(String text);
	}

	private static AnimationAPI instance = new AnimationAPI();

	public static AnimationAPI getInstance() {
		return instance;
	}

	private AnimationAPI() {
	}

	/**
	 * Run an animation
	 *
	 * @param texts       Text for animation
	 * @param timeBetween Time between text updates
	 * @param animation   Method to run to display text
	 */
	public void runAnimation(ArrayList<String> texts, long timeBetween, final Animation animation) {
		Iterator<String> it = texts.iterator();
		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				if (it.hasNext()) {
					animation.onAnimate(it.next());
				} else {
					cancel();
				}

			}
		}, 0, timeBetween);

	}
}
