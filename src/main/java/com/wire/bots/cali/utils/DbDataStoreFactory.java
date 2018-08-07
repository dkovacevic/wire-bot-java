/*
 * Copyright (c) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wire.bots.cali.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.Maps;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import com.wire.bots.sdk.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;

/**
 * Thread-safe file implementation of a credential store.
 *
 * <p>
 * For security purposes, the file's permissions are set to be accessible only by the file's owner.
 * Note that Java 1.5 does not support manipulating file permissions, and must be done manually or
 * using the JNI.
 * </p>
 *
 * @author Yaniv Inbar
 * @since 1.16
 */
public class DbDataStoreFactory extends AbstractDataStoreFactory {

    private final Redis redis;
    private final String botId;

    public DbDataStoreFactory(Configuration.DB db, String botId) {
        this.redis = new Redis(db.host, db.port, db.password);
        this.botId = botId;
    }

    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) throws IOException {
        return new FileDataStore<>(this, String.format("%s_%s", id, botId));
    }

    /**
     * File data store that inherits from the abstract memory data store because the key-value pairs
     * are stored in a memory cache, and saved in the file (see {@link #save()} when changing values.
     *
     * @param <V> serializable type of the mapped value
     */
    class FileDataStore<V extends Serializable> extends AbstractMemoryDataStore<V> {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final DbDataStoreFactory dataStoreFactory;

        FileDataStore(DbDataStoreFactory dataStore, String id) throws IOException {
            super(dataStore, id);
            this.dataStoreFactory = dataStore;

            String value = redis.get(id);
            // create new file (if necessary)
            if (value == null) {
                keyValueMap = Maps.newHashMap();
                // save the credentials
                //Logger.info("Saving credentials for %s", id);
                save();
            } else {
                // load credentials
                //Logger.info("Loading credentials for %s", id);
                keyValueMap = objectMapper.readValue(value, new TypeReference<HashMap<String, byte[]>>() {
                });
            }
        }

        @Override
        void save() throws IOException {
            String s = objectMapper.writeValueAsString(keyValueMap);
            redis.put(getId(), s);
        }

        @Override
        public DbDataStoreFactory getDataStoreFactory() {
            return dataStoreFactory;
        }
    }

    static class Redis {
        private static final int TIMEOUT = 5000;
        private static JedisPool pool;
        private final String host;
        private final Integer port;
        private final String password;

        Redis(String host, int port, String password) {
            this.host = host;
            this.port = port;
            this.password = password;
        }

        private static JedisPoolConfig buildPoolConfig() {
            final JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(1100);
            poolConfig.setMaxIdle(16);
            poolConfig.setMinIdle(16);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
            poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
            poolConfig.setNumTestsPerEvictionRun(3);
            poolConfig.setBlockWhenExhausted(true);
            return poolConfig;
        }

        private static JedisPool pool(String host, Integer port, String password) {
            if (pool == null) {
                JedisPoolConfig poolConfig = buildPoolConfig();
                if (password != null && port != null)
                    pool = new JedisPool(poolConfig, host, port, TIMEOUT, password);
                else if (port != null)
                    pool = new JedisPool(poolConfig, host, port);
                else
                    pool = new JedisPool(poolConfig, host);

            }
            return pool;
        }

        String get(String id) {
            try (Jedis jedis = getConnection()) {
                String key = String.format("cali_%s", id);
                return jedis.get(key);
            }
        }

        void put(String id, String data) {
            try (Jedis jedis = getConnection()) {
                String key = String.format("cali_%s", id);
                jedis.set(key, data);
            }
        }

        private Jedis getConnection() {
            return pool(host, port, password).getResource();
        }

    }

}
