package com.telegram.common;

import lombok.Data;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author: Gray
 * @create: 2020-03-17 14:38
 **/
public final class Telegram {
    private String phoneNumber;
    public Client getClient() {
        return this.client;
    }
    private  final Logger logger = LoggerFactory.getLogger(Telegram.class);
    public  volatile String message;
    private  Client client = null;
    private  TdApi.User me = null;
    private  TdApi.AuthorizationState authorizationState = null;
    private final Lock authorizationLock = new ReentrantLock();
    private final Condition gotAuthorization = authorizationLock.newCondition();
    private  ConcurrentMap<Integer, TdApi.User> users = new ConcurrentHashMap<Integer, TdApi.User>();
    private  ConcurrentMap<Integer, TdApi.BasicGroup> basicGroups = new ConcurrentHashMap<Integer, TdApi.BasicGroup>();
    private  ConcurrentMap<Integer, TdApi.Supergroup> supergroups = new ConcurrentHashMap<Integer, TdApi.Supergroup>();
    private  ConcurrentMap<Integer, TdApi.SecretChat> secretChats = new ConcurrentHashMap<Integer, TdApi.SecretChat>();

    private  ConcurrentMap<Long, TdApi.Chat> chats = new ConcurrentHashMap<Long, TdApi.Chat>();
    private  NavigableSet<OrderedChat> mainChatList = new TreeSet<OrderedChat>();

    private  final ConcurrentMap<Integer, TdApi.UserFullInfo> usersFullInfo = new ConcurrentHashMap<Integer, TdApi.UserFullInfo>();
    private  final ConcurrentMap<Integer, TdApi.BasicGroupFullInfo> basicGroupsFullInfo = new ConcurrentHashMap<Integer, TdApi.BasicGroupFullInfo>();
    private  final ConcurrentMap<Integer, TdApi.SupergroupFullInfo> supergroupsFullInfo = new ConcurrentHashMap<Integer, TdApi.SupergroupFullInfo>();
    private  volatile boolean haveAuthorization = false;

    public Telegram(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        if(client == null) {
            authorizationLock.lock();
            try {
                client = Client.create(new UpdatesHandler(), null, null);
                gotAuthorization.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                authorizationLock.unlock();
            }
        }
    }
    private class UpdatesHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.UpdateAuthorizationState.CONSTRUCTOR:
                    onAuthorizationStateUpdated(((TdApi.UpdateAuthorizationState) object).authorizationState);
                    break;
                case TdApi.UpdateNewCallbackQuery.CONSTRUCTOR: {

                    break;
                }
                case TdApi.UpdateNewMessage.CONSTRUCTOR: {//todo：接受消息
                    TdApi.UpdateNewMessage message = (TdApi.UpdateNewMessage) object;

                    //获取sender User信息
                    int senderID = message.message.senderUserId;
                    String sender = String.valueOf(senderID);
                    TdApi.User sendUser = getOrQueryUser(senderID);
                    if (Objects.nonNull(sendUser)) {
                        sender = sendUser.firstName + " " + (sendUser.lastName.length() > 0 ? sendUser.lastName : "");
                    }
                    //获取Chat信息
                    long chatId = message.message.chatId;
                    String chatName = String.valueOf(chatId);
                    TdApi.Chat targetChat = getOrQueryChat(chatId);
                    if (Objects.nonNull(targetChat)) {
                        chatName = targetChat.title;
                    }

                    TdApi.MessageContent messageContent = message.message.content;
                    if (messageContent instanceof TdApi.MessageText) {
                        TdApi.MessageText textMessage = (TdApi.MessageText) messageContent;
                        TdApi.FormattedText formattedText = textMessage.text;
                        String log = String.format("(%s) @%s (%s)@%s\n%s\n", sender, chatName, senderID, chatId, formattedText.text);
                        logger.info("\n" + log);
                    }
                    break;
                }
                case TdApi.UpdateUser.CONSTRUCTOR:
                    TdApi.UpdateUser updateUser = (TdApi.UpdateUser) object;
                    users.put(updateUser.user.id, updateUser.user);
                    break;
                case TdApi.UpdateUserStatus.CONSTRUCTOR: {
                    TdApi.UpdateUserStatus updateUserStatus = (TdApi.UpdateUserStatus) object;
                    TdApi.User user = users.get(updateUserStatus.userId);
                    synchronized (user) {
                        user.status = updateUserStatus.status;
                    }
                    break;
                }
                case TdApi.UpdateBasicGroup.CONSTRUCTOR:
                    TdApi.UpdateBasicGroup updateBasicGroup = (TdApi.UpdateBasicGroup) object;
                    basicGroups.put(updateBasicGroup.basicGroup.id, updateBasicGroup.basicGroup);
                    break;
                case TdApi.UpdateSupergroup.CONSTRUCTOR:
                    TdApi.UpdateSupergroup updateSupergroup = (TdApi.UpdateSupergroup) object;
                    supergroups.put(updateSupergroup.supergroup.id, updateSupergroup.supergroup);
                    break;
                case TdApi.UpdateSecretChat.CONSTRUCTOR:
                    TdApi.UpdateSecretChat updateSecretChat = (TdApi.UpdateSecretChat) object;
                    secretChats.put(updateSecretChat.secretChat.id, updateSecretChat.secretChat);
                    break;

