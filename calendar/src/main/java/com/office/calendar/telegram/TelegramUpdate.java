package com.office.calendar.telegram;

import java.util.Map;

// getUpdates로 받은 업데이트 한 건에서 연동에 필요한 값만 뽑은 것.
// 메시지가 아닌 업데이트(수정·채널 글 등)는 chatId와 text가 null이다.
public record TelegramUpdate(long updateId, Long chatId, String chatType, String text) {

    @SuppressWarnings("unchecked")
    static TelegramUpdate fromMap(Map<String, Object> raw) {
        long updateId = ((Number) raw.get("update_id")).longValue();

        Object message = raw.get("message");
        if (!(message instanceof Map)) {
            return new TelegramUpdate(updateId, null, null, null);
        }
        Map<String, Object> msg = (Map<String, Object>) message;

        Long chatId = null;
        String chatType = null;
        if (msg.get("chat") instanceof Map) {
            Map<String, Object> chat = (Map<String, Object>) msg.get("chat");
            if (chat.get("id") instanceof Number) {
                chatId = ((Number) chat.get("id")).longValue();
            }
            if (chat.get("type") instanceof String) {
                chatType = (String) chat.get("type");
            }
        }

        String text = msg.get("text") instanceof String ? (String) msg.get("text") : null;

        return new TelegramUpdate(updateId, chatId, chatType, text);

    }

    // 개인 대화(봇과 1:1)에서 온 텍스트 메시지인지
    public boolean isPrivateText() {
        return chatId != null && text != null && "private".equals(chatType);
    }

}
