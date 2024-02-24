package com.bencodez.advancedcore.bungeeapi.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public abstract class RedisHandler {
	JedisPool jedisPool;

	public RedisHandler(String host, int port, String username, String password) {
		if (username.isEmpty() && password.isEmpty()) {
			jedisPool = new JedisPool(host, port);
		} else {
			jedisPool = new JedisPool(host, port, username, password);
		}
	}

	public void loadListener(RedisListener listener) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.subscribe(listener, listener.getChannel());
		}
	}

	public abstract void debug(String message);

	public void close() {
		jedisPool.close();
	}

	public void sendMessage(String channel, String... message) {
		String str = "";
		for (int i = 0; i < message.length; i++) {
			str += message[i];
			if (i < message.length - 1) {
				str += ":";
			}
		}
		try (Jedis jedis = jedisPool.getResource()) {
			debug("Redis Send: " + channel + ", " + str);
			jedis.publish(channel, str);
		}
	}

	protected abstract void onMessage(String channel, String[] message);

}
