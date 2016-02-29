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
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Igor Filin
 */
public class TcLogParser {

    private static final String DESCRIPTION_ENTRY_NAME = "Description.tcLog";

    private static final String TEST_COUNT_PROPERTY_NAME = "test count";
    private static final String START_TIME_PROPERTY_NAME = "start time";
    private static final String STOP_TIME_PROPERTY_NAME = "stop time";
    private static final String ERROR_COUNT_PROPERTY_NAME = "error count";
    private static final String WARNING_COUNT_PROPERTY_NAME = "warning count";

    private final File log;
    private final String suite;
    private final String project;
    private final boolean generateJUnitReports;
    private boolean errorOnWarnings;

    public TcLogParser(File log, String suite, String project, boolean generateJUnitReports, boolean errorOnWarnings) {
        this.log = log;
        this.suite = suite;
        this.project = project;
        this.generateJUnitReports = generateJUnitReports;
        this.errorOnWarnings = errorOnWarnings;
    }

    public static class ParsingException extends IOException {
        public ParsingException(String message) {
            super(message);
        }
    }

    private static class Pair<K, V> {

        private final K key;
        private final V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

    }

    private static class NodeUtils {
        static public String getTextAttribute(Node node, String name) {
            if (node == null) {
                return null;
            }
            NamedNodeMap attributes = node.getAttributes();
            Node attribute;
            if (attributes != null && ((attribute = attributes.getNamedItem(name)) != null)) {
                return attribute.getNodeValue();
            }
            return null;
        }

        static public String getTextProperty(Node node, String propertyName) {
            if (node == null) {
                return null;
            }
            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                if ("Prp".equals(childNode.getNodeName())) {
                    String nameAttribute = getTextAttribute(childNode, "name");
                    if (propertyName.equals(nameAttribute)) {
                        return getTextAttribute(childNode, "value");
                    }
                }
            }
            return null;
        }

        static public List<String> getErrorMessages(Node node) {
            return getMessages(node, "3");
        }

        static public List<String> getWarningMessages(Node node) {
            return getMessages(node, "2");
        }

