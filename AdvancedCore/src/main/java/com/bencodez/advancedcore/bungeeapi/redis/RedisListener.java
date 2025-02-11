package com.bencodez.advancedcore.bungeeapi.redis;

import java.util.regex.Pattern;

import lombok.Getter;
import redis.clients.jedis.JedisPubSub;

public class RedisListener extends JedisPubSub {
	private RedisHandler redisHandler;
	@Getter
	private String channel;

	public RedisListener(RedisHandler redisHandler, String channel) {
		this.redisHandler = redisHandler;
		this.channel = channel;
	}

	public void onMessage(String channel, String message) {
		redisHandler.debug("Redis Message: " + channel + "," + message);
		if (channel.equals(this.channel)) {
			redisHandler.onMessage(channel, message.split(Pattern.quote(":")));
		}
	}

	public void onPMessage(String pattern, String channel, String message) {
	}

	public void onPSubscribe(String pattern, int subscribedChannels) {
	}

	public void onPUnsubscribe(String pattern, int subscribedChannels) {
	}

	public void onSubscribe(String channel, int subscribedChannels) {
	}

	public void onUnsubscribe(String channel, int subscribedChannels) {
	}
}
