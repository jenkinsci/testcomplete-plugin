package com.smartbear.jenkins.plugins.testcomplete;

/**
 * @author Igor Filin
 */
public class Constants {

    public static final String TC_NAME = "TestComplete";
    public static final String TE_NAME = "TestExecute";
    public static final String TC_SERVICE_EXEC_NAME = "TestCompleteService%d.exe";

    public static final String PLUGIN_NAME = "TestComplete";
    public static final String LOG_PREFIX = "TestComplete";
    public static final String REPORTS_DIRECTORY_NAME = "tcreports";
    public static final String LOGX_FILE_EXTENSION = ".tclogx";
    public static final String HTMLX_FILE_EXTENSION = ".htmlx";
    public static final String ERROR_FILE_EXTENSION = ".txt";
    public static final String ANY_CONSTANT = "any";

    public static final String SERVICE_ARG = "//LogonAndExecute";
    public static final String SERVICE_ARG_DOMAIN = "//lDomain:";
    public static final String SERVICE_ARG_NAME = "//lName:";
    public static final String SERVICE_ARG_PASSWORD = "//lPassword:";
    public static final String SERVICE_ARG_TIMEOUT = "//lTimeout:";
    public static final String SERVICE_ARG_USE_ACTIVE_SESSION = "//lUseActiveSession:";
    public static final String SERVICE_ARG_COMMAND_LINE = "//lCommandLine:";

    public static final int WAITING_AFTER_TIMEOUT_INTERVAL = 300;
    public static final int SERVICE_INTERVAL_DELAY = 180;

}