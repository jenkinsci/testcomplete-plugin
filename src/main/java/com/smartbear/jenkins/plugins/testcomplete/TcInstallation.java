package com.smartbear.jenkins.plugins.testcomplete;

import java.io.File;
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
        lcItem
    }

    public static enum ExecutorType {
        TC,
        TE
    }

    private final static int VERSION_PARTS = 3;

    private final String path;
    private final ExecutorType type;
    private final String version;

    public TcInstallation(String path, ExecutorType type, String version) {
        this.path = path;
        this.type = type;
        this.version = version;
    }

    public String getPath() {
        return path;
    }

    public String getServicePath() {
        String tcBinDirPath = path.substring(0, path.lastIndexOf("\\"));
        return tcBinDirPath + "\\" + String.format(Constants.TC_SERVICE_EXEC_NAME, getMajorVersion());
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

    @Override
    public String toString() {
        return String.format(Messages.TcInstallation_InstallationString(), getType(), getVersion(), getPath());
    }

}