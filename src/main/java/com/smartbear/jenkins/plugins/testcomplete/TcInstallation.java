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

import java.io.Serializable;

/**
 * @author Igor Filin
 */
public class TcInstallation implements Serializable{

    public static enum LaunchType {
        lcSuite,
        lcProject,
        lcRoutine,
        lcKdt,
        lcItem,
        lcCBT
    }

    public static enum ExecutorType {
        TC,
        TE
    }

    private final static int VERSION_PARTS = 3;

    private final String rootBinPath;
    private final String executorPath;
    private final ExecutorType type;
    private final String version;

    public TcInstallation(String rootBinPath, String executorPath, ExecutorType type, String version) {
        this.rootBinPath = rootBinPath;
        this.executorPath = executorPath;
        this.type = type;
        this.version = version;
    }

    public String getExecutorPath() {
        return executorPath;
    }

    public String getServicePath() {
        return rootBinPath + "\\" + String.format(Constants.TC_SERVICE_EXEC_NAME, getMajorVersion());
    }

    public ExecutorType getType() {
        return type;
    }

    public String getVersion() {
        return version;
    }

    public int getMajorVersion() {
        String[] parts = version.split("[.]");
        return Integer.parseInt(parts[0]);
    }

    public int compareVersion(String anotherVersion, boolean majorOnly) {

        int[] selfVersionParts = new int[VERSION_PARTS];
        for (int i = 0; i < VERSION_PARTS; i++) {
            selfVersionParts[i] = 0;
        }

        int[] anotherVersionParts = new int[VERSION_PARTS];
        for (int i = 0; i < VERSION_PARTS; i++) {
            anotherVersionParts[i] = 0;
        }

        String[] selfSplit = version.split("[.]");
        for (int i = 0; i < Math.min(VERSION_PARTS, selfSplit.length); i++) {
            selfVersionParts[i] = Integer.parseInt(selfSplit[i]);
        }

        String[] anotherSplit = anotherVersion.split("[.]");
        for (int i = 0; i < Math.min(VERSION_PARTS, anotherSplit.length); i++) {
            anotherVersionParts[i] = Integer.parseInt(anotherSplit[i]);
        }

        for (int i = 0; i < (majorOnly ? 1 : VERSION_PARTS); i++) {
            if (selfVersionParts[i] > anotherVersionParts[i]) {
                return 1;
            } else if (selfVersionParts[i] < anotherVersionParts[i]) {
                return -1;
            }
        }

        return 0;
    }

    public boolean isServiceLaunchingAvailable() {
        return compareVersion("10.60", false) >= 0;
    }

    public boolean isCustomScreenResolutionSupported() {
        return compareVersion("12.30", false) >= 0;
    }

    @Override
    public String toString() {
        return String.format(Messages.TcInstallation_InstallationString(), getType(), getVersion(), getExecutorPath());
    }

}