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

package com.smartbear.jenkins.plugins.testcomplete.parser;

import com.smartbear.jenkins.plugins.testcomplete.Messages;
import com.smartbear.jenkins.plugins.testcomplete.TcLog;
import com.smartbear.jenkins.plugins.testcomplete.TcLogInfo;
import com.smartbear.jenkins.plugins.testcomplete.Utils;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * @author Igor Filin
 */
public class LogParser2 implements ILogParser {

    private static final String SUMMARY_ENTRY_NAME = "Summary.dat";
    private static final String DESCRIPTION_ENTRY_NAME = "Description.tcLog";

    private static final String TEST_COUNT_PROPERTY_NAME = "test count";
    private static final String START_TIME_PROPERTY_NAME = "start time";
    private static final String STOP_TIME_PROPERTY_NAME = "stop time";
    private static final String ERROR_COUNT_PROPERTY_NAME = "error count";
    private static final String WARNING_COUNT_PROPERTY_NAME = "warning count";

    private static final String UNEXPECTED_LOG_FORMAT = "Unexpected log format";


    private final ParserSettings context;

    public LogParser2(ParserSettings context) {
        this.context = context;
    }

    private boolean checkIncomplete(String status) {
        return "3".equals(status);
    }

    private boolean checkFail(String status) {
        if (context.errorOnWarnings()) {
            return !"0".equals(status);
        }
        return !"0".equals(status) && !"1".equals(status);
    }

    private String fixTestCaseName(String name) {
        return name.replace(" Log [", " [");
    }

    private TcLogInfo extractBaseLogInfo(ZipFile logArchive) throws ParsingException {
        Node descriptionTopLevelNode = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive, DESCRIPTION_ENTRY_NAME);
        if (descriptionTopLevelNode == null) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        long startTime = Utils.safeConvertDate(LogNodeUtils.getTextProperty(descriptionTopLevelNode, START_TIME_PROPERTY_NAME));
        long stopTime = Utils.safeConvertDate(LogNodeUtils.getTextProperty(descriptionTopLevelNode, STOP_TIME_PROPERTY_NAME));

