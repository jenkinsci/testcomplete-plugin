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

import hudson.model.TaskListener;

/**
 * @author Igor Filin
 */
public class TcLog {

    private static String formatMessage(String marker, String format, Object... args) {
        return "[" + Constants.LOG_PREFIX + "] " + marker + String.format(format, args);
    }

    public static void info(TaskListener listener, String message, Object ... args) {
        listener.getLogger().println(formatMessage("", message, args));
    }

    public static void warning(TaskListener listener, String message, Object ... args) {
        listener.getLogger().println(formatMessage("[WARNING] ", message, args));
    }

    public static void error(TaskListener listener, String message, Object ... args) {
        listener.getLogger().println(formatMessage("[ERROR] ", message, args));
    }

    public static void debug(TaskListener listener, String message, Object ... args) {
        listener.getLogger().println(formatMessage("[DEBUG] ", message, args));
    }

}