                case TdApi.UpdateNewChat.CONSTRUCTOR: {
                    TdApi.UpdateNewChat updateNewChat = (TdApi.UpdateNewChat) object;
                    TdApi.Chat chat = updateNewChat.chat;
                    synchronized (chat) {
                        chats.put(chat.id, chat);

                        long order = chat.order;
                        chat.order = 0;
                        setChatOrder(chat, order);
                    }
                    break;
                }
                case TdApi.UpdateChatTitle.CONSTRUCTOR: {
                    TdApi.UpdateChatTitle updateChat = (TdApi.UpdateChatTitle) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.title = updateChat.title;
                    }
                    break;
                }
                case TdApi.UpdateChatPhoto.CONSTRUCTOR: {
                    TdApi.UpdateChatPhoto updateChat = (TdApi.UpdateChatPhoto) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.photo = updateChat.photo;
                    }
                    break;
                }
                case TdApi.UpdateChatChatList.CONSTRUCTOR: {
                    TdApi.UpdateChatChatList updateChat = (TdApi.UpdateChatChatList) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (mainChatList) { // to not change Chat.chatList while mainChatList is locked
                        synchronized (chat) {
                            assert chat.order == 0; // guaranteed by TDLib
                            chat.chatList = updateChat.chatList;
                        }
                    }
                    break;
                }
                case TdApi.UpdateChatLastMessage.CONSTRUCTOR: {
                    TdApi.UpdateChatLastMessage updateChat = (TdApi.UpdateChatLastMessage) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastMessage = updateChat.lastMessage;
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }
                case TdApi.UpdateChatOrder.CONSTRUCTOR: {
                    TdApi.UpdateChatOrder updateChat = (TdApi.UpdateChatOrder) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }
                case TdApi.UpdateChatIsPinned.CONSTRUCTOR: {
                    TdApi.UpdateChatIsPinned updateChat = (TdApi.UpdateChatIsPinned) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.isPinned = updateChat.isPinned;
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }
                case TdApi.UpdateChatReadInbox.CONSTRUCTOR: {
                    TdApi.UpdateChatReadInbox updateChat = (TdApi.UpdateChatReadInbox) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastReadInboxMessageId = updateChat.lastReadInboxMessageId;
                        chat.unreadCount = updateChat.unreadCount;
                    }
                    break;
                }
                case TdApi.UpdateChatReadOutbox.CONSTRUCTOR: {
                    TdApi.UpdateChatReadOutbox updateChat = (TdApi.UpdateChatReadOutbox) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.lastReadOutboxMessageId = updateChat.lastReadOutboxMessageId;
                    }
                    break;
                }
                case TdApi.UpdateChatUnreadMentionCount.CONSTRUCTOR: {
                    TdApi.UpdateChatUnreadMentionCount updateChat = (TdApi.UpdateChatUnreadMentionCount) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.unreadMentionCount = updateChat.unreadMentionCount;
                    }
                    break;
                }
                case TdApi.UpdateMessageMentionRead.CONSTRUCTOR: {
                    TdApi.UpdateMessageMentionRead updateChat = (TdApi.UpdateMessageMentionRead) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.unreadMentionCount = updateChat.unreadMentionCount;
                    }
                    break;
                }
                case TdApi.UpdateChatReplyMarkup.CONSTRUCTOR: {
                    TdApi.UpdateChatReplyMarkup updateChat = (TdApi.UpdateChatReplyMarkup) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.replyMarkupMessageId = updateChat.replyMarkupMessageId;
                    }
                    break;
                }
                case TdApi.UpdateChatDraftMessage.CONSTRUCTOR: {
                    TdApi.UpdateChatDraftMessage updateChat = (TdApi.UpdateChatDraftMessage) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.draftMessage = updateChat.draftMessage;
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }
                case TdApi.UpdateChatNotificationSettings.CONSTRUCTOR: {
                    TdApi.UpdateChatNotificationSettings update = (TdApi.UpdateChatNotificationSettings) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.notificationSettings = update.notificationSettings;
                    }
                    break;
                }
                case TdApi.UpdateChatDefaultDisableNotification.CONSTRUCTOR: {
                    TdApi.UpdateChatDefaultDisableNotification update = (TdApi.UpdateChatDefaultDisableNotification) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.defaultDisableNotification = update.defaultDisableNotification;
                    }
                    break;
                }
                case TdApi.UpdateChatIsMarkedAsUnread.CONSTRUCTOR: {
                    TdApi.UpdateChatIsMarkedAsUnread update = (TdApi.UpdateChatIsMarkedAsUnread) object;
                    TdApi.Chat chat = chats.get(update.chatId);
                    synchronized (chat) {
                        chat.isMarkedAsUnread = update.isMarkedAsUnread;
                    }
                    break;
                }
                case TdApi.UpdateChatIsSponsored.CONSTRUCTOR: {
                    TdApi.UpdateChatIsSponsored updateChat = (TdApi.UpdateChatIsSponsored) object;
                    TdApi.Chat chat = chats.get(updateChat.chatId);
                    synchronized (chat) {
                        chat.isSponsored = updateChat.isSponsored;
                        setChatOrder(chat, updateChat.order);
                    }
                    break;
                }

                case TdApi.UpdateUserFullInfo.CONSTRUCTOR:
                    TdApi.UpdateUserFullInfo updateUserFullInfo = (TdApi.UpdateUserFullInfo) object;
                    usersFullInfo.put(updateUserFullInfo.userId, updateUserFullInfo.userFullInfo);
                    break;
                case TdApi.UpdateBasicGroupFullInfo.CONSTRUCTOR:
                    TdApi.UpdateBasicGroupFullInfo updateBasicGroupFullInfo = (TdApi.UpdateBasicGroupFullInfo) object;
                    basicGroupsFullInfo.put(updateBasicGroupFullInfo.basicGroupId, updateBasicGroupFullInfo.basicGroupFullInfo);
                    break;
                case TdApi.UpdateSupergroupFullInfo.CONSTRUCTOR:
                    TdApi.UpdateSupergroupFullInfo updateSupergroupFullInfo = (TdApi.UpdateSupergroupFullInfo) object;
                    supergroupsFullInfo.put(updateSupergroupFullInfo.supergroupId, updateSupergroupFullInfo.supergroupFullInfo);
                    break;
                default:

            }
        }
    }

    private void onAuthorizationStateUpdated(TdApi.AuthorizationState authorizationState) {
        if (authorizationState != null) {
            this.authorizationState = authorizationState;
        }
        switch (this.authorizationState.getConstructor()) {
            //todo: 设置初始化参数
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
                TdApi.TdlibParameters parameters = new TdApi.TdlibParameters();
                parameters.databaseDirectory = "dbDir/" + this.phoneNumber.substring(1);
                parameters.useMessageDatabase = true;
                parameters.useSecretChats = true;
                parameters.apiId = 1250086;
                parameters.apiHash = "8a495d67fd311656f7ec68cb57c7332f";
                parameters.systemLanguageCode = "en";
                parameters.deviceModel = "Desktop";
                parameters.systemVersion = "Unknown";
                parameters.applicationVersion = "1.0";
                parameters.enableStorageOptimizer = true;

                client.send(new TdApi.SetTdlibParameters(parameters), new AuthorizationRequestHandler());
                break;
            case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR:
                client.send(new TdApi.CheckDatabaseEncryptionKey(), new AuthorizationRequestHandler());
                authorizationLock.lock();
                try {
                    gotAuthorization.signal();
                } finally {
                    authorizationLock.unlock();
                }
                break;
            case TdApi.AuthorizationStateReady.CONSTRUCTOR:
                haveAuthorization = true;
                client.send(new TdApi.GetMe(), (cell) -> {
                    if (cell instanceof TdApi.User) {
                        me = (TdApi.User) cell;
                    }
                });
                break;
            case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
                haveAuthorization = false;
                client.send(new TdApi.LogOut(), new DefaultHandler());
                break;
            case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
                haveAuthorization = false;
                break;
            case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
                client.send(new TdApi.Close(), new DefaultHandler());
                break;
            default:
                logger.info("Unsupported authorization state:" + this.authorizationState);
        }
    }

    private  TdApi.User getOrQueryUser(int userID) {
        if (userID == 0) {
            return null;
        }
        TdApi.User sendUser = users.get(userID);
        if (Objects.nonNull(sendUser)) {
            return sendUser;
        } else {
            client.send(new TdApi.GetUser(userID), new DefaultHandler());
            return null;
        }
    }

    private  TdApi.Chat getOrQueryChat(long chatId) {
        TdApi.Chat targetChat = chats.get(chatId);
        if (Objects.isNull(targetChat)) {
            client.send(new TdApi.GetChat(chatId), new DefaultHandler());
            return null;
        } else {
            return targetChat;
        }
    }

    private  void setChatOrder(TdApi.Chat chat, long order) {
        synchronized (mainChatList) {
            synchronized (chat) {
                if (chat.chatList == null || chat.chatList.getConstructor() != TdApi.ChatListMain.CONSTRUCTOR) {
                    return;
                }

                if (chat.order != 0) {
                    boolean isRemoved = mainChatList.remove(new OrderedChat(chat.order, chat.id));
                    assert isRemoved;
                }

                chat.order = order;

                if (chat.order != 0) {
                    boolean isAdded = mainChatList.add(new OrderedChat(chat.order, chat.id));
                    assert isAdded;
                }
            }
        }
    }

    private  class OrderedChat implements Comparable<OrderedChat> {
        final long order;
        final long chatId;

        OrderedChat(long order, long chatId) {
            this.order = order;
            this.chatId = chatId;
        }

        @Override
        public int compareTo(OrderedChat o) {
            if (this.order != o.order) {
                return o.order < this.order ? -1 : 1;
            }
            if (this.chatId != o.chatId) {
                return o.chatId < this.chatId ? -1 : 1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            OrderedChat o = (OrderedChat) obj;
            return this.order == o.order && this.chatId == o.chatId;
        }
    }

    private  class DefaultHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            if (object instanceof TdApi.Chat) {
                TdApi.Chat chat = (TdApi.Chat) object;
                chats.put(chat.id, chat);
            }
            if (object instanceof TdApi.User) {
                TdApi.User user = (TdApi.User) object;
                users.put(user.id, user);
            }
        }
    }

    public  class AuthorizationRequestHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.Error.CONSTRUCTOR:
                    authorizationLock.lock();
                    gotAuthorization.signal();
                    message = String.valueOf(object);
                    logger.error("接收到错误:" + object);
                    authorizationLock.unlock();
                    break;
                case TdApi.Ok.CONSTRUCTOR:
                    message = String.valueOf(object);
                    logger.info("接收到结果:" + object);
                    break;
                default:
                    logger.info("Receive wrong response from TDLib:" + object);
            }
        }
    }
}