        static public List<String> getMessages(Node node, String status) {
            List<String> result = new LinkedList<String>();

            // using TreeMap for sorting messages order
            Map<Integer, String> orderedResult = new TreeMap<Integer, String>();

            if (node == null) {
                return result;
            }

            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                if ("Node".equals(childNode.getNodeName())) {
                    String name = getTextAttribute(childNode, "name");
                    if (name != null && name.startsWith("message")) {
                        int index = 0;
                        try {
                            index = Integer.parseInt(name.replace("message", "").trim());
                        } catch (NumberFormatException e) {
                            // Do nothing
                        }

                        String type = getTextProperty(childNode, "type");
                        if (status.equals(type)) {
                            String message = getTextProperty(childNode, "message");
                            if (message != null && !message.isEmpty()) {
                                orderedResult.put(index, message);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<Integer, String> entry : orderedResult.entrySet()) {
                result.add(entry.getValue());
            }

            return result;
        }

        static public Node findRootOwnerNode(NodeList nodes) {
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                String nodeName = getTextAttribute(node, "name");
                if ("item 0".equals(nodeName)) {
                    String ownerMoniker = getTextProperty(node, "ownermoniker");
                    if (ownerMoniker != null && ownerMoniker.isEmpty()) {
                        return node;
                    }
                }
            }
            return null;
        }

        static public Node getRootDocumentNodeFromArchive(ZipFile archive, String name) {
            if (name == null) {
                return null;
            }

            ZipEntry rootLogDataEntry = archive.getEntry(name);
            if (rootLogDataEntry == null) {
                return null;
            }

            InputStream logDataStream = null;
            try {
                logDataStream = archive.getInputStream(rootLogDataEntry);
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();

                Document document = builder.parse(logDataStream);

                Element element = document.getDocumentElement();
                if (!"1".equals(element.getAttribute("version"))) {
                    return null;
                }

                NodeList list = element.getChildNodes();

                for (int i = 0; i < list.getLength(); i++) {
                    Node item = list.item(i);
                    NamedNodeMap attributes = item.getAttributes();
                    if (attributes != null && attributes.getNamedItem("name") != null) {
                        String nodeName = attributes.getNamedItem("name").getNodeValue();
                        if ("root".equals(nodeName)) {
                            return item;
                        }
                    }
                }
            } catch (IOException e) {
                // Do nothing
            } catch (ParserConfigurationException e) {
                // Do nothing
            } catch (SAXException e) {
                // Do nothing
            } finally {
                if (logDataStream != null) {
                    try {
                        logDataStream.close();
                    } catch (IOException e) {
                        // Do nothing
                    }
                }
            }

            return null;
        }

        static public Node findNamedNode(NodeList nodes, String name) {
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                String nodeName = getTextAttribute(node, "name");
                if (name.equals(nodeName)) {
                    return node;
                }
            }
            return null;
        }

        static public Node findNamedNode(Node node, String name) {
            if (node == null) {
                return null;
            }

            return findNamedNode(node.getChildNodes(), name);
        }

        static public List<Node> findChildNodes(Node root, NodeList nodes) {
            List<Node> result = new ArrayList<Node>();

            List<String> childrenKeys = new ArrayList<String>();
            Node childNode = findNamedNode(root.getChildNodes(), "children");
            if (childNode != null) {
                NodeList childNodeProperties = childNode.getChildNodes();
                for (int i = 0; i < childNodeProperties.getLength(); i++) {
                    Node childNodeProperty = childNodeProperties.item(i);
                    String childKey = getTextAttribute(childNodeProperty, "value");
                    if (childKey != null && !childKey.isEmpty()) {
                        childrenKeys.add(childKey);
                    }
                }
            }

            for (String childKey : childrenKeys) {
                for (int j = 0; j < nodes.getLength(); j++) {
                    Node node = nodes.item(j);
                    String nodeMoniker = getTextProperty(node, "moniker");
                    if (childKey.equals(nodeMoniker)) {
                        result.add(node);
                    }
                }
            }

            return result;
        }

        static public boolean isProjectItem(ZipFile archive, Node node) {
            String fileName = getTextProperty(node, "filename");
            Node nodeInfo = getRootDocumentNodeFromArchive(archive, fileName);
            Node logDataRowNode = NodeUtils.findNamedNode(nodeInfo, "status");

            if (logDataRowNode == null) {
                return true;
            }

            return false;
        }

        static public List<Node> scanForSubItems(ZipFile archive, Node root, NodeList nodes) {
            List<String> childrenKeys = new ArrayList<String>();
            Node childNode = findNamedNode(root.getChildNodes(), "children");
            if (childNode != null) {
                NodeList childNodeProperties = childNode.getChildNodes();
                for (int i = 0; i < childNodeProperties.getLength(); i++) {
                    Node childNodeProperty = childNodeProperties.item(i);
                    String childKey = getTextAttribute(childNodeProperty, "value");
                    if (childKey != null && !childKey.isEmpty()) {
                        childrenKeys.add(childKey);
                    }
                }
            }

            List<Node> subItems = new ArrayList<Node>();

            for (String childKey : childrenKeys) {
                for (int j = 0; j < nodes.getLength(); j++) {
                    Node node = nodes.item(j);
                    String nodeMoniker = getTextProperty(node, "moniker");
                    if (childKey.equals(nodeMoniker)) {
                        subItems.add(node);
                    }
                }
            }

            return subItems;
        }

        static public boolean isTestItem(ZipFile archive, Node node, NodeList nodes) {
            List<Node> subItems = scanForSubItems(archive, node, nodes);

            for (Node subItem : subItems) {
                if (!isProjectItem(archive, subItem)) {
                    return true;
                }
            }

            return false;
        }

        static public List<Pair<String, Node>> findChildNodesRecursively(ZipFile archive, Node root, NodeList nodes, String nodeName) throws Exception {
            List<Node> subItems = scanForSubItems(archive, root, nodes);

            if (subItems.isEmpty() && isProjectItem(archive, root)) {
                return null;
            }

            List<Pair<String, Node>> result = new ArrayList<Pair<String, Node>>();

            // search sub items
            for (Node node : subItems) {
                String subNodeName = NodeUtils.getTextProperty(node, "name");
                List<Pair<String, Node>> children = findChildNodesRecursively(archive, node, nodes,
                        "".equals(nodeName) ? subNodeName : nodeName + "/" + subNodeName);

                if (children != null  && !children.isEmpty()) {
                    result.addAll(children);
                }

                if (isProjectItem(archive, node) && isTestItem(archive, node, nodes)){
                    result.add(new Pair<String, Node>("".equals(nodeName) ? subNodeName : nodeName + "/" + subNodeName, node));
                }
            }
            return result;
        }
    }

