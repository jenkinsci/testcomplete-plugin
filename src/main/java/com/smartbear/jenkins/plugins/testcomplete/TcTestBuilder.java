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
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Igor Filin
 */
public class TcTestBuilder extends Builder implements Serializable {

    private static final String RUN_ARG = "/run";
    private static final String SILENT_MODE_ARG = "/SilentMode";
    private static final String NS_ARG = "/ns";
    private static final String EXIT_ARG = "/exit";
    private static final String EXPORT_LOG_ARG = "/ExportLog:";
    private static final String ERROR_LOG_ARG = "/ErrorLog:";
    private static final String TIMEOUT_ARG = "/Timeout:";
    private static final String PROJECT_ARG = "/project:";
    private static final String UNIT_ARG = "/unit:";
    private static final String ROUTINE_ARG = "/routine:";
    private static final String TEST_ARG = "/test:";
    private static final String NO_LOG_ARG = "/DoNotShowLog";
    private static final String FORCE_CONVERSION_TAG = "/ForceConversion";

    private final String suite;

    private final String launchType;
    private final String project;
    private final String unit;
    private final String routine;
    private final String test;

    private final String executorType;
    private final String executorVersion;

    private final String actionOnWarnings;
    private final String actionOnErrors;

    private boolean useTimeout;
    private final String timeout;

    private boolean useTCService;
    private String userName;
    private String userPassword;
    private boolean useActiveSession;

    public static enum BuildStepAction {
        NONE,
        MAKE_UNSTABLE,
        MAKE_FAILED
    }

    @DataBoundConstructor
    public TcTestBuilder(String suite, JSONObject launchConfig, String executorType, String executorVersion,
                         String actionOnWarnings, String actionOnErrors, boolean useTimeout, String timeout,
                         boolean useTCService, String userName, String userPassword, boolean useActiveSession) {
        this.suite = suite != null ? suite : "";

        if (launchConfig != null) {
            this.launchType = launchConfig.optString("value", TcInstallation.LaunchType.lcSuite.toString());
            this.project = launchConfig.optString("project", "");
            this.unit = launchConfig.optString("unit", "");
            this.routine = launchConfig.optString("routine", "");
            this.test = launchConfig.optString("test", "");
        } else {
            this.launchType = TcInstallation.LaunchType.lcSuite.toString();
            this.project = "";
            this.unit = "";
            this.routine = "";
            this.test = "";
        }

        this.executorType = executorType != null ? executorType : Constants.ANY_CONSTANT;
        this.executorVersion = executorVersion != null ? executorVersion : Constants.ANY_CONSTANT;
        this.actionOnWarnings = actionOnWarnings != null ? actionOnWarnings : BuildStepAction.NONE.toString();
        this.actionOnErrors = actionOnErrors != null ? actionOnErrors : BuildStepAction.MAKE_UNSTABLE.toString();

        this.useTimeout = useTimeout;
        if (this.useTimeout) {
            this.timeout = timeout != null ? timeout : "";
        } else {
            this.timeout = "";
        }

        this.useTCService = useTCService;
        if (this.useTCService) {
            this.userName = userName != null ? userName : "";
            this.userPassword = userPassword != null ? userPassword : "";
            this.useActiveSession = useActiveSession;
        } else {
            this.userName = "";
            this.userPassword = "";
            this.useActiveSession = false;
        }
    }

    // Do not launch more than one build (TC can not be launched more than once)
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    public String getSuite() {
        return suite;
    }

    public String getLaunchType() {
        return launchType;
    }

    public String getProject() {
        return project;
    }

    public String getUnit() {
        return unit;
    }

    public String getRoutine() {
        return routine;
    }

    public String getTest() {
        return test;
    }

    public String getExecutorType() {
        return executorType;
    }

    public String getExecutorVersion() {
        return executorVersion;
    }

    public String getActionOnWarnings() {
        return actionOnWarnings;
    }

    public String getActionOnErrors() {
        return actionOnErrors;
    }

    public boolean getUseTimeout() {
        return useTimeout;
    }

    public String getTimeout() {
        return timeout;
    }

