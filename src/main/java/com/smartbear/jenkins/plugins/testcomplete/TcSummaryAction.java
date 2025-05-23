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

import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Run;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * @author Igor Filin
 */
@ExportedBean
public class TcSummaryAction implements Action {

    private final Run<?, ?> build;

    private LinkedHashMap<String, TcReportAction> reports = new LinkedHashMap<>();

    private ArrayList<TcReportAction> reportsOrder = new ArrayList<>();
    private final TcDynamicReportAction dynamic;

    TcSummaryAction(Run<?, ?> build) {
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

    public Run<?, ?> getBuild() {
        return build;
    }

    public void addReport(TcReportAction report) {
        if (!reports.containsValue(report)) {
            report.setParent(this);
            reports.put(report.getId(), report);
            reportsOrder.add(report);
        }
    }

    @Exported(name = "reports", inline = true)
    public ArrayList<TcReportAction> getReportsOrder() {
        return reportsOrder;
    }

    public HashMap<String, TcReportAction> getReports() {
        return reports;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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