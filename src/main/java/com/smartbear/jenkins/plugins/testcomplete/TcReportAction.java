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

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import java.io.Serializable;

/**
 * @author Igor Filin
 */

@ExportedBean
public class TcReportAction implements Action, Serializable {

    private final AbstractBuild build;
    private final String id;

    private final String testName;
    private final String agent;

    private String tcLogXFileName = "";
    private String htmlXFileName = "";

    private int exitCode = 0;
    private boolean result = true;
    private String error = "";

    private TcLogInfo logInfo = null;

    private TcSummaryAction parent = null;

    public TcReportAction(AbstractBuild build, String id, String testName, String agent) {
        this.id = id;
        this.testName = testName;
        this.agent = agent;
        this.build = build;
    }

    public Api getApi() {
        return new Api(this);
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return testName;
    }

    public String getUrlName() {
        return null;
    }

    public AbstractBuild getBuild() {
        return build;
    }

    public String getId() {
        return id;
    }

    @Exported(name="testName")
    public String getTestName() {
        return testName;
    }

    @Exported(name="agent")
    public String getAgent() {
        return agent;
    }

    @Exported(name="url")
    public String getUrl() {
        return Jenkins.getInstance().getRootUrl() + build.getUrl() + Constants.PLUGIN_NAME + "/reports/" + id;
    }

    public String getTcLogXFileName() {
        return tcLogXFileName;
    }

    public void setTcLogXFileName(String tcLogXFileName) {
        this.tcLogXFileName = tcLogXFileName;
    }

    public String getHtmlXFileName() {
        return htmlXFileName;
    }

    public void setHtmlXFileName(String htmlXFileName) {
        this.htmlXFileName = htmlXFileName;
    }

    @Exported(name="exitCode")
    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    @Exported(name="success")
    public boolean getResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    @Exported(name="error")
    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Exported(name="details", inline=true)
    public TcLogInfo getLogInfo() {
        return logInfo;
    }

    public void setLogInfo(TcLogInfo logInfo) {
        this.logInfo = logInfo;
    }

    public TcSummaryAction getParent() {
        return parent;
    }

    public void setParent(TcSummaryAction parent) {
        this.parent = parent;
    }

    public boolean hasInfo() {
        return (htmlXFileName != null && !htmlXFileName.isEmpty()) ||
                (error != null && !error.isEmpty());
    }

    public String getNoInfoMessage(String url) {
        return String.format(Messages.TcTestBuilder_NoInfo(), url);
    }

}