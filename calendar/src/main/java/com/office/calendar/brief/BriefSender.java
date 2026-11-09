package com.office.calendar.brief;

import java.util.List;

// 브리핑 메시지를 한 사람에게 보낸다. 발송 채널(지금은 텔레그램)을 바꿔도 BriefService는 그대로 쓰도록 나눈 경계다.
public interface BriefSender {

    // parts를 순서대로 보낸다. 하나라도 실패하면 예외를 던지고 남은 조각은 보내지 않는다.
    void send(long chatId, List<String> parts);

}
