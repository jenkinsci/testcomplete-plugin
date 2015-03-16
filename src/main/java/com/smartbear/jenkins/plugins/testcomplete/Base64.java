/*
 * The MIT License
 *
 * Copyright (c) 2015, SmartBear Software
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.smartbear.jenkins.plugins.testcomplete;

/**
 * @author Igor Filin
 */
public class Base64
{
    private static int[] integers = new int[128];

    private final static char[] symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            .toCharArray();

    static {
        for (int i = 0; i < symbols.length; i++) {
            integers[symbols[i]] = i;
        }
    }

    public String encode(byte[] buffer) {
        int totalSize = buffer.length;
        char[] ar = new char[((totalSize + 2) / 3) * 4];
        int a = 0, i = 0;
        while (i < totalSize) {
            byte b0 = buffer[i++];
            byte b1 = (i < totalSize) ? buffer[i++] : 0;
            byte b2 = (i < totalSize) ? buffer[i++] : 0;

            int mask = 0x3F;
            ar[a++] = symbols[(b0 >> 2) & mask];
            ar[a++] = symbols[((b0 << 4) | ((b1 & 0xFF) >> 4)) & mask];
            ar[a++] = symbols[((b1 << 2) | ((b2 & 0xFF) >> 6)) & mask];
            ar[a++] = symbols[b2 & mask];
        }
        switch (totalSize % 3) {
            case 1:
                ar[--a] = '=';
            case 2:
                ar[--a] = '=';
        }
        return new String(ar);
    }

    public byte[] decode(String s) {
        int delta = s.endsWith("==") ? 2 : s.endsWith("=") ? 1 : 0;
        byte[] buffer = new byte[s.length() * 3 / 4 - delta];
        int mask = 0xFF;
        int index = 0;
        for (int i = 0; i < s.length(); i += 4) {
            int c0 = integers[s.charAt(i)];
            int c1 = integers[s.charAt(i + 1)];
            buffer[index++] = (byte) (((c0 << 2) | (c1 >> 4)) & mask);
            if (index >= buffer.length) {
                return buffer;
            }
            int c2 = integers[s.charAt(i + 2)];
            buffer[index++] = (byte) (((c1 << 4) | (c2 >> 2)) & mask);
            if (index >= buffer.length) {
                return buffer;
            }
            int c3 = integers[s.charAt(i + 3)];
            buffer[index++] = (byte) (((c2 << 6) | c3) & mask);
        }
        return buffer;
    }
}
