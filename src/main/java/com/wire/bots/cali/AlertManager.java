package com.wire.bots.cali;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.wire.bots.sdk.ClientRepo;
import com.wire.bots.sdk.Logger;
import com.wire.bots.sdk.WireClient;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

class AlertManager {
    private static final int REMIND_IN = 16;
    private static final int PERIOD = 5;

    private final Timer timer = new Timer();
    private final HashMap<String, Event> remindersMap = new HashMap<>();
    private final ClientRepo repo;

    AlertManager(ClientRepo repo) {
        this.repo = repo;

        schedule();
    }

    private void schedule() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (WireClient client : getClients()) {
                    fetchEvents(client);
                }
            }
        }, TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(PERIOD));
    }

    private void fetchEvents(final WireClient wireClient) {
        try {
            Calendar service = CalendarAPI.getCalendarService(wireClient.getId());
            DateTime now = new DateTime(System.currentTimeMillis());
            Events events = service.events().list("primary")
                    .setMaxResults(3)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            for (final Event event : events.getItems()) {
                String id = String.format("%s-%s", wireClient.getId(), event.getId());
                if (remindersMap.put(id, event) == null) {
                    final DateTime start = event.getStart().getDateTime();

                    Date at = new Date(start.getValue() - TimeUnit.MINUTES.toMillis(REMIND_IN));
                    if (at.getTime() > System.currentTimeMillis()) {
                        scheduleReminder(wireClient, at, event.getId());
                    }
                }
            }
        } catch (IOException e) {
            //Logger.warning(e.getLocalizedMessage());
        }
    }

    private void scheduleReminder(final WireClient wireClient, final Date at, final String eventId) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    String botId = wireClient.getId();
                    Event e = CalendarAPI.getEvent(botId, eventId);
                    if (e != null) {
                        DateTime eventStart = e.getStart().getDateTime();

                        long l = eventStart.getValue() - System.currentTimeMillis();
                        long minutes = TimeUnit.MILLISECONDS.toMinutes(l);

                        wireClient.ping();
                        String msg = String.format("**%s** in **%d** minutes", e.getSummary(), minutes);
                        wireClient.sendText(msg);

                        Logger.info("Reminder `%s` sent to %s for: %s",
                                msg,
                                botId,
                                eventStart.toString());
                    }
                } catch (Exception e) {
                    Logger.warning(e.getLocalizedMessage());
                }
            }
        }, at);
    }

    private ArrayList<WireClient> getClients() {
        final ArrayList<WireClient> ret = new ArrayList<>();
        File dir = new File(repo.getPath());
        dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                String botId = file.getName();
                WireClient wireClient = repo.getWireClient(botId);
                boolean valid = wireClient != null;
                if (valid)
                    ret.add(wireClient);
                return valid;
            }
        });
        return ret;
    }
}
