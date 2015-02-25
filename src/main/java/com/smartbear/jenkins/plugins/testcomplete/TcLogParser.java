package com.smartbear.jenkins.plugins.testcomplete;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Igor Filin
 */
public class TcLogParser {

    private static final String DESCRIPTION_ENTRY_NAME = "Description.tcLog";

    private static final Pattern namePattern = Pattern.compile("name=\".*?\"");
    private static final Pattern valuePattern = Pattern.compile("value=\".*?\"");

    private static final String TEST_COUNT_PROPERTY_NAME = "test count";
    private static final String START_TIME_PROPERTY_NAME = "start time";
    private static final String STOP_TIME_PROPERTY_NAME = "stop time";
    private static final String ERROR_COUNT_PROPERTY_NAME = "error count";
    private static final String WARNING_COUNT_PROPERTY_NAME = "warning count";

    private final File log;

    public TcLogParser(File log) {
        this.log = log;
    }

    public TcLogInfo parse() {
        try {
            ZipFile logArchive = new ZipFile(log);

            ZipEntry descriptionEntry = logArchive.getEntry(DESCRIPTION_ENTRY_NAME);
            if (descriptionEntry == null) {
                return null;
            }

            byte[] rawData = new byte[(int)descriptionEntry.getSize()];
            InputStream stream = logArchive.getInputStream(descriptionEntry);
            int read = stream.read(rawData);
            if (read != rawData.length) {
                return null;
            }

            String content = new String(rawData);

            if (!checkHeader(content)) {
                return null;
            }

            Map<String, String> properties = getAllProperties(content);

            long startTime = 0;
            try {
                double value = Double.parseDouble(properties.get(START_TIME_PROPERTY_NAME));
                startTime = Utils.OLEDateToMillis(value);
            } catch (NumberFormatException e) {
                // Do nothing
            }

            long stopTime = 0;
            try {
                double value = Double.parseDouble(properties.get(STOP_TIME_PROPERTY_NAME));
                stopTime = Utils.OLEDateToMillis(value);
            } catch (NumberFormatException e) {
                // Do nothing
            }

            int testCount = 0;
            try {
                testCount = Integer.parseInt(properties.get(TEST_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            int warningCount = 0;
            try {
                warningCount = Integer.parseInt(properties.get(WARNING_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            int errorCount = 0;
            try {
                errorCount = Integer.parseInt(properties.get(ERROR_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            return new TcLogInfo(startTime, stopTime, testCount, errorCount, warningCount);
        }
        catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> getAllProperties(String content) {

        Map<String, String> result = new HashMap<String, String>();

        Matcher matcher = Pattern.compile("<Prp.*?/>").matcher(content);
        while (matcher.find()) {
            String group = matcher.group();

            Matcher nameMatcher = namePattern.matcher(group);
            if (!nameMatcher.find()) {
                continue;
            }
            String name = nameMatcher.group();

            String[] nameParts = name.split("=");
            if (nameParts.length < 2) {
                continue;
            }

            name = nameParts[1].trim();
            name = name.substring(1, name.length() - 1);

            Matcher valueMatcher = valuePattern.matcher(group);
            if (!valueMatcher.find()) {
                continue;
            }
            String value = valueMatcher.group();

            String[] valueParts = value.split("=");
            if (valueParts.length < 2) {
                continue;
            }

            value = valueParts[1].trim();
            value = value.substring(1, value.length() - 1);

            result.put(name, value);
        }

        return result;
    }

    private boolean checkHeader(String content) {
        return Pattern.compile("<\\?xml version=\"1.0\" encoding=\"UTF-8\" .*?>")
                .matcher(content).find();
    }

}