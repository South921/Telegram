package com.telegram.common;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOError;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author: Gray
 * @create: 2020-03-17 14:30
 **/
@Component
@Configuration
public class TelegramCommon {

    private Map<String, Telegram> telegramMap = new HashMap<>();

    @Value("{tdjniPath}")
    private String tdjniPath;

    @PostConstruct
    public void init() {
        try {
            System.setProperty("java.library.path", tdjniPath);
            System.loadLibrary("tdjni");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
        Client.execute(new TdApi.SetLogVerbosityLevel(0));
        if (Client.execute(new TdApi.SetLogStream(new TdApi.LogStreamFile("tdlib.log", 1 << 27))) instanceof TdApi.Error) {
            throw new IOError(new IOException("Write access to the current directory is required"));
        }
    }

    public void mapPush(String phoneNumber, Telegram telegram) {
        telegramMap.put(phoneNumber, telegram);
    }

    public Telegram mapGet(String phoneNumber) {
        return telegramMap.get(phoneNumber);
    }
}
