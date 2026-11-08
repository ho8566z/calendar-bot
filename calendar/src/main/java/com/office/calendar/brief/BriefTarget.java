package com.office.calendar.brief;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 브리핑을 받을 회원: 수신을 켰고 텔레그램이 연동된 USER
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BriefTarget {

    private int memberNo;
    private String memberId;
    private Long chatId;

}