    public TcLogInfo parse(BuildListener listener) {
        try {
            ZipFile logArchive = new ZipFile(log);

            Node descriptionTopLevelNode = NodeUtils.getRootDocumentNodeFromArchive(logArchive, DESCRIPTION_ENTRY_NAME);
            if (descriptionTopLevelNode == null) {
                throw new ParsingException("Unable to obtain description top-level node.");
            }

            long startTime = Utils.safeConvertDate(NodeUtils.getTextProperty(descriptionTopLevelNode, START_TIME_PROPERTY_NAME));
            long stopTime = Utils.safeConvertDate(NodeUtils.getTextProperty(descriptionTopLevelNode, STOP_TIME_PROPERTY_NAME));

            int testCount = 0;
            try {
                testCount = Integer.parseInt(NodeUtils.getTextProperty(descriptionTopLevelNode, TEST_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            int warningCount = 0;
            try {
                warningCount = Integer.parseInt(NodeUtils.getTextProperty(descriptionTopLevelNode, WARNING_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            int errorCount = 0;
            try {
                errorCount = Integer.parseInt(NodeUtils.getTextProperty(descriptionTopLevelNode, ERROR_COUNT_PROPERTY_NAME));
            } catch (NumberFormatException e) {
                // Do nothing
            }

            TcLogInfo logInfo = new TcLogInfo(startTime, stopTime, testCount, errorCount, warningCount);

            String xml = null;

            if (generateJUnitReports) {
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
        }
        catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
            return null;
        }
    }

    private boolean checkFail(String status) {
        if (errorOnWarnings) {
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

        Node descriptionTopLevelNode = NodeUtils.getRootDocumentNodeFromArchive(logArchive, DESCRIPTION_ENTRY_NAME);
        if (descriptionTopLevelNode == null) {
            throw new ParsingException("Unable to obtain description top-level node.");
        }

        Node topLevelNode = NodeUtils.getRootDocumentNodeFromArchive(logArchive,
                NodeUtils.getTextProperty(descriptionTopLevelNode, "root file name"));

        if (topLevelNode == null) {
            throw new ParsingException("Unable to obtain root top-level node.");
        }

        NodeList rootNodes = topLevelNode.getChildNodes();
        Node rootOwnerNode = NodeUtils.findRootOwnerNode(rootNodes);

        if (rootOwnerNode == null) {
            throw new ParsingException("Unable to obtain root owner node.");
        }

        boolean isSuite = "{00000000-0000-0000-0000-000000000000}".
                equals(NodeUtils.getTextProperty(rootOwnerNode, "projectkey"));

        Node rootOwnerNodeInfo = NodeUtils.getRootDocumentNodeFromArchive(logArchive,
                NodeUtils.getTextProperty(rootOwnerNode, "filename"));

        if (rootOwnerNodeInfo == null) {
            throw new ParsingException("Unable to obtain root owner node info.");
        }

        Node rootOwnerNodeInfoSummary = NodeUtils.findNamedNode(rootOwnerNodeInfo.getChildNodes(), "summary");
        boolean isSuiteOrProject = rootOwnerNodeInfoSummary != null;

        writer.writeStartElement("testsuites");

        if (isSuite) {
            List<Node> projects = NodeUtils.findChildNodes(rootOwnerNode, rootOwnerNode.getParentNode().getChildNodes());
            for (Node projectNode : projects) {
                Node projectNodeInfo = NodeUtils.getRootDocumentNodeFromArchive(logArchive,
                        NodeUtils.getTextProperty(projectNode, "filename"));
                Node projectNodeInfoSummary = NodeUtils.findNamedNode(projectNodeInfo, "summary");
                processProject(logArchive, projectNode, projectNodeInfoSummary, writer);
            }
        } else if (isSuiteOrProject) {
            processProject(logArchive, rootOwnerNode, rootOwnerNodeInfoSummary, writer);
        } else {
            String testCaseName = NodeUtils.getTextProperty(rootOwnerNode, "name");
            String testCaseDuration = Double.toString(logInfo.getTestDuration() / 1000f);

            writer.writeStartElement("testsuite");
            writer.writeAttribute("name", project);
            writer.writeAttribute("time", testCaseDuration);

            writer.writeStartElement("testcase");
            writer.writeAttribute("name", fixTestCaseName(testCaseName));
            writer.writeAttribute("classname", suite + "." + project);
            writer.writeAttribute("time", testCaseDuration);
            if (checkFail(NodeUtils.getTextProperty(rootOwnerNode, "status"))) {
                writer.writeStartElement("failure");

                List<String> messages = NodeUtils.getErrorMessages(rootOwnerNodeInfo);
                if (errorOnWarnings) {
                    messages.addAll(NodeUtils.getWarningMessages(rootOwnerNodeInfo));
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
        Node nodeInfo = NodeUtils.getRootDocumentNodeFromArchive(logArchive,
                NodeUtils.getTextProperty(node, "filename"));

        if (nodeInfo == null) {
            throw new ParsingException("Unable to obtain item node info.");
        }

        Node logDataRowNode = NodeUtils.findNamedNode(NodeUtils.findNamedNode(nodeInfo, "log data"), "row0");
        if (logDataRowNode == null) {
            throw new ParsingException("Unable to obtain log data->row0 node for item with name '" + name + "'.");
        }

        writer.writeStartElement("testcase");
        writer.writeAttribute("name", name);
        writer.writeAttribute("classname", suite + "." + projectName);

        long startTime = Utils.safeConvertDate(NodeUtils.getTextProperty(logDataRowNode, "start time"));
        long endTime = Utils.safeConvertDate(NodeUtils.getTextProperty(logDataRowNode, "end time"));
        long duration = endTime - startTime > 0 ? endTime - startTime : 0;

        writer.writeAttribute("time", Double.toString(duration / 1000f));

        if (checkFail(NodeUtils.getTextProperty(node, "status"))) {

            Node testDetailsNode = NodeUtils.getRootDocumentNodeFromArchive(logArchive,
                    NodeUtils.getTextProperty(logDataRowNode, "details"));
            writer.writeStartElement("failure");

            List<String> messages = NodeUtils.getErrorMessages(testDetailsNode);
            if (errorOnWarnings) {
                messages.addAll(NodeUtils.getWarningMessages(testDetailsNode));
            }

            writer.writeAttribute("message", StringUtils.join(messages, "\n\n"));
            writer.writeEndElement(); //failure
        }
        writer.writeEndElement(); //testcase
    }

    private void processProject(ZipFile logArchive, Node rootOwnerNode, Node rootOwnerNodeInfoSummary, XMLStreamWriter writer)
            throws ParsingException, XMLStreamException {

        String totalTests = NodeUtils.getTextProperty(
                NodeUtils.findNamedNode(rootOwnerNodeInfoSummary.getChildNodes(), "total"), "total (sum)");
        String failedTests = NodeUtils.getTextProperty(
                NodeUtils.findNamedNode(rootOwnerNodeInfoSummary.getChildNodes(), "failed"), "total (sum)");
        long startDate = Utils.safeConvertDate(NodeUtils.getTextProperty(rootOwnerNodeInfoSummary, "start date"));
        long stopDate = Utils.safeConvertDate(NodeUtils.getTextProperty(rootOwnerNodeInfoSummary, "stop date"));
        long projectDuration = stopDate - startDate > 0 ? stopDate - startDate : 0;
        String projectName = NodeUtils.getTextProperty(rootOwnerNodeInfoSummary, "test");
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(startDate);
        String timestamp = DatatypeConverter.printDateTime(cal);

        String rootOwnerNodeFileName = NodeUtils.getTextProperty(rootOwnerNode, "filename");
        if (rootOwnerNodeFileName == null || rootOwnerNodeFileName.isEmpty()) {
            throw new ParsingException("Unable to obtain filename for project node.");
        }

        List<Pair<String, Node>> items = null;

        try {
            items = NodeUtils.findChildNodesRecursively(logArchive, rootOwnerNode, rootOwnerNode.getParentNode().getChildNodes(), "");
        } catch (Exception e) {
            items = new ArrayList<Pair<String, Node>>();
        }

        writer.writeStartElement("testsuite");
        writer.writeAttribute("name", projectName);
        writer.writeAttribute("failures", failedTests);
        writer.writeAttribute("tests", totalTests);
        writer.writeAttribute("time", Double.toString(projectDuration / 1000f));
        writer.writeAttribute("timestamp", timestamp);

        for (Pair<String, Node> pair : items) {
            processItem(logArchive, pair.getValue(), projectName, writer, pair.getKey());
        }

        writer.writeEndElement(); //testcase
    }

}