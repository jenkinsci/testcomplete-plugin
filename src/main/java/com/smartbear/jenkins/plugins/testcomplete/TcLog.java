package com.smartbear.jenkins.plugins.testcomplete;

import hudson.model.BuildListener;

/**
 * @author Igor Filin
 */
public class TcLog {

    public static void info(BuildListener listener, String message, Object ... args) {
        String stringMessage = "[" + Constants.LOG_PREFIX + "] " + String.format(message, args);
        listener.getLogger().println(stringMessage);
    }

    public static void warning(BuildListener listener, String message, Object ... args) {
        String stringMessage = "[" + Constants.LOG_PREFIX + "] [WARNING] " + String.format(message, args);
        listener.getLogger().println(stringMessage);
    }

    public static void error(BuildListener listener, String message, Object ... args) {
        String stringMessage = "[" + Constants.LOG_PREFIX + "] [ERROR] " + String.format(message, args);
        listener.getLogger().println(stringMessage);
    }

}