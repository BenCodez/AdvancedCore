package com.bencodez.advancedcore.bungeeapi.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public abstract class RedisHandler {
	private final JedisPool publishPool;
	private final JedisPool subscribePool;

	public RedisHandler(String host, int port, String username, String password) {
		if (username.isEmpty() && password.isEmpty()) {
			publishPool = new JedisPool(host, port);
			subscribePool = new JedisPool(host, port);
		} else if (username.isEmpty()) {
			publishPool = new JedisPool(host, port, null, password);
			subscribePool = new JedisPool(host, port, null, password);
		} else {
			publishPool = new JedisPool(host, port, username, password);
			subscribePool = new JedisPool(host, port, username, password);
		}
	}

	public void close() {
		publishPool.close();
		subscribePool.close();
	}

	public abstract void debug(String message);

	public void loadListener(RedisListener listener) {
		try (Jedis jedis = subscribePool.getResource()) {
			jedis.subscribe(listener, listener.getChannel());
		}
	}

	protected abstract void onMessage(String channel, String[] message);

	public void sendMessage(String channel, String... message) {
		String str = String.join(":", message);
		try (Jedis jedis = publishPool.getResource()) {
			debug("Redis Send: " + channel + ", " + str);
			jedis.publish(channel, str);
		}
	}
}
