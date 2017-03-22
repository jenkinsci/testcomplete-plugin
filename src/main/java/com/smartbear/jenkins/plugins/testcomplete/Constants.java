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
public class Constants {

    public static final String TC_NAME = "TestComplete";
    public static final String TE_NAME = "TestExecute";
    public static final String TC_SERVICE_EXEC_NAME = "TestCompleteService%d.exe";

    public static final String PLUGIN_NAME = "TestComplete";
    public static final String LOG_PREFIX = "TestComplete";
    public static final String REPORTS_DIRECTORY_NAME = "tcreports";
    public static final String LOGX_FILE_EXTENSION = ".tclogx";
    public static final String HTMLX_FILE_EXTENSION = ".htmlx";
    public static final String MHT_FILE_EXTENSION = ".mht";
    public static final String ERROR_FILE_EXTENSION = ".txt";
    public static final String ANY_CONSTANT = "any";

    public static final String SERVICE_ARG = "//LogonAndExecute";
    public static final String SERVICE_ARG_DOMAIN = "//lDomain:";
    public static final String SERVICE_ARG_NAME = "//lName:";
    public static final String SERVICE_ARG_PASSWORD = "//lPassword:";
    public static final String SERVICE_ARG_TIMEOUT = "//lTimeout:";
    public static final String SERVICE_ARG_USE_ACTIVE_SESSION = "//lUseActiveSession:";
    public static final String SERVICE_ARG_SCREEN_WIDTH = "//lSessionScreenWidth:";
    public static final String SERVICE_ARG_SCREEN_HEIGHT = "//lSessionScreenHeight:";
    public static final String SERVICE_ARG_COMMAND_LINE = "//lCommandLine:";

    public static final int WAITING_AFTER_TIMEOUT_INTERVAL = 300;
    public static final int SERVICE_INTERVAL_DELAY = 180;

    public static final String DEFAULT_CHARSET_NAME = "UTF-8";

}