package com.telegram.controller;

import com.telegram.common.Telegram;
import com.telegram.common.TelegramCommon;
import lombok.AllArgsConstructor;
import org.drinkless.tdlib.TdApi;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: Gray
 * @create: 2020-03-17 13:40
 **/
@RestController
@AllArgsConstructor
@RequestMapping(value = "/telegram", produces = MediaType.APPLICATION_JSON_VALUE)
public class TelegramController {

    private TelegramCommon telegramCommon;

    @RequestMapping("/sendPhoneNumberCode")
    public String sendPhoneNumberCode(String phoneNumber) {
        Telegram telegram = new Telegram(phoneNumber);
        telegram.getClient().send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), telegram.new AuthorizationRequestHandler());
        telegramCommon.mapPush(phoneNumber, telegram);
        return telegram.message;
    }

    @RequestMapping("/checkPhoneNumberCode")
    public String checkPhoneNumberCode(String phoneNumber, String code) {
        Telegram telegram = telegramCommon.mapGet(phoneNumber);
        if(telegram != null) {
            telegram.getClient().send(new TdApi.CheckAuthenticationCode(code), telegram.new AuthorizationRequestHandler());
            return telegram.message;
        } else {
            return "无实例数据.";
        }
    }

    @RequestMapping("/logout")
    public String logout(String phoneNumber, String code) {
        Telegram telegram = telegramCommon.mapGet(phoneNumber);
        if(telegram != null) {
            telegram.getClient().send(new TdApi.LogOut(), telegram.new AuthorizationRequestHandler());
            return telegram.message;
        } else {
            return "无实例数据.";
        }
    }
}
