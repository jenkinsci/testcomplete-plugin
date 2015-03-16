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

import hudson.model.BuildListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;

import javax.crypto.Cipher;
import java.security.Key;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Calendar;

public class Utils {

    private static final String PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDCD43scUktBOFoR10dS80DbFJf" +
        "MgJoyNGtfxVyQ6DKwmzb1OS+P3E5Y47K3G6fXX8OfhT0WmQ/Aqr61nUXxRgn2cFH" +
        "Kyc4rjFjfMTkPGkv7rWdIuu+4VR9PYEXar4OyCQEThfhdDSPzfHJ8oiPNqkXe5IY" +
        "L1xQevURO0+Sapzf7wIDAQAB";

    private static final int ENC_CHUNK_MAX_SIZE = 116;

    private Utils() {
    }

    public static boolean isWindows(VirtualChannel channel, BuildListener listener) {
        try {
            return channel.call(new Callable<Boolean, Exception>() {
                public Boolean call() throws Exception {
                    String os = System.getProperty("os.name");
                    if (os != null) {
                        os = os.toLowerCase();
                    }
                    return (os != null && os.contains("windows"));
                }
            });
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e);
            return false;
        }
    }

    public static long getSystemTime(VirtualChannel channel, BuildListener listener) {
        try {
            return channel.call(new Callable<Long, Exception>() {
                public Long call() throws Exception {
                    return System.currentTimeMillis();
                }
            });
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e);
            return 0;
        }
    }

    private static double HALF_SECOND = (1.0 / 172800.0);
    static int rgMonthDays[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365};

    public static long OLEDateToMillis(double dSerialDate)
    {
        // Note that every fourth century is a leap year but other centuries are not
        long nDaysAdjust;         // Adjust for Excel treating 1900 as a leap year
        long nSecsInDay;          // Time in seconds since midnight
        long nMinutesInDay;       // Minutes in day
        long QuadCenturies;       // Number of 400 year periods since Jan 1, 1900
        long QuadCenturyCount;    // Which period are we in
        long QuadYearsCount;      // Number of 4 year periods since Jan 1, 1900
        long QuadYearsDayCount;   // Day position within current 4 year period
        long QuadYearsYearCount;  // Year position within 4 year period

        boolean bLeapInCurrentPeriod = true; // FALSE if it includes century that is not a leap year

        double dblDate = dSerialDate; // temporary serial date

        // Round to the second
        dblDate += ((dSerialDate > 0.0) ? HALF_SECOND : -HALF_SECOND);

        // Adjust for Excel treating 1900 as a leap year
        //  Offset so that 12/30/1899 is 0
        nDaysAdjust = (long)dblDate + 693959L;
        dblDate = Math.abs(dblDate);
        nSecsInDay = (long)((dblDate - Math.floor(dblDate)) * 86400.);

        // Leap years every 4 yrs except where period includes century that is not a leap year
        QuadCenturies = nDaysAdjust / 146097L;

        // Set nDaysAdjust to day within Quad Century block
        nDaysAdjust %= 146097L;

        // Subtract 1 to adjust for Excel treating 1900 as a leap year
        QuadCenturyCount = (nDaysAdjust - 1) / 36524L;  // Non-leap century

        if (QuadCenturyCount != 0) {
            // Set nDaysAdjust to day within current centurY
            nDaysAdjust = (nDaysAdjust - 1) % 36524L;

            // +1 to adjust for Excel treating 1900 as a leap year
            QuadYearsCount = (nDaysAdjust + 1) / 1461L;

            if (QuadYearsCount != 0) {
                QuadYearsDayCount = (nDaysAdjust + 1) % 1461L;
            } else {
                // Current century is not a leap year
                bLeapInCurrentPeriod = false;
                QuadYearsDayCount = nDaysAdjust;
            }
        } else {
            // Current century is leap year
            QuadYearsCount = nDaysAdjust / 1461L;
            QuadYearsDayCount = nDaysAdjust % 1461L;
        }

        if (bLeapInCurrentPeriod) {
            // -1 because first year has 366 days
            QuadYearsYearCount = (QuadYearsDayCount - 1) / 365;

            if (QuadYearsYearCount != 0) {
                QuadYearsDayCount = (QuadYearsDayCount - 1) % 365;
            }
        } else {
            QuadYearsYearCount = QuadYearsDayCount / 365;
            QuadYearsDayCount %= 365;
        }

        // values in terms of year month date.
        int tm_sec;
        int tm_min;
        int tm_hour;
        int tm_mday;
        int tm_mon;
        int tm_year;

        tm_year = (int)(QuadCenturies * 400 + QuadCenturyCount * 100 + QuadYearsCount * 4 + QuadYearsYearCount);

        // Handle leap year: before, on, and after Feb. 29.
        if (QuadYearsYearCount == 0 && bLeapInCurrentPeriod && QuadYearsDayCount == 59) {
            /* Feb. 29 */
            tm_mon = 2;
            tm_mday = 29;
        } else {
            if (QuadYearsYearCount == 0 && bLeapInCurrentPeriod && QuadYearsDayCount >= 59) {
                --QuadYearsDayCount;
            }

            // Make QuadYearsDayCount a 1-based day of non-leap year and compute
            //  month/day for everything but Feb. 29.
            ++QuadYearsDayCount;

            // Month number always >= n/32, so save some loop time */
            for (tm_mon = (int)((QuadYearsDayCount >> 5) + 1); QuadYearsDayCount > rgMonthDays[tm_mon]; tm_mon++);

            tm_mday = (int)(QuadYearsDayCount - rgMonthDays[tm_mon-1]);
        }

        tm_sec = (int)(nSecsInDay % 60L);
        nMinutesInDay = nSecsInDay / 60L;
        tm_min = (int)(nMinutesInDay % 60);
        tm_hour = (int)(nMinutesInDay / 60);

        Calendar c = Calendar.getInstance();

        c.set(Calendar.YEAR, tm_year);
        c.set(Calendar.MONTH, tm_mon - 1);
        c.set(Calendar.DATE, tm_mday);
        c.set(Calendar.HOUR_OF_DAY, tm_hour);
        c.set(Calendar.MINUTE, tm_min);
        c.set(Calendar.SECOND, tm_sec);

        return c.getTimeInMillis();
    }

    public static String encryptPassword(String password) throws Exception {
        byte[] keyRawData = new Base64().decode(PUBLIC_KEY);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        KeySpec ks = new X509EncodedKeySpec(keyRawData);
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(ks);
        byte[] encryptedData = encrypt(password, publicKey);

        String encodedString = new Base64().encode(encryptedData);

        return encodedString;
    }

    private static byte[] encrypt(String data, Key publicKey) throws Exception {
        ArrayList<byte[]> resultData = new ArrayList<byte[]>();

        Cipher rsa = Cipher.getInstance("RSA");
        rsa.init(Cipher.ENCRYPT_MODE, publicKey);
        byte dataRaw[] = data.getBytes("UTF-16LE");

        int chunksCount = dataRaw.length / ENC_CHUNK_MAX_SIZE +
                ((dataRaw.length % ENC_CHUNK_MAX_SIZE) > 0 ? 1 : 0);
        int remaining = dataRaw.length;

        for (int i = 0; i < chunksCount; i++) {
            int startIndex = i * ENC_CHUNK_MAX_SIZE;
            int length = Math.min(remaining, ENC_CHUNK_MAX_SIZE);
            remaining -= length;
            resultData.add(reverseOrder(rsa.doFinal(dataRaw, startIndex, length)));
        }

        int totalLength = 0;
        for (int i = 0; i < resultData.size(); i++) {
            totalLength += resultData.get(i).length;
        }

        byte[] result = new byte[totalLength];
        int position = 0;
        for (int i = 0; i < resultData.size(); i++) {
            byte[] current = resultData.get(i);
            System.arraycopy(current, 0, result, position, current.length);
            position += current.length;
        }

        return result;
    }

    private static byte[] reverseOrder(byte[] data) {
        int length = data.length;
        byte[] reversedData = new byte[length];
        for (int i = 0; i < length; i++) {
            reversedData[i] = data[length - 1 - i];
        }
        return reversedData;
    }
}