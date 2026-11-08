package com.office.calendar.telegram;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

// 연동 코드를 만든다. 추측할 수 없도록 암호학적 난수로 영문·숫자 32자를 뽑는다
// (텔레그램 딥링크 start 값은 영문·숫자·_·-, 최대 64자까지 허용).
@Component
public class LinkCodeGenerator {

    public static final int LENGTH = 32;
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);

    }

}