        int testCount;
        try {
            testCount = Integer.parseInt(LogNodeUtils.getTextProperty(descriptionTopLevelNode, TEST_COUNT_PROPERTY_NAME));
        } catch (NumberFormatException e) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        int warningCount;
        try {
            warningCount = Integer.parseInt(LogNodeUtils.getTextProperty(descriptionTopLevelNode, WARNING_COUNT_PROPERTY_NAME));
        } catch (NumberFormatException e) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        int errorCount;
        try {
            errorCount = Integer.parseInt(LogNodeUtils.getTextProperty(descriptionTopLevelNode, ERROR_COUNT_PROPERTY_NAME));
        } catch (NumberFormatException e) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        return new TcLogInfo(startTime, stopTime, testCount, errorCount, warningCount);
    }

    private void convertSingleEntryToXML(ZipFile logArchive, TcLogInfo logInfo, XMLStreamWriter writer) throws ParsingException, XMLStreamException {
        writer.writeStartDocument("utf-8", "1.0");

        Node descriptionTopLevelNode = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive, DESCRIPTION_ENTRY_NAME);
        if (descriptionTopLevelNode == null) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        Node topLevelNode = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive,
                LogNodeUtils.getTextProperty(descriptionTopLevelNode, "root file name"));

        if (topLevelNode == null) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        NodeList rootNodes = topLevelNode.getChildNodes();
        Node rootOwnerNode = LogNodeUtils.findRootOwnerNode(rootNodes);

        if (rootOwnerNode == null) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        Node rootOwnerNodeInfo = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive,
                LogNodeUtils.getTextProperty(rootOwnerNode, "filename"));

        if (rootOwnerNodeInfo == null) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        writer.writeStartElement("testsuites");

        String testCaseName = LogNodeUtils.getTextProperty(rootOwnerNode, "name");
        String testCaseDuration = Double.toString(logInfo.getTestDuration() / 1000f);

        writer.writeStartElement("testsuite");
        writer.writeAttribute("name", context.getProject());
        writer.writeAttribute("time", testCaseDuration);

        writer.writeStartElement("testcase");
        writer.writeAttribute("name", fixTestCaseName(testCaseName));
        writer.writeAttribute("classname", context.getSuite() + "." + context.getProject());
        writer.writeAttribute("time", testCaseDuration);

        if (checkFail(LogNodeUtils.getTextProperty(rootOwnerNode, "status"))) {
            writer.writeStartElement("failure");

            List<String> messages = LogNodeUtils.getErrorMessages(rootOwnerNodeInfo);
            if (context.errorOnWarnings()) {
                messages.addAll(LogNodeUtils.getWarningMessages(rootOwnerNodeInfo));
            }

            writer.writeAttribute("message", StringUtils.join(messages, "\n\n"));
            writer.writeEndElement(); //failure
        }

        writer.writeEndElement(); //testcase
        writer.writeEndElement(); //testsuite
        writer.writeEndElement(); //testsuites
        writer.writeEndDocument();
    }

    private void convertSummaryToXML(Node summaryNode, XMLStreamWriter writer) throws ParsingException, XMLStreamException {

        writer.writeStartDocument("utf-8", "1.0");
        writer.writeStartElement("testsuites");

        Node projectsNode = LogNodeUtils.findNamedNode(summaryNode, "projects");

        if (projectsNode == null) {
            throw new ParsingException(UNEXPECTED_LOG_FORMAT);
        }

        List<Node> projectNodes = LogNodeUtils.findChildNodes(projectsNode);

        for (Node projectNode : projectNodes) {
            String failedTests = LogNodeUtils.getTextProperty(projectNode, "failedtests");
            if (failedTests == null) {
                failedTests = Integer.toString(0);
            }

            String testProjectName = LogNodeUtils.getTextProperty(projectNode, "name");
            String testStartTime = LogNodeUtils.getTextProperty(projectNode, "starttime");
            Node testsNode = LogNodeUtils.findNamedNode(projectNode, "tests");

            if (testsNode == null) {
                throw new ParsingException(UNEXPECTED_LOG_FORMAT);
            }

            List<Node> testNodes = LogNodeUtils.findChildNodes(testsNode);

            String projectDurationMS = LogNodeUtils.getTextProperty(projectNode, "duration");
            String projectDuration = Double.toString(Integer.parseInt(projectDurationMS) / 1000f);

            writer.writeStartElement("testsuite");
            writer.writeAttribute("name", testProjectName);
            writer.writeAttribute("time", projectDuration);

            writer.writeAttribute("failures", failedTests);
            writer.writeAttribute("tests", Integer.toString(testNodes.size()));
            writer.writeAttribute("timestamp", LogNodeUtils.startTimeToTimestamp(testStartTime));

            for (Node testNode : testNodes) {
                String testName = LogNodeUtils.getTextProperty(testNode, "name");

                writer.writeStartElement("testcase");
                writer.writeAttribute("name", testName);
                writer.writeAttribute("classname", context.getSuite() + "." + testProjectName);

                String testDurationMS = LogNodeUtils.getTextProperty(testNode, "duration");
                String testDuration = Double.toString(Integer.parseInt(testDurationMS) / 1000f);

                writer.writeAttribute("time", testDuration);

                String testCaseStatus = LogNodeUtils.getTextProperty(testNode, "status");

                if (checkIncomplete(testCaseStatus)) {
                    writer.writeStartElement("skipped");
                    writer.writeEndElement(); //skipped
                } else if (checkFail(testCaseStatus)) {
                    writer.writeStartElement("failure");

                    List<String> messages = new ArrayList<>();

                    List<String> errors = LogNodeUtils.findChildMessages(testNode, "errors", "error");
                    messages.addAll(errors);

                    if (context.errorOnWarnings()) {
                        List<String> warnings = LogNodeUtils.findChildMessages(testNode, "warnings", "warning");
                        messages.addAll(warnings);
                    }

                    writer.writeAttribute("message", StringUtils.join(messages, "\n\n"));
                    writer.writeEndElement(); //failure
                }

                writer.writeEndElement(); //testcase
            }

            writer.writeEndElement(); //testsuite
        }

        writer.writeEndElement(); //testsuites
        writer.writeEndDocument();
    }

    @Override
    public TcLogInfo parse(TaskListener listener) {
        try {
            ZipFile logArchive = new ZipFile(context.getLog());
            TcLogInfo logInfo = extractBaseLogInfo(logArchive);

            String xml = null;

            if (context.generateJUnitReports()) {
                XMLStreamWriter xmlStreamWriter = null;
                try {
                    Node summaryNode = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive, SUMMARY_ENTRY_NAME);
                    StringWriter stringWriter = new StringWriter();
                    xmlStreamWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter);

                    if (summaryNode != null) {
                        convertSummaryToXML(summaryNode, xmlStreamWriter);
                    } else {
                        convertSingleEntryToXML(logArchive, logInfo, xmlStreamWriter);
                    }

                    xmlStreamWriter.flush();
                    xmlStreamWriter.close();
                    xmlStreamWriter = null;
                    xml = stringWriter.toString();
                } finally {
                    if (xmlStreamWriter != null) {
                        xmlStreamWriter.close();
                    }
                }
            }

            logInfo.setXML(xml);

            return logInfo;
        }
        catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), stringWriter.toString());
            return null;
        }
    }

}