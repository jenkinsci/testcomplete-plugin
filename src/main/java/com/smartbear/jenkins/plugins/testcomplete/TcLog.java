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

    public static void debug(BuildListener listener, String message, Object ... args) {
        String stringMessage = "[" + Constants.LOG_PREFIX + "] [DEBUG] " + String.format(message, args);
        listener.getLogger().println(stringMessage);
    }

}