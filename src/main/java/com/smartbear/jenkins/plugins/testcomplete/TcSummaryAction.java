package com.smartbear.jenkins.plugins.testcomplete;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.File;
import java.util.*;

/**
 * @author Igor Filin
 */
@ExportedBean
public class TcSummaryAction implements Action {

    private final AbstractBuild<?, ?> build;

    private LinkedHashMap<String, TcReportAction> reports = new LinkedHashMap<String, TcReportAction>();

    private ArrayList<TcReportAction> reportsOrder = new ArrayList<TcReportAction>();
    private final TcDynamicReportAction dynamic;

    TcSummaryAction(AbstractBuild<?, ?> build) {
        this.build = build;
        String buildDir = build.getRootDir().getAbsolutePath();
        String reportsPath = buildDir + File.separator + Constants.REPORTS_DIRECTORY_NAME + File.separator;
        dynamic = new TcDynamicReportAction(reportsPath);
    }

    public String getIconFileName() {
        return "/plugin/" + Constants.PLUGIN_NAME + "/images/tc-48x48.png";
    }

    public String getDisplayName() {
        return Messages.TcSummaryAction_DisplayName();
    }

    public String getUrlName() {
        return Constants.PLUGIN_NAME;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public void addReport(TcReportAction report) {
        if (!reports.containsValue(report)) {
            report.setParent(this);
            reports.put(report.getId(), report);
            reportsOrder.add(report);
        }
    }

    @Exported(name="reports", inline = true)
    public ArrayList<TcReportAction> getReportsOrder() {
        return reportsOrder;
    }

    public HashMap<String, TcReportAction> getReports() {
        return reports;
    }

    public TcReportAction getNextReport(TcReportAction report) {
        if (report == null || !reportsOrder.contains(report)) {
            return null;
        }
        int index = reportsOrder.indexOf(report);
        if (index + 1 >= reportsOrder.size()) {
            return null;
        }
        return reportsOrder.get(index + 1);
    }

    public TcReportAction getPreviousReport(TcReportAction report) {
        if (report == null || !reportsOrder.contains(report)) {
            return null;
        }
        int index = reportsOrder.indexOf(report);
        if (index <= 0) {
            return null;
        }
        return reportsOrder.get(index - 1);
    }

    public TcDynamicReportAction getDynamic() {
        return dynamic;
    }

    public String getPluginName() {
        return Constants.PLUGIN_NAME;
    }

    public Api getApi() {
        return new Api(this);
    }

}