    public boolean getUseTCService() {
        return useTCService;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public boolean getUseActiveSession() {
        return useActiveSession;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {

        final PrintStream logger = listener.getLogger();
        EnvVars env = build.getEnvironment(listener);
        logger.println();

        String testDisplayName;
        try {
            testDisplayName = makeDisplayName(build, listener);
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            build.setResult(Result.FAILURE);
            return false;
        }

        TcLog.info(listener, Messages.TcTestBuilder_TestStartedMessage(), testDisplayName);

        if (!Utils.isWindows(launcher.getChannel(), listener)) {
            TcLog.error(listener, Messages.TcTestBuilder_NotWindowsOS());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            build.setResult(Result.FAILURE);
            return false;
        }

        // Search required TC/TE installation

        final TcInstallationsScanner scanner = new TcInstallationsScanner(launcher.getChannel(), listener);
        List<TcInstallation> installations = scanner.getInstallations();

        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append(Messages.TcTestBuilder_FoundedInstallations());
        for (TcInstallation i : installations) {
            msgBuilder.append("\n\t").append(i);
        }

        TcLog.info(listener, msgBuilder.toString());

        final TcInstallation chosenInstallation = scanner.findInstallation(installations, getExecutorType(), getExecutorVersion());

        if (chosenInstallation == null) {
            TcLog.error(listener, Messages.TcTestBuilder_InstallationNotFound());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            build.setResult(Result.FAILURE);
            return false;
        }

        TcLog.info(listener, Messages.TcTestBuilder_ChosenInstallation() + "\n\t" + chosenInstallation);

        // Generating  paths
        final Workspace workspace;
        try {
            workspace = new Workspace(build, launcher, listener);
        } catch (IOException e) {
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            build.setResult(Result.FAILURE);
            return false;
        }

        // Making the command line
        ArgumentListBuilder args = makeCommandLineArgs(build, launcher, listener, workspace, chosenInstallation);

        boolean isJNLPSlave = !build.getBuiltOn().toComputer().isLaunchSupported();

        if (isJNLPSlave && useTCService) {
            TcLog.warning(listener, Messages.TcTestBuilder_SlaveConnectedWithJNLP());
        }

        if (!isJNLPSlave && !useTCService) {
            TcLog.warning(listener, Messages.TcTestBuilder_SlaveConnectedWithService());
        }


        if (useTCService && !isJNLPSlave) {
            if (!chosenInstallation.isServiceLaunchingAvailable()) {
                TcLog.info(listener, Messages.TcTestBuilder_UnableToLaunchByServiceUnsupportedVersion());
                TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                build.setResult(Result.FAILURE);
                return false;
            } else {
                try {
                    args = prepareServiceCommandLine(chosenInstallation, args, env);
                }
                catch (Exception e) {
                    TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                    build.setResult(Result.FAILURE);
                    return false;
                }
            }
        }

        // TC/TE launching and data processing
        final TcReportAction tcReportAction = new TcReportAction(build,
                workspace.getLogId(),
                testDisplayName,
                build.getBuiltOn().getDisplayName());

        int exitCode = -2;
        boolean result = false;

        try {
            TcLog.info(listener, Messages.TcTestBuilder_LaunchingTestRunner());


            long realTimeout = getTimeoutValue(null, env);
            if (realTimeout != -1) {
                realTimeout += Constants.WAITING_AFTER_TIMEOUT_INTERVAL;
                if (useTCService) {
                    realTimeout += Constants.SERVICE_INTERVAL_DELAY;
                }
            }

            long startTime = Utils.getSystemTime(launcher.getChannel(), listener);

            Launcher.ProcStarter processStarter = launcher.launch().cmds(args).envs(build.getEnvironment(listener));

            if (realTimeout == -1) {
                exitCode = processStarter.start().join();
            } else {
                exitCode = processStarter.start().joinWithTimeout(realTimeout, TimeUnit.SECONDS, listener);
            }

            String exitCodeDescription = getExitCodeDescription(exitCode);
            TcLog.info(listener, Messages.TcTestBuilder_ExitCodeMessage(),
                    exitCodeDescription == null ? exitCode : exitCode + " (" + exitCodeDescription + ")");

            processFiles(workspace, tcReportAction, listener, startTime);

            if (exitCode == 0) {
                result = true;
            } else if (exitCode == 1) {
                TcLog.warning(listener, Messages.TcTestBuilder_BuildStepHasWarnings());
                if (actionOnWarnings.equals(BuildStepAction.MAKE_UNSTABLE.name())) {
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsUnstable());
                    build.setResult(Result.UNSTABLE);
                    result = true;
                } else if (actionOnWarnings.equals(BuildStepAction.MAKE_FAILED.name())) {
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                    build.setResult(Result.FAILURE);
                } else {
                    result = true;
                }
            } else {
                TcLog.warning(listener, Messages.TcTestBuilder_BuildStepHasErrors());
                if (actionOnErrors.equals(BuildStepAction.MAKE_UNSTABLE.name())) {
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsUnstable());
                    build.setResult(Result.UNSTABLE);
                } else if (actionOnErrors.equals(BuildStepAction.MAKE_FAILED.name())) {
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                    build.setResult(Result.FAILURE);
                }
            }
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(),
                    e.getCause() == null ? e.toString() : e.getCause().toString());
        } finally {
            tcReportAction.setExitCode(exitCode);
            tcReportAction.setResult(result);
            TcSummaryAction currentAction = getOrCreateAction(build);
            currentAction.addReport(tcReportAction);
        }

