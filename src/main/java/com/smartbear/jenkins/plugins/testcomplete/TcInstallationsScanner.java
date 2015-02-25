package com.smartbear.jenkins.plugins.testcomplete;

import hudson.model.BuildListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.util.jna.JnaException;
import hudson.util.jna.RegistryKey;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Igor Filin
 */
public class TcInstallationsScanner implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int REGISTRY_KEY_WOW64_32KEY = 0x0200;
    private static final int REGISTRY_KEY_READ = 0x20019;

    private final VirtualChannel channel;
    private final BuildListener listener;

    public TcInstallationsScanner(VirtualChannel channel, BuildListener listener) {
        this.channel = channel;
        this.listener = listener;
    }

    private static class ScannerCallable implements Callable<List<TcInstallation>, Exception>, Serializable {

        private static final long serialVersionUID = 1L;
        private static final String registryKey = "SOFTWARE\\SmartBear\\";

        ScannerCallable() {

        }

        public List<TcInstallation> call() throws Exception {
            List<TcInstallation> result = new ArrayList<TcInstallation>();
            scanForInstallations(result, "TestComplete", TcInstallation.ExecutorType.TC);
            scanForInstallations(result, "TestExecute", TcInstallation.ExecutorType.TE);
            return result;
        }

        private void scanForInstallations(List<TcInstallation> result,
                                          String executor,
                                          TcInstallation.ExecutorType type) {

            String key = registryKey + executor + "\\";
            RegistryKey executorKey = null;

            try {
                executorKey = RegistryKey.LOCAL_MACHINE.open(key, REGISTRY_KEY_READ | REGISTRY_KEY_WOW64_32KEY);
                Collection<String> installKeys = executorKey.getSubKeys();

                if (installKeys != null && installKeys.size() > 0) {
                    for (String versionKey : installKeys) {
                        if (!versionKey.matches("^[0-9]{1,3}([.][0-9]{1,5})+$")) {
                            continue;
                        }

                        RegistryKey setupKey = null;
                        try {
                            setupKey = executorKey.open(versionKey + "\\Setup\\", REGISTRY_KEY_READ | REGISTRY_KEY_WOW64_32KEY);
                            String path = setupKey.getStringValue("Product path");
                            String version = setupKey.getStringValue("Version");

                            if (version == null || !version.matches("^[0-9]{1,3}([.][0-9]{1,5})+$")) {
                                continue;
                            }

                            if (path != null && !path.isEmpty()) {
                                String executorPath = path + "bin\\" + executor + ".exe";
                                if (new File(executorPath).exists()) {
                                    result.add(new TcInstallation(executorPath, type, version));
                                }
                            }
                        } catch (JnaException e) {
                            // Do nothing
                        } finally {
                            if (setupKey != null)
                                setupKey.dispose();
                        }
                    }
                }
            } catch (JnaException e) {
                // Do nothing
            } finally {
                if (executorKey != null) {
                    executorKey.dispose();
                }
            }
        }
    }

    public List<TcInstallation> getInstallations() {
        List<TcInstallation> result = null;

        try {
            result = channel.call(new ScannerCallable());
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_RemoteCallingFailed(), e.getCause().getMessage());
        }

        return result != null ? result : new LinkedList<TcInstallation>();
    }

    public TcInstallation findInstallation(List<TcInstallation> installations, String executorType, String executorVersion) {
        TcInstallation result = null;

        for (TcInstallation one : installations) {
            if (!executorType.equals(Constants.ANY_CONSTANT) &&
                    one.getType() != TcInstallation.ExecutorType.valueOf(executorType)) {
                continue;
            }

            if (!executorVersion.equals(Constants.ANY_CONSTANT) &&
                    one.compareVersion(executorVersion, true) != 0) {
                continue;
            }

            if (result == null) {
                result = one;
                continue;
            }

            if (one.compareVersion(result.getVersion(), false) > 0) {
                result = one;
                continue;
            }

            if (result.compareVersion(one.getVersion(), false) == 0 &&
                    one.getType() == TcInstallation.ExecutorType.TE) {
                result = one;
            }
        }

        return result;
    }

}