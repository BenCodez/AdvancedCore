package com.bencodez.advancedcore.api.misc.animation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

/**
 * API for running text animations.
 */
public class AnimationAPI {
	/**
	 * Interface for animation callbacks.
	 */
	public interface Animation {
		/**
		 * Called on each animation frame.
		 * 
		 * @param text the text to display
		 */
		public void onAnimate(String text);
	}

	private static AnimationAPI instance = new AnimationAPI();

	/**
	 * Gets the singleton instance.
	 * 
	 * @return the animation API instance
	 */
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
