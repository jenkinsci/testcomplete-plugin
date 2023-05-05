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

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.text.DateFormat;
import java.util.Date;

/**
 * @author Igor Filin
 */
@ExportedBean
public class TcLogInfo implements Serializable {

 	private static final long serialVersionUID = 1758311907560159547L;
	private final long startTime;
    private final long stopTime;
    private final long testDuration;

    private final int testCount;
    private final int errorCount;
    private final int warningCount;

    private String XML = null;

    public TcLogInfo(long startTime, long stopTime, int testCount, int errorCount, int warningCount) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        testDuration = stopTime > startTime ? stopTime - startTime : 0;
        this.testCount = testCount;
        this.errorCount = errorCount;
        this.warningCount = warningCount;
    }

    @Exported(name="timestamp")
    public long getStartTime() {
        return startTime;
    }

    public long getStopTime() {
        return stopTime;
    }

    @Exported(name="duration")
    public long getTestDuration() {
        return testDuration;
    }

    public int getTestCount() {
        return testCount;
    }

    @Exported(name="errors")
    public int getErrorCount() {
        return errorCount;
    }

    @Exported(name="warnings")
    public int getWarningCount() {
        return warningCount;
    }

    @SuppressWarnings("unused")
    public String formatStartTime() {
        Date date = new Date(startTime);
        return DateFormat.getDateTimeInstance().format(date);
    }

    @SuppressWarnings("unused")
    public String formatStopTime() {
        Date date = new Date(stopTime);
        return DateFormat.getDateTimeInstance().format(date);
    }

    @SuppressWarnings("unused")
    public String formatTestDuration() {
        long timeInSeconds = testDuration / 1000;
        long s = timeInSeconds % 60;
        long m = (timeInSeconds / 60) % 60;
        long h = timeInSeconds / (60 * 60);
        return String.format("%d:%02d:%02d", h,m,s);
    }

    public String getXML() {
        return XML;
    }

    public void setXML(String XML) {
        this.XML = XML;
    }
}