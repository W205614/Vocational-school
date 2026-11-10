package com.zhixuetang.promotion.utils;

import cn.hutool.core.text.StrBuilder;

/**
 * 将整数转为base32字符的工具，因为是32进制，所以每5个bit位转一次
 */
public class Base32 {
    private static final String baseChars = "6CSB7H8DAKXZF3N95RTMVUQG2YE4JWPL";

    public static String encode(long raw) {
        StrBuilder sb = new StrBuilder();
        while (raw != 0) {
            int i = (int) (raw & 0b11111);
            sb.append(baseChars.charAt(i));
            raw = raw >>> 5;
        }
        return sb.toString();
    }

    public static long decode(String code) {
        long r = 0;
        char[] chars = code.toCharArray();
        for (int i = chars.length - 1; i >= 0; i--) {
            long n = baseChars.indexOf(chars[i]);
            r = r | (n << (5 * i));
        }
        return r;
    }

}
