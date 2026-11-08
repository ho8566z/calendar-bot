package com.office.calendar.telegram;

import java.util.List;

// 텔레그램 Bot API 중 이 프로젝트가 쓰는 두 가지. 실패하면 TelegramApiException을 던진다.
public interface TelegramApi {

    // 업데이트를 가져온다(롱 폴링). offset은 처리한 update_id + 1이고, 음수(-1)면 가장 최근 1건만 돌려주고 이전 것은 확인 처리된다.
    // timeoutSeconds 동안 새 업데이트가 없으면 빈 목록으로 돌아온다.
    List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds);

    // 일반 텍스트 메시지를 보낸다(parse_mode 없음, 최대 4096자)
    void sendMessage(long chatId, String text);

}
