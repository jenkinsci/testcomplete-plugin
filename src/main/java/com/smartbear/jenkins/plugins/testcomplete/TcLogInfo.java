package com.smartbear.jenkins.plugins.testcomplete;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import java.text.DateFormat;
import java.util.Date;

/**
 * @author Igor Filin
 */
@ExportedBean
public class TcLogInfo {

    private final long startTime;
    private final long stopTime;
    private final long testDuration;

    private final int testCount;
    private final int errorCount;
    private final int warningCount;

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

    public String formatStartTime() {
        Date date = new Date(startTime);
        return DateFormat.getDateTimeInstance().format(date);
    }

    public String formatStopTime() {
        Date date = new Date(stopTime);
        return DateFormat.getDateTimeInstance().format(date);
    }

    public String formatTestDuration() {
        long timeInSeconds = testDuration / 1000;
        long s = timeInSeconds % 60;
        long m = (timeInSeconds / 60) % 60;
        long h = timeInSeconds / (60 * 60);
        return String.format("%d:%02d:%02d", h,m,s);
    }

}