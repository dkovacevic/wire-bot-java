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

import com.wire.bots.sdk.MessageHandlerBase;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.factories.StorageFactory;
import com.wire.bots.sdk.models.TextMessage;
import com.wire.bots.sdk.server.model.NewBot;
import com.wire.bots.sdk.server.model.User;
import com.wire.bots.sdk.tools.Logger;

public class MessageHandler extends MessageHandlerBase {
    private final StorageFactory storageF;
    //private final ConcurrentHashMap<String, Blender> blenders = new ConcurrentHashMap<>();
    private final AlertManager alertManager;
    private final CommandManager commandManager;

    MessageHandler(AlertManager alertManager, CommandManager commandManager, StorageFactory storageF) {
        this.storageF = storageF;
        this.alertManager = alertManager;
        this.commandManager = commandManager;
    }

    @Override
    public String getName(NewBot newBot) {
        return String.format("%s's Calendar", newBot.origin.name);
    }

    @Override
    public boolean onNewBot(NewBot newBot) {
        try {
            boolean insertNewSubscriber = alertManager.insertNewSubscriber(newBot.id);
            Logger.info("onNewBot: bot: %s, user: %s %s", newBot.id, newBot.origin.id, insertNewSubscriber);
        } catch (Exception e) {
            Logger.error("onNewBot: bot: %s, error: %s", newBot.id, e);
        }
        return true;
    }

    @Override
    public void onNewConversation(final WireClient client) {
        try {
            client.sendText("Hello!\n" +
                    "Thank you for adding me here. Follow this link to connect me to one of your calendars.");
            commandManager.showAuthLink(client, getOwner(client));
        } catch (Exception e) {
            Logger.warning("onNewConversation: %s %s", client.getId(), e);
        }
    }

    @Override
    public void onBotRemoved(String botId) {
        try {
            boolean remove = alertManager.removeSubscriber(botId);
            Logger.info("onBotRemoved. Bot: %s %s", botId, remove);
        } catch (Exception e) {
            Logger.error("onBotRemoved: %s %s", botId, e);
        }
    }

    @Override
    public void onCalling(WireClient client, String userId, String clientId, String content) {
        //String botId = client.getId();
//        Blender blender = getBlender(botId);
//        blender.recvMessage(botId, userId, clientId, content);
    }

    @Override
    public void onText(WireClient client, TextMessage msg) {
        try {
            commandManager.processCommand(client, msg.getUserId(), msg.getText());
        } catch (Exception e) {
            Logger.warning("onText: %s %s", client.getId(), e);
        }
    }

//    private Blender getBlender(String botId) {
//        return blenders.computeIfAbsent(botId, k -> {
//            try {
//                String module = Service.CONFIG.getModule();
//                String ingress = Service.CONFIG.getIngress();
//                int portMin = Service.CONFIG.getPortMin();
//                int portMax = Service.CONFIG.getPortMax();
//
//                State storage = storageFactory.create(botId);
//                NewBot state = storage.getState();
//                Blender blender = new Blender();
//                blender.init(module, botId, state.client, ingress, portMin, portMax);
//                blender.registerListener(new CallListener(repo));
//                return blender;
//            } catch (Exception e) {
//                Logger.error(e.toString());
//                return null;
//            }
//        });
//    }

    private User getOwner(WireClient client) throws Exception {
        String botId = client.getId();
        NewBot state = storageF.create(botId).getState();
        return client.getUser(state.origin.id);
    }
}
