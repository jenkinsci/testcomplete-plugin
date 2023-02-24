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

import com.smartbear.jenkins.plugins.testcomplete.Utils;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;

/**
 * @author Igor Filin
 */
class LogNodeUtils {

    public static class Pair<K, V> {

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
        List<String> result = new LinkedList<>();

        // using TreeMap for sorting messages order
        Map<Integer, String> orderedResult = new TreeMap<>();

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

   
    static private final String EXTERNAL_GENERAL_ENTITIES = 
        "http://xml.org/sax/features/external-general-entities";

    static private final String EXTERNAL_PARAMETER_ENTITIES = 
        "http://xml.org/sax/features/external-parameter-entities";

    static private final String LOAD_EXTERNAL_DTD = 
        "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    // This is added to prevent XXE attack on xml parser
    static private void secureDocumentBuilderFactory(DocumentBuilderFactory factory) 
        throws ParserConfigurationException {
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES , false);
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        factory.setFeature(LOAD_EXTERNAL_DTD, false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
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
            secureDocumentBuilderFactory(factory);
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
        } catch (IOException | ParserConfigurationException | SAXException e) {
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

    static public List<String> findChildMessages(Node containerNode, String listName, String prefix) {
        List<String> result = new ArrayList<>();
        Map<Integer, String> map = new HashMap<>();

        if (containerNode == null) {
            return result;
        }

        Node listNode = LogNodeUtils.findNamedNode(containerNode, listName);
        if (listNode == null) {
            return result;
        }

        List<Node> messageNodes = LogNodeUtils.findChildNodes(listNode);

        for (Node messageNode : messageNodes) {
            String name = LogNodeUtils.getTextAttribute(messageNode, "name");

            if (name == null || (!name.startsWith(prefix))) {
                continue;
            }

            name = name.substring(prefix.length());

            int index;

            try {
                index = Integer.parseInt(name);
            } catch (NumberFormatException e) {
                continue;
            }

            String msg = LogNodeUtils.getTextProperty(messageNode, "msg");

            if (msg == null) {
                continue;
            }

            msg = msg.trim();

            if (msg.isEmpty()) {
                continue;
            }

            map.put(index, msg);
        }

        ArrayList<Integer> keys = new ArrayList<Integer>(map.keySet());
        Collections.sort(keys);

        for (Integer key : keys) {
            result.add(map.get(key));
        }

        return result;
    }

    static public List<Node> findChildNodes(Node root, NodeList nodes) {
        List<Node> result = new ArrayList<>();

        List<String> childrenKeys = new ArrayList<>();
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

    static public List<Node> findChildNodes(Node parent) {
        NodeList children = parent.getChildNodes();
        List<Node> result = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            if ("Node".equals(children.item(i).getNodeName())) {
                result.add(0, children.item(i));
            }
        }
        return result;
    }

    static public boolean isProjectItem(ZipFile archive, Node node) {
        String fileName = getTextProperty(node, "filename");
        Node nodeInfo = getRootDocumentNodeFromArchive(archive, fileName);
        Node logDataRowNode = LogNodeUtils.findNamedNode(nodeInfo, "status");
        return logDataRowNode == null;
    }

    static public List<Node> scanForSubItems(Node root, NodeList nodes) {
        List<String> childrenKeys = new ArrayList<>();
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

        List<Node> subItems = new ArrayList<>();

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
        List<Node> subItems = scanForSubItems(node, nodes);

        for (Node subItem : subItems) {
            if (!isProjectItem(archive, subItem)) {
                return true;
            }
        }

        return false;
    }

    static public List<Pair<String, Node>> findChildNodesRecursively(ZipFile archive, Node root, NodeList nodes, String nodeName) {
        List<Node> subItems = scanForSubItems(root, nodes);
        List<Pair<String, Node>> result = new ArrayList<>();

        if (subItems.isEmpty() && isProjectItem(archive, root)) {
            return result;
        }

        // search sub items
        for (Node node : subItems) {
            String subNodeName = LogNodeUtils.getTextProperty(node, "name");
            List<Pair<String, Node>> children = findChildNodesRecursively(archive, node, nodes, "".equals(nodeName) ? subNodeName : nodeName + "/" + subNodeName);

            if (children != null  && !children.isEmpty()) {
                result.addAll(children);
            }

            if (isProjectItem(archive, node) && isTestItem(archive, node, nodes)){
                result.add(new Pair<>("".equals(nodeName) ? subNodeName : nodeName + "/" + subNodeName, node));
            }
        }
        return result;
    }

    static public String startTimeToTimestamp(String startTime) {
        long startDate = Utils.safeConvertDate(startTime);
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(startDate);
        return DatatypeConverter.printDateTime(cal);
    }

}
