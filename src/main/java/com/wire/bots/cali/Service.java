//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//

package com.wire.bots.cali;

import com.wire.bots.cali.resources.AuthResource;
import com.wire.bots.cali.resources.NotificationResource;
import com.wire.bots.sdk.ClientRepo;
import com.wire.bots.sdk.MessageHandlerBase;
import com.wire.bots.sdk.Server;
import com.wire.bots.sdk.crypto.CryptoDatabase;
import com.wire.bots.sdk.crypto.storage.RedisStorage;
import com.wire.bots.sdk.factories.CryptoFactory;
import com.wire.bots.sdk.factories.StorageFactory;
import com.wire.bots.sdk.state.RedisState;
import io.dropwizard.setup.Environment;

public class Service extends Server<Config> {
    static Config CONFIG;
    static ClientRepo repo;
    private AlertManager alertManager;
    private CommandManager commandManager;

    public static void main(String[] args) throws Exception {
        //System.loadLibrary("blender"); // Load native library at runtime
        new Service().run(args);
    }

    @Override
    protected MessageHandlerBase createHandler(Config config, Environment env) {
        return new MessageHandler(alertManager, commandManager, getStorageFactory(config));
    }

    @Override
    protected void initialize(Config config, Environment env) {
        CONFIG = config;
        env.jersey().setUrlPattern("/cali/*");

        alertManager = new AlertManager(config.postgres);
        commandManager = new CommandManager();
    }

    @Override
    protected void onRun(Config config, Environment env) {
        Service.repo = super.repo;
        addResource(new AuthResource(repo), env);
        addResource(new NotificationResource(repo), env);
    }

    @Override
    protected StorageFactory getStorageFactory(Config config) {
        return botId -> new RedisState(botId, config.db);
    }

    @Override
    protected CryptoFactory getCryptoFactory(Config config) {
        return (botId) -> new CryptoDatabase(botId, new RedisStorage(
                config.db.host,
                config.db.port,
                config.db.password));
    }
}
