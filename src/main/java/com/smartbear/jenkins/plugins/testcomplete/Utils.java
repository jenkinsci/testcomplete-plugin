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

import hudson.PluginWrapper;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.RoleChecker;

import javax.crypto.Cipher;
import java.lang.ref.WeakReference;
import java.security.Key;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;

public class Utils {

    private static final String PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDCD43scUktBOFoR10dS80DbFJf" +
        "MgJoyNGtfxVyQ6DKwmzb1OS+P3E5Y47K3G6fXX8OfhT0WmQ/Aqr61nUXxRgn2cFH" +
        "Kyc4rjFjfMTkPGkv7rWdIuu+4VR9PYEXar4OyCQEThfhdDSPzfHJ8oiPNqkXe5IY" +
        "L1xQevURO0+Sapzf7wIDAQAB";

    private static final int ENC_CHUNK_MAX_SIZE = 116;

    private Utils() {
    }

    static boolean isWindows(VirtualChannel channel, TaskListener listener) {
        try {
            return channel.call(new Callable<Boolean, Exception>() {

                private static final long serialVersionUID = -6109926297806624006L;

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Boolean call() {
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

    static boolean IsLaunchedAsSystemUser(VirtualChannel channel, final TaskListener listener) {
        try {
            return channel.call(new Callable<Boolean, Exception>() {

                private static final long serialVersionUID = -7887444820720775808L;

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Boolean call() {

                    // Trying to check whether we are running on System account

                    String winDir = System.getenv("WINDIR");
                    if (winDir == null) {
                        return false;
                    }

                    String userProfile = System.getenv("USERPROFILE");
                    if (userProfile == null) {
                        return false;
                    }

                    return userProfile.startsWith(winDir);
                }

            });
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e);
            return false;
        }
    }

    static long getSystemTime(VirtualChannel channel, TaskListener listener) {
        try {
            return channel.call(new Callable<Long, Exception>() {

                private static final long serialVersionUID = -8337586169108934130L;

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Long call() {
                    return System.currentTimeMillis();
                }

            });
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e);
            return 0;
        }
    }

    static int getTimezoneOffset(VirtualChannel channel, TaskListener listener) {
        try {
            return channel.call(new Callable<Integer, Exception>() {

                 private static final long serialVersionUID = 1585057738074873637L;

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Integer call() {
                    Calendar now = Calendar.getInstance();
                    TimeZone timeZone = now.getTimeZone();
                    return timeZone.getRawOffset();
                }

            });
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e);
            return 0;
        }
    }

    public static long safeConvertDate(String oleDate) {
        double dateToConvert = 0f;
        try {
            dateToConvert = Double.parseDouble(oleDate);
        } catch (NumberFormatException e) {
            // Do nothing
        }
        return OLEDateToMillis(dateToConvert);
    }

    private static long OLEDateToMillis(double dSerialDate)
    {
        return (long) ((dSerialDate - 25569) * 24 * 3600 * 1000);
    }

    static String encryptPassword(String password) throws Exception {
        byte[] keyRawData = Base64.getDecoder().decode(PUBLIC_KEY);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        KeySpec ks = new X509EncodedKeySpec(keyRawData);
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(ks);
        byte[] encryptedData = encrypt(password, publicKey);

        return Base64.getEncoder().encodeToString(encryptedData);
    }

    private static byte[] encrypt(String data, Key publicKey) throws Exception {
        ArrayList<byte[]> resultData = new ArrayList<>();

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

    public static String getPluginVersionOrNull() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }

        for (PluginWrapper plugin : jenkins.pluginManager.getPlugins()) {
            String name = plugin.getShortName();
            if (name != null) {
                if (name.equals(Constants.PLUGIN_NAME)) {
                    return plugin.getVersion().split("[ ]")[0];
                }
            }
        }

        return null;
    }

    public static class BusyNodeList {

        private Map<WeakReference<Computer>, Semaphore> computerLocks = new HashMap<>();

        public void lock(Computer computer, TaskListener listener) throws InterruptedException {
            Semaphore semaphore = null;
            synchronized (this) {
                for (Entry<WeakReference<Computer>, Semaphore> computerRef : computerLocks.entrySet()) {
                    Computer actualComputer = computerRef.getKey().get();
                    if (actualComputer != null && actualComputer == computer) {
                        semaphore = computerRef.getValue();
                    }
                }

                if (semaphore == null) {
                    semaphore = new Semaphore(1, true);
                    computerLocks.put(new WeakReference<>(computer), semaphore);
                } else {
                    listener.getLogger().println();
                    TcLog.info(listener, Messages.TcTestBuilder_WaitingForNodeRelease());
                }
            }

            semaphore.acquire();
        }

        public void release(Computer computer) throws InterruptedException {
            Semaphore semaphore = null;
            synchronized (this) {
                for (Entry<WeakReference<Computer>,Semaphore> computerRef : computerLocks.entrySet()) {
                    Computer actualComputer = computerRef.getKey().get();
                    if (actualComputer != null && actualComputer == computer) {
                        semaphore = computerRef.getValue();
                    }
                }
            }
            if (semaphore != null) {
                semaphore.release();
            }

            Thread.sleep(200);

            // cleanup the unused items
            synchronized (this) {
                List<WeakReference<Computer>> toRemove = new ArrayList<>();

                for (Entry<WeakReference<Computer>, Semaphore> computerRef : computerLocks.entrySet()) {
                    Computer actualComputer = computerRef.getKey().get();
                    if (actualComputer != null && actualComputer == computer) {
                        semaphore = computerRef.getValue();
                        if (semaphore.availablePermits() > 0) {
                            toRemove.add(computerRef.getKey());
                        }
                    }
                }

                for (WeakReference<Computer> computerRef : toRemove) {
                    computerLocks.remove(computerRef);
                }
            }
        }
    }
}