        TcLog.info(listener, Messages.TcTestBuilder_TestExecutionFinishedMessage(), testDisplayName);
        return true;
    }

    private ArgumentListBuilder prepareServiceCommandLine(TcInstallation chosenInstallation, ArgumentListBuilder baseArgs, EnvVars env) throws Exception{
        ArgumentListBuilder resultArgs = new ArgumentListBuilder();

        resultArgs.addQuoted(chosenInstallation.getServicePath());

        String userName = env.expand(getUserName());
        String domain = "";
        if (userName.contains("\\")) {
            int pos = userName.lastIndexOf("\\");
            domain = userName.substring(0, pos);
            userName = userName.substring(pos + 1);
        }

        resultArgs.add(Constants.SERVICE_ARG);

        resultArgs.add(Constants.SERVICE_ARG_DOMAIN).addQuoted(domain);
        resultArgs.add(Constants.SERVICE_ARG_NAME).addQuoted(userName);

        String encryptedPassword = Utils.encryptPassword(env.expand(getUserPassword()));
        resultArgs.add(Constants.SERVICE_ARG_PASSWORD).addQuoted(encryptedPassword, true);

        long timeout = getTimeoutValue(null, env);

        if (timeout != -1) {
            timeout += Constants.SERVICE_INTERVAL_DELAY;
            timeout *= 1000 /*ms*/;
        }

        resultArgs.add(Constants.SERVICE_ARG_TIMEOUT).addQuoted(Long.toString(timeout));

        resultArgs.add(Constants.SERVICE_ARG_USE_ACTIVE_SESSION).addQuoted(Boolean.toString(getUseActiveSession()));
        resultArgs.add(Constants.SERVICE_ARG_COMMAND_LINE).addQuoted(baseArgs.toStringWithQuote());

        return resultArgs;
    }

    private void processFiles(Workspace workspace, TcReportAction testResult, BuildListener listener, long startTime)
            throws IOException, InterruptedException {

        // reading error file

        BufferedReader br = null;
        try {
            if (workspace.getSlaveErrorFilePath().exists()) {
                br = new BufferedReader(new InputStreamReader(workspace.getSlaveErrorFilePath().read()));
                String errorString = br.readLine().trim();
                TcLog.warning(listener, Messages.TcTestBuilder_ErrorMessage(), errorString);
                testResult.setError(errorString);
            }
        } finally {
            if (br != null) {
                br.close();
            }
            workspace.getSlaveErrorFilePath().delete();
        }

        //copying tclogx file

        if (workspace.getSlaveLogXFilePath().exists()) {
            try {
                workspace.getSlaveLogXFilePath().copyTo(workspace.getMasterLogXFilePath());
                String logFileName = workspace.getMasterLogXFilePath().getName();
                testResult.setTcLogXFileName(logFileName);
                TcLogParser tcLogParser = new TcLogParser(new File(workspace.getMasterLogXFilePath().getRemote()));
                testResult.setLogInfo(tcLogParser.parse());
            } finally {
                workspace.getSlaveLogXFilePath().delete();
            }
        }
        else {
            TcLog.warning(listener, Messages.TcTestBuilder_UnableToFindLogFile(),
                    workspace.getSlaveLogXFilePath().getName());

            testResult.setLogInfo(new TcLogInfo(startTime, 0, 0, 1, 0));
        }

        //copying htmlx file

        if (workspace.getSlaveHtmlXFilePath().exists()) {
            try {
                workspace.getSlaveHtmlXFilePath().copyTo(workspace.getMasterHtmlXFilePath());
                String logFileName = workspace.getMasterHtmlXFilePath().getName();
                testResult.setHtmlXFileName(logFileName);
            } finally {
                workspace.getSlaveHtmlXFilePath().delete();
            }
        } else {
            TcLog.warning(listener, Messages.TcTestBuilder_UnableToFindLogFile(),
                    workspace.getSlaveHtmlXFilePath().getName());
        }
    }

    private String makeDisplayName(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
        StringBuilder builder = new StringBuilder();
        EnvVars env = build.getEnvironment(listener);

        String launchType = getLaunchType();

        // always add suite name to test display name
        String suiteFileName = new FilePath(new File(env.expand(getSuite()))).getBaseName();
        builder.append(suiteFileName);

        if (TcInstallation.LaunchType.lcProject.name().equals(launchType)) {
            builder.append("/");
            builder.append(env.expand(getProject()));
        } else if (TcInstallation.LaunchType.lcRoutine.name().equals(launchType)) {
            builder.append("/");
            builder.append(env.expand(getProject()));
            builder.append("/");
            builder.append(env.expand(getUnit()));
            builder.append("/");
            builder.append(env.expand(getRoutine()));
        } else if (TcInstallation.LaunchType.lcKdt.name().equals(launchType)) {
            builder.append("/");
            builder.append(env.expand(getProject()));
            builder.append("/KeyWordTests|");
            builder.append(env.expand(getTest()));
        } else if (TcInstallation.LaunchType.lcItem.name().equals(launchType)) {
            builder.append("/");
            builder.append(env.expand(getProject()));
            builder.append("/");
            builder.append(env.expand(getTest()));
        }

        return builder.toString();
    }

    private String getExitCodeDescription(int exitCode) {
        switch (exitCode) {
            case -6:
                return Messages.TcServiceProcessNotAvailable();
            case -7:
                return Messages.TcServiceInvalidArgs();
            case -8:
                return Messages.TcServiceInternalError();
            case -9:
                return Messages.TcServiceInternalError();
            case -10:
                return Messages.TcServiceSessionCreationError();
            case -11:
                return Messages.TcServiceSessionLogOffError();
            case -12:
                return Messages.TcServiceProcessCreationError();
            case -13:
                return Messages.TcServiceTimeout();
            case -14:
                return Messages.TcServiceOldVersion();
            default:
                return null;
        }
    }

    private long getTimeoutValue(BuildListener listener, EnvVars env) {
        if (getUseTimeout()) {
            try {
                long timeout = Long.parseLong(env.expand(getTimeout()));
                if (timeout > 0) {
                    return timeout;
                }
            } catch (NumberFormatException e) {
                // Do nothing
            }
            if (listener != null) {
                TcLog.warning(listener, Messages.TcTestBuilder_InvalidTimeoutValue(),
                        env.expand(getTimeout()));
            }
        }
        return -1; // infinite
    }

    private ArgumentListBuilder makeCommandLineArgs(AbstractBuild build,
                                                    Launcher launcher,
                                                    BuildListener listener,
                                                    Workspace workspace,
                                                    TcInstallation installation) throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder();

        FilePath execPath = new FilePath(launcher.getChannel(), installation.getPath());
        args.add(execPath.getRemote());

        EnvVars env = build.getEnvironment(listener);

        args.add(new FilePath(workspace.getSlaveWorkspacePath(), env.expand(getSuite())));

        args.add(RUN_ARG);
        args.add(SILENT_MODE_ARG);
        args.add(FORCE_CONVERSION_TAG);
        args.add(NS_ARG);
        args.add(EXIT_ARG);
        args.add(EXPORT_LOG_ARG + workspace.getSlaveLogXFilePath().getRemote());
        args.add(EXPORT_LOG_ARG + workspace.getSlaveHtmlXFilePath().getRemote());
        args.add(ERROR_LOG_ARG + workspace.getSlaveErrorFilePath().getRemote());

        if (getUseTimeout()) {
            long timeout = getTimeoutValue(listener, env);
            if (timeout != -1) {
                args.add(TIMEOUT_ARG + timeout);
            }
        }

        if (TcInstallation.LaunchType.lcProject.name().equals(launchType)) {
            args.add(PROJECT_ARG + env.expand(getProject()));
        } else if (TcInstallation.LaunchType.lcRoutine.name().equals(launchType)) {
            args.add(PROJECT_ARG + env.expand(getProject()));
            args.add(UNIT_ARG + env.expand(getUnit()));
            args.add(ROUTINE_ARG + env.expand(getRoutine()));
        } else if (TcInstallation.LaunchType.lcKdt.name().equals(launchType)) {
            args.add(PROJECT_ARG + env.expand(getProject()));
            args.add(TEST_ARG + "KeyWordTests|" + env.expand(getTest()));
        } else if (TcInstallation.LaunchType.lcItem.name().equals(launchType)) {
            args.add(PROJECT_ARG + env.expand(getProject()));
            args.add(TEST_ARG + env.expand(getTest()));
        }

        if (installation.getType() == TcInstallation.ExecutorType.TE) {
            args.add(NO_LOG_ARG);
        }

        return args;
    }

    private TcSummaryAction getOrCreateAction(AbstractBuild build) {
        TcSummaryAction currentAction = build.getAction(TcSummaryAction.class);
        if (currentAction == null) {
            currentAction = new TcSummaryAction(build);
            build.addAction(currentAction);
        }
        return currentAction;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            super(TcTestBuilder.class);
            load();
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        public FormValidation doCheckSuite(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckProject(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckUnit(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckRoutine(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTest(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTimeout(@QueryParameter String value)
                throws IOException, ServletException {
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_IsNotNumber());
            }
        }

        public ListBoxModel doFillExecutorTypeItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.TcTestBuilder_Descriptor_AnyTagText(), Constants.ANY_CONSTANT);
            model.add(Constants.TE_NAME, TcInstallation.ExecutorType.TE.toString());
            model.add(Constants.TC_NAME, TcInstallation.ExecutorType.TC.toString());
            return model;
        }

        //TODO: Need manual version input
        public ListBoxModel doFillExecutorVersionItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.TcTestBuilder_Descriptor_LatestTagText(), Constants.ANY_CONSTANT);
            model.add("10", "10.0");
            model.add("9", "9.0");
            return model;
        }

        public ListBoxModel doFillActionOnWarningsItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.BuildStepAction_None(), BuildStepAction.NONE.name());
            model.add(Messages.BuildStepAction_MakeUnstable(), BuildStepAction.MAKE_UNSTABLE.name());
            model.add(Messages.BuildStepAction_MakeFailed(), BuildStepAction.MAKE_FAILED.name());
            return model;
        }

        public ListBoxModel doFillActionOnErrorsItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.BuildStepAction_None(), BuildStepAction.NONE.name());
            model.add(Messages.BuildStepAction_MakeUnstable(), BuildStepAction.MAKE_UNSTABLE.name());
            model.add(Messages.BuildStepAction_MakeFailed(), BuildStepAction.MAKE_FAILED.name());
            return model;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return Messages.TcTestBuilder_DisplayName();
        }
    }

}