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
import org.w3c.dom.*;

import javax.xml.bind.DatatypeConverter;
import javax.xml.stream.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * @author Igor Filin
 */
public class LogParser implements ILogParser {

    private static final String DESCRIPTION_ENTRY_NAME = "Description.tcLog";

    private static final String TEST_COUNT_PROPERTY_NAME = "test count";
    private static final String START_TIME_PROPERTY_NAME = "start time";
    private static final String STOP_TIME_PROPERTY_NAME = "stop time";
    private static final String ERROR_COUNT_PROPERTY_NAME = "error count";
    private static final String WARNING_COUNT_PROPERTY_NAME = "warning count";

    private final ParserSettings context;
    private final int timezoneOffset;

    public LogParser(ParserSettings context, int timezoneOffset) {
        this.context = context;
        this.timezoneOffset = timezoneOffset;
    }

    @Override
    public TcLogInfo parse(TaskListener listener) {
        try {
            ZipFile logArchive = new ZipFile(context.getLog());

            Node descriptionTopLevelNode = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive, DESCRIPTION_ENTRY_NAME);
            if (descriptionTopLevelNode == null) {
                throw new ParsingException("Unable to obtain description top-level node.");
            }

            long startTime = Utils.safeConvertDate(LogNodeUtils.getTextProperty(descriptionTopLevelNode, START_TIME_PROPERTY_NAME));
            if (startTime > 0) {
                startTime -= timezoneOffset;
            }

            long stopTime = Utils.safeConvertDate(LogNodeUtils.getTextProperty(descriptionTopLevelNode, STOP_TIME_PROPERTY_NAME));
            if (stopTime > 0) {
                stopTime -= timezoneOffset;
            }

            int testCount = 0;
            try {
                testCount = Integer.parseInt(LogNodeUtils.getTextProperty(descriptionTopLevelNode, TEST_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            int warningCount = 0;
            try {
                warningCount = Integer.parseInt(LogNodeUtils.getTextProperty(descriptionTopLevelNode, WARNING_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            int errorCount = 0;
            try {
                errorCount = Integer.parseInt(LogNodeUtils.getTextProperty(descriptionTopLevelNode, ERROR_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            TcLogInfo logInfo = new TcLogInfo(startTime, stopTime, testCount, errorCount, warningCount);

            String xml = null;

            if (context.generateJUnitReports()) {
                XMLStreamWriter xmlStreamWriter = null;
                try {
                    StringWriter stringWriter = new StringWriter();
                    xmlStreamWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter);
                    convertToXML(logArchive, logInfo, xmlStreamWriter);
                    xmlStreamWriter.flush();
                    xmlStreamWriter.close();
                    xmlStreamWriter = null;
                    xml = stringWriter.toString();
                } catch (Exception e) {
                    TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
                } finally {
                    if (xmlStreamWriter != null) {
                        xmlStreamWriter.close();
                    }
                }
            }

            logInfo.setXML(xml);

            return logInfo;
        } catch ( IOException 
                | FactoryConfigurationError
                | XMLStreamException e) {

            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
            return null;
        }
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

    private void convertToXML(ZipFile logArchive, TcLogInfo logInfo, XMLStreamWriter writer)
            throws ParsingException, XMLStreamException {
        writer.writeStartDocument("utf-8", "1.0");

        Node descriptionTopLevelNode = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive, DESCRIPTION_ENTRY_NAME);
        if (descriptionTopLevelNode == null) {
            throw new ParsingException("Unable to obtain description top-level node.");
        }

        Node topLevelNode = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive,
                LogNodeUtils.getTextProperty(descriptionTopLevelNode, "root file name"));

        if (topLevelNode == null) {
            throw new ParsingException("Unable to obtain root top-level node.");
        }

        NodeList rootNodes = topLevelNode.getChildNodes();
        Node rootOwnerNode = LogNodeUtils.findRootOwnerNode(rootNodes);

        if (rootOwnerNode == null) {
            throw new ParsingException("Unable to obtain root owner node.");
        }

        boolean isSuite = "{00000000-0000-0000-0000-000000000000}".
                equals(LogNodeUtils.getTextProperty(rootOwnerNode, "projectkey"));

        Node rootOwnerNodeInfo = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive,
                LogNodeUtils.getTextProperty(rootOwnerNode, "filename"));

        if (rootOwnerNodeInfo == null) {
            throw new ParsingException("Unable to obtain root owner node info.");
        }

        Node rootOwnerNodeInfoSummary = LogNodeUtils.findNamedNode(rootOwnerNodeInfo.getChildNodes(), "summary");
        boolean isSuiteOrProject = rootOwnerNodeInfoSummary != null;

        writer.writeStartElement("testsuites");

        if (isSuite) {
            List<Node> projects = LogNodeUtils.findChildNodes(rootOwnerNode, rootOwnerNode.getParentNode().getChildNodes());
            for (Node projectNode : projects) {
                Node projectNodeInfo = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive,
                        LogNodeUtils.getTextProperty(projectNode, "filename"));
                Node projectNodeInfoSummary = LogNodeUtils.findNamedNode(projectNodeInfo, "summary");
                processProject(logArchive, projectNode, projectNodeInfoSummary, writer);
            }
        } else if (isSuiteOrProject) {
            processProject(logArchive, rootOwnerNode, rootOwnerNodeInfoSummary, writer);
        } else {
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
        }

        writer.writeEndElement(); //testsuites
        writer.writeEndDocument();
    }

    private void processItem(ZipFile logArchive, Node node, String projectName, XMLStreamWriter writer, String name)
            throws ParsingException, XMLStreamException {
        Node nodeInfo = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive,
                LogNodeUtils.getTextProperty(node, "filename"));

        if (nodeInfo == null) {
            throw new ParsingException("Unable to obtain item node info.");
        }

        Node logDataRowNode = LogNodeUtils.findNamedNode(LogNodeUtils.findNamedNode(nodeInfo, "log data"), "row0");
        if (logDataRowNode == null) {
            throw new ParsingException("Unable to obtain log data->row0 node for item with name '" + name + "'.");
        }

        writer.writeStartElement("testcase");
        writer.writeAttribute("name", name);
        writer.writeAttribute("classname", context.getSuite() + "." + projectName);

        long startTime = Utils.safeConvertDate(LogNodeUtils.getTextProperty(logDataRowNode, "start time"));
        long endTime = Utils.safeConvertDate(LogNodeUtils.getTextProperty(logDataRowNode, "end time"));
        long duration = endTime - startTime > 0 ? endTime - startTime : 0;

        writer.writeAttribute("time", Double.toString(duration / 1000f));

        if (checkFail(LogNodeUtils.getTextProperty(node, "status"))) {

            Node testDetailsNode = LogNodeUtils.getRootDocumentNodeFromArchive(logArchive,
                    LogNodeUtils.getTextProperty(logDataRowNode, "details"));
            writer.writeStartElement("failure");

            List<String> messages = LogNodeUtils.getErrorMessages(testDetailsNode);
            if (context.errorOnWarnings()) {
                messages.addAll(LogNodeUtils.getWarningMessages(testDetailsNode));
            }

            writer.writeAttribute("message", StringUtils.join(messages, "\n\n"));
            writer.writeEndElement(); //failure
        }
        writer.writeEndElement(); //testcase
    }

    private void processProject(ZipFile logArchive, Node rootOwnerNode, Node rootOwnerNodeInfoSummary, XMLStreamWriter writer)
            throws ParsingException, XMLStreamException {

        String totalTests = LogNodeUtils.getTextProperty(
                LogNodeUtils.findNamedNode(rootOwnerNodeInfoSummary.getChildNodes(), "total"), "total (sum)");
        String failedTests = LogNodeUtils.getTextProperty(
                LogNodeUtils.findNamedNode(rootOwnerNodeInfoSummary.getChildNodes(), "failed"), "total (sum)");
        long startDate = Utils.safeConvertDate(LogNodeUtils.getTextProperty(rootOwnerNodeInfoSummary, "start date"));
        long stopDate = Utils.safeConvertDate(LogNodeUtils.getTextProperty(rootOwnerNodeInfoSummary, "stop date"));
        long projectDuration = stopDate - startDate > 0 ? stopDate - startDate : 0;
        String projectName = LogNodeUtils.getTextProperty(rootOwnerNodeInfoSummary, "test");
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(startDate);
        String timestamp = DatatypeConverter.printDateTime(cal);

        String rootOwnerNodeFileName = LogNodeUtils.getTextProperty(rootOwnerNode, "filename");
        if (rootOwnerNodeFileName == null || rootOwnerNodeFileName.isEmpty()) {
            throw new ParsingException("Unable to obtain filename for project node.");
        }

        List<LogNodeUtils.Pair<String, Node>> items;

        try {
            items = LogNodeUtils.findChildNodesRecursively(logArchive, rootOwnerNode,
                    rootOwnerNode.getParentNode().getChildNodes(), "");
        } catch (Exception e) {
            items = new ArrayList<>();
        }

        writer.writeStartElement("testsuite");
        writer.writeAttribute("name", projectName);
        writer.writeAttribute("failures", failedTests);
        writer.writeAttribute("tests", totalTests);
        writer.writeAttribute("time", Double.toString(projectDuration / 1000f));
        writer.writeAttribute("timestamp", timestamp);

        for (LogNodeUtils.Pair<String, Node> pair : items) {
            processItem(logArchive, pair.getValue(), projectName, writer, pair.getKey());
        }

        writer.writeEndElement(); //testcase
    }

}