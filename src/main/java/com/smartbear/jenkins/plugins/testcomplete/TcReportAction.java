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