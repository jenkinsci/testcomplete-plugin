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
import com.smartbear.jenkins.plugins.testcomplete.parser.ILogParser;
import com.smartbear.jenkins.plugins.testcomplete.parser.LogParser2;
import com.smartbear.jenkins.plugins.testcomplete.parser.ParserSettings;
import com.smartbear.jenkins.plugins.testcomplete.parser.LogParser;
import hudson.*;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Igor Filin
 */
@SuppressWarnings("unused")
public class TcTestBuilder extends Builder implements Serializable, SimpleBuildStep {

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
    private static final String FORCE_CONVERSION_ARG = "/ForceConversion";
    private static final String USE_CBT_INTEGRATION_ARG = "/env";
    private static final String VERSION_ARG = "/JenkinsTCPluginVersion:";
    private static final String TAGS_ARG = "/tags:";

    private static final String DEBUG_FLAG_NAME = "TESTCOMPLETE_PLUGIN_DEBUG";
    private static final String KEEP_LOGS_FLAG_NAME = "TESTCOMPLETE_PLUGIN_KEEP_LOGS";

    public static class LaunchConfig {

        private String launchType;
        private String project;
        private String unit;
        private String routine;
        private String test;
        private String tags;

        @DataBoundConstructor
        public LaunchConfig() {
            this.launchType = TcInstallation.LaunchType.lcSuite.toString();
            this.project = "";
            this.unit = "";
            this.routine = "";
            this.test = "";
            this.tags = "";
        }

        @DataBoundSetter
        public void setValue(String value) {
            this.launchType = value;
        }

        public String getValue() {
            return this.launchType;
        }

        @DataBoundSetter
        public void setProject(String project) {
            this.project = project;
        }

        public String getProject() {
            return project;
        }

        @DataBoundSetter
        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getUnit() {
            return unit;
        }

        @DataBoundSetter
        public void setRoutine(String routine) {
            this.routine = routine;
        }

        public String getRoutine() {
            return routine;
        }

        @DataBoundSetter
        public void setTest(String test) {
            this.test = test;
        }

        public String getTest() {
            return test;
        }

        @DataBoundSetter
        public void setTags(String tags) {
            this.tags = tags;
        }

        public String getTags() {
            return tags;
        }

    }

    private transient boolean DEBUG = false;
    private transient boolean KEEP_LOGS = false;

    private String suite;

    private String launchType;
    private String project;
    private String unit;
    private String routine;
    private String test;
    private String tags;

    private String executorType;
    private String executorVersion;

    private String actionOnWarnings;
    private String actionOnErrors;
    private String commandLineArguments;

    private boolean useTimeout;
    private String timeout;

    private boolean useTCService;
    private String userName;
    private Secret userPassword;
    private boolean useActiveSession;

    private String sessionScreenResolution;

    private boolean generateMHT;
    private boolean publishJUnitReports;

    public enum BuildStepAction {
        NONE,
        MAKE_UNSTABLE,
        MAKE_FAILED
    }

    private static class CBTException extends Exception {
        CBTException(String message) {
            super(message);
        }
    }

    private static class TagsException extends Exception {
        TagsException(String message) {
            super(message);
        }
    }

    private static class InvalidConfigurationException extends Exception {
        InvalidConfigurationException(String message) {
            super(message);
        }
    }

    private static Utils.BusyNodeList busyNodes = new Utils.BusyNodeList();

    @DataBoundConstructor
    public TcTestBuilder(String suite) {
        this.suite = suite != null ? suite : "";

        this.launchType = TcInstallation.LaunchType.lcSuite.toString();
        this.project = "";
        this.unit = "";
        this.routine = "";
        this.test = "";
        this.tags = "";

        this.executorType = Constants.ANY_CONSTANT;
        this.executorVersion = Constants.ANY_CONSTANT;
        this.actionOnWarnings = BuildStepAction.NONE.toString();
        this.actionOnErrors = BuildStepAction.MAKE_UNSTABLE.toString();
        this.commandLineArguments = "";

        this.useTimeout = false;
        this.timeout = "";
        this.useTCService = false;
        this.sessionScreenResolution = ScreenResolution.getDefaultResolution().toString();
        this.userName = "";
        this.userPassword = Secret.fromString("");
        this.useActiveSession = true;

        this.generateMHT = false;
        this.publishJUnitReports = true;
    }

    @DataBoundSetter
    public void setLaunchConfig(LaunchConfig launchConfig) {
        this.launchType = launchConfig == null ? TcInstallation.LaunchType.lcSuite.toString() : launchConfig.launchType;
        this.project = launchConfig == null ? "" : launchConfig.project;
        this.unit = launchConfig == null ? "" : launchConfig.unit;
        this.routine = launchConfig == null ? "" : launchConfig.routine;
        this.test = launchConfig == null ? "" : launchConfig.test;
        this.tags = launchConfig == null ? "" : launchConfig.tags;
    }

    public LaunchConfig getLaunchConfig() {
        return null;
    }

    @DataBoundSetter
    public void setSuite(String suite) {
        this.suite = suite != null ? suite : "";
    }

    public String getSuite() {
        return suite;
    }

    @DataBoundSetter
    public void setLaunchType(String launchType) {
        this.launchType = launchType;
    }

    public String getLaunchType() {
        return launchType;
    }

    @DataBoundSetter
    public void setProject(String project) {
        this.project = project;
    }

    public String getProject() {
        return project;
    }

    @DataBoundSetter
    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUnit() {
        return unit;
    }

    @DataBoundSetter
    public void setRoutine(String routine) {
        this.routine = routine;
    }

    public String getRoutine() {
        return routine;
    }

    @DataBoundSetter
    public void setTest(String test) {
        this.test = test;
    }

    public String getTest() {
        return test;
    }

    @DataBoundSetter
    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getTags() {
        return tags;
    }

    @DataBoundSetter
    public void setExecutorType(String executorType) {
        this.executorType = executorType;
    }

    public String getExecutorType() {
        return executorType;
    }

    @DataBoundSetter
    public void setExecutorVersion(String executorVersion) {
        this.executorVersion = executorVersion;
    }

    public String getExecutorVersion() {
        return executorVersion;
    }

    @DataBoundSetter
    public void setActionOnWarnings(String actionOnWarnings) {
        this.actionOnWarnings = actionOnWarnings;
    }

    public String getActionOnWarnings() {
        return actionOnWarnings;
    }

    @DataBoundSetter
    public void setActionOnErrors(String actionOnErrors) {
        this.actionOnErrors = actionOnErrors;
    }

    public String getActionOnErrors() {
        return actionOnErrors;
    }

    @DataBoundSetter
    public void setCommandLineArguments(String commandLineArguments) {
        this.commandLineArguments = commandLineArguments;
    }

    public String getCommandLineArguments() {
        return commandLineArguments;
    }

    @DataBoundSetter
    public void setUseTimeout(boolean useTimeout) {
        this.useTimeout = useTimeout;
    }

    public boolean getUseTimeout() {
        return useTimeout;
    }

    @DataBoundSetter
    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setUseTCService(boolean useTCService) {
        this.useTCService = useTCService;
    }

    public boolean getUseTCService() {
        return useTCService;
    }

    @DataBoundSetter
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    @DataBoundSetter
    public void setUserPassword(String userPassword) {
        this.userPassword = Secret.fromString(userPassword);
    }

    public String getUserPassword() {
        return Secret.toString(this.userPassword);
    }

    public Secret getUserPasswordSecret() {
        return this.userPassword;
    }

    @DataBoundSetter
    public void setUseActiveSession(boolean useActiveSession) {
        this.useActiveSession = useActiveSession;
    }

    public boolean getUseActiveSession() {
        return useActiveSession;
    }

    @DataBoundSetter
    public void setSessionScreenResolution(String sessionScreenResolution) {
        this.sessionScreenResolution = sessionScreenResolution;
    }

    public String getSessionScreenResolution() {
        return sessionScreenResolution;
    }

    @DataBoundSetter
    public void setGenerateMHT(boolean generateMHT) {
        this.generateMHT = generateMHT;
    }

    public boolean getGenerateMHT() {
        return generateMHT;
    }

    @DataBoundSetter
    public void setPublishJUnitReports(boolean publishJUnitReports) {
        this.publishJUnitReports = publishJUnitReports;
    }

    public boolean getPublishJUnitReports() {
        return publishJUnitReports;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private int fixExitCode(int exitCode, Workspace workspace, TaskListener listener) throws IOException, InterruptedException {
        BufferedReader br = null;
        int fixedCode = exitCode;

        try {
            if (workspace.getSlaveExitCodeFilePath().exists()) {
                br = new BufferedReader(new InputStreamReader(workspace.getSlaveExitCodeFilePath().read(), Charset.forName(Constants.DEFAULT_CHARSET_NAME)));

                try {
                    String exiCodeString = br.readLine().trim();
                    if (DEBUG) {
                        TcLog.debug(listener, Messages.TcTestBuilder_Debug_ExitCodeRead(), exiCodeString);
                    }
                    fixedCode = Integer.parseInt(exiCodeString);
                } catch (Exception e) {
                    if (DEBUG) {
                        TcLog.debug(listener, Messages.TcTestBuilder_Debug_ExitCodeReadFailed());
                    }
                }
            } else {
                if (DEBUG) {
                    TcLog.debug(listener, Messages.TcTestBuilder_Debug_ExitCodeFileNotExists());
                }
            }
        } finally {
            if (br != null) {
                br.close();
            }
            workspace.getSlaveExitCodeFilePath().delete();
        }

        return fixedCode;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run,
                        @Nonnull FilePath filePath,
                        @Nonnull Launcher launcher,
                        @Nonnull TaskListener taskListener) throws InterruptedException, IOException {

        Computer currentComputer = filePath.toComputer();
        busyNodes.lock(currentComputer, taskListener);

        try {
            performInternal(run, filePath, launcher, taskListener);
        } catch (InvalidConfigurationException | CBTException | TagsException e) {
            TcLog.error(taskListener, e.getMessage());
            run.setResult(Result.FAILURE);
        } finally {
            busyNodes.release(currentComputer);
        }
    }

    public void performInternal(Run<?, ?> run, FilePath filePath, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException, InvalidConfigurationException, CBTException, TagsException {

        final PrintStream logger = listener.getLogger();
        logger.println();

        EnvVars env = run.getEnvironment(listener);

        DEBUG = false;
        try {
            DEBUG = Boolean.parseBoolean(env.expand("${" + DEBUG_FLAG_NAME + "}"));
        } catch (Exception e) {
            // Do nothing
        }

        KEEP_LOGS = false;
        try {
            KEEP_LOGS = Boolean.parseBoolean(env.expand("${" + KEEP_LOGS_FLAG_NAME + "}"));
        } catch (Exception e) {
            // Do nothing
        }

        if (DEBUG) {
            TcLog.debug(listener, Messages.TcTestBuilder_Debug_Enabled());
        }

        checkParameter(launchType, "launchType", TcInstallation.LaunchType.class, null);
        checkParameter(executorType, "executorType", TcInstallation.ExecutorType.class, Constants.ANY_CONSTANT);
        checkParameter(actionOnWarnings, "actionOnWarnings",   BuildStepAction.class, null);
        checkParameter(actionOnErrors, "actionOnErrors",   BuildStepAction.class, null);

        if (sessionScreenResolution != null && (!sessionScreenResolution.isEmpty()) && ScreenResolution.parseResolution(sessionScreenResolution) == null) {
            throw new InvalidConfigurationException(String.format(Messages.TcTestBuilder_InvalidParameterValue(), sessionScreenResolution, "sessionScreenResolution"));
        }

        String testDisplayName;
        try {
            testDisplayName = makeDisplayName(run, listener);
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
            return;
        }

        TcLog.info(listener, Messages.TcTestBuilder_TestStartedMessage(), testDisplayName);

        if (!Utils.isWindows(launcher.getChannel(), listener)) {
            TcLog.error(listener, Messages.TcTestBuilder_NotWindowsOS());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
            return;
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
            run.setResult(Result.FAILURE);
            return;
        }

        TcLog.info(listener, Messages.TcTestBuilder_ChosenInstallation() + "\n\t" + chosenInstallation);

        // Generating  paths
        final Workspace workspace;
        try {
            workspace = new Workspace(run, filePath);
        } catch (IOException e) {
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
            return;
        }

        boolean isJNLPSlave = !filePath.toComputer().isLaunchSupported() &&
                !Utils.IsLaunchedAsSystemUser(launcher.getChannel(), listener);

        boolean needToUseService = useTCService;

        if (needToUseService && isJNLPSlave) {
            TcLog.warning(listener, Messages.TcTestBuilder_SlaveConnectedWithJNLP());
            needToUseService = false;
        }

        boolean useSessionCreator = chosenInstallation.hasExtendedCommandLine() && (!needToUseService);

        // Making the command line
        ArgumentListBuilder args = makeCommandLineArgs(run, launcher, listener, workspace, chosenInstallation, useSessionCreator);

        if (!isJNLPSlave && !useTCService) {
            if (TcInstallation.LaunchType.lcCBT.name().equals(launchType)) {
                TcLog.error(listener, Messages.TcTestBuilder_SlaveConnectedWithService());
                TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                run.setResult(Result.FAILURE);
                return;
            } else {
                TcLog.warning(listener, Messages.TcTestBuilder_SlaveConnectedWithService());
            }
        }

        if (useTCService && !isJNLPSlave) {
            if (!chosenInstallation.isServiceLaunchingAvailable()) {
                TcLog.info(listener, Messages.TcTestBuilder_UnableToLaunchByServiceUnsupportedVersion());
                TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                run.setResult(Result.FAILURE);
                return;
            } else {
                try {
                    args = prepareServiceCommandLine(listener, chosenInstallation, args, env);
                }
                catch (Exception e) {
                    TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                    run.setResult(Result.FAILURE);
                    return;
                }
            }
        } else if (useSessionCreator) {
            try {
                args = prepareSessionCreatorCommandLine(listener, chosenInstallation, args, env);
            }
            catch (Exception e) {
                TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.toString());
                TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                run.setResult(Result.FAILURE);
                return;
            }
        }

        // TC/TE launching and data processing
        final TcReportAction tcReportAction = new TcReportAction(run, workspace.getLogId(), testDisplayName,
                filePath.toComputer().getNode().getDisplayName());

        int exitCode = -2;
        int fixedExitCode = exitCode;
        boolean result = false;

        Proc process = null;
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

            Launcher.ProcStarter processStarter = launcher.launch().cmds(args).envs(run.getEnvironment(listener));
            processStarter.readStdout();

            process = processStarter.start();
            InputStream processStdout = process.getStdout();

            if (realTimeout == -1) {
                exitCode = process.join();
            } else {
                exitCode = process.joinWithTimeout(realTimeout, TimeUnit.SECONDS, listener);
            }

            if (DEBUG && (processStdout != null)) {
                String processOutput = IOUtils.toString(processStdout);
                if ((processOutput != null) && (!processOutput.isEmpty())) {
                    TcLog.debug(listener, Messages.TcTestBuilder_Debug_ExecutorOutput() + "\n" + processOutput);
                }
            }

            process = null;

            fixedExitCode = fixExitCode(exitCode, workspace, listener);
            String exitCodeDescription = getExitCodeDescription(fixedExitCode);

            TcLog.info(listener, Messages.TcTestBuilder_ExitCodeMessage(),
                    exitCodeDescription == null ? fixedExitCode : fixedExitCode + " (" + exitCodeDescription + ")");

            if (DEBUG) {
                TcLog.debug(listener, Messages.TcTestBuilder_Debug_FixedExitCodeMessage(), exitCode, fixedExitCode);
            }

            processFiles(chosenInstallation, run, launcher.getChannel(), listener, workspace, tcReportAction, startTime);

            if (fixedExitCode == 0) {
                result = true;
            } else if (fixedExitCode == 1) {
                TcLog.warning(listener, Messages.TcTestBuilder_BuildStepHasWarnings());
                if (actionOnWarnings.equals(BuildStepAction.MAKE_UNSTABLE.name())) {
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsUnstable());
                    run.setResult(Result.UNSTABLE);
                    result = true;
                } else if (actionOnWarnings.equals(BuildStepAction.MAKE_FAILED.name())) {
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                    run.setResult(Result.FAILURE);
                } else {
                    result = true;
                }
            } else {
                TcLog.warning(listener, Messages.TcTestBuilder_BuildStepHasErrors());
                if (actionOnErrors.equals(BuildStepAction.MAKE_UNSTABLE.name())) {
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsUnstable());
                    run.setResult(Result.UNSTABLE);
                } else if (actionOnErrors.equals(BuildStepAction.MAKE_FAILED.name())) {
                    TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                    run.setResult(Result.FAILURE);
                }
            }
        } catch (InterruptedException e) {
            // The build has been aborted. Let Jenkins mark it as ABORTED
            throw e;
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(),
                    e.getCause() == null ? e.toString() : e.getCause().toString());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
        } finally {
            if (process != null) {
                try {
                    process.kill();
                } catch (Exception e) {
                    // Do nothing
                }
            }

            tcReportAction.setExitCode(fixedExitCode);
            tcReportAction.setResult(result);
            String tcLogXFileName = tcReportAction.getTcLogXFileName();
            tcReportAction.setStartFailed(tcLogXFileName == null || tcLogXFileName.isEmpty());

            TcSummaryAction currentAction = getOrCreateAction(run);
            currentAction.addReport(tcReportAction);
            if (getPublishJUnitReports()) {
                publishResult(run, listener, workspace, tcReportAction);
            }
        }

        TcLog.info(listener, Messages.TcTestBuilder_TestExecutionFinishedMessage(), testDisplayName);
    }

    private TestResultAction getTestResultAction(Run<?, ?> run) {
        return run.getAction(TestResultAction.class);
    }

    private void publishResult(Run<?, ?> run, TaskListener listener,
                               Workspace workspace, TcReportAction tcReportAction) throws InterruptedException {

        if (tcReportAction.getLogInfo() == null || tcReportAction.getLogInfo().getXML() == null) {
            TcLog.warning(listener, Messages.TcTestBuilder_UnableToPublishTestData());
            return;
        }

        OutputStream os = null;

        String reportFileName = tcReportAction.getId() + ".xml";
        FilePath reportFile = new FilePath(workspace.getMasterLogDirectory(), reportFileName);

        if (DEBUG) {
            TcLog.debug(listener, Messages.TcTestBuilder_Debug_JUNIT_PathOnMaster(), reportFile.getRemote());
        }

        try {
            os = reportFile.write();

            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                String xml = tcReportAction.getLogInfo().getXML();
                byteArrayOutputStream.write(xml.getBytes("UTF-8"));
                byteArrayOutputStream.writeTo(os);
            } finally {
                os.close();
            }

            if (DEBUG) {
                TcLog.debug(listener, Messages.TcTestBuilder_Debug_JUNIT_GeneratedSuccessfully());
            }

            if (KEEP_LOGS) {
                FilePath slaveJUnitFilePath = new FilePath(workspace.getSlaveWorkspacePath(), reportFileName);
                slaveJUnitFilePath.copyFrom(reportFile);

                if (DEBUG) {
                    TcLog.debug(listener, Messages.TcTestBuilder_Debug_JUNIT_CopiedToWorkspace(), slaveJUnitFilePath.getRemote());
                }
            }

            synchronized (run) {
                TestResultAction testResultAction = getTestResultAction(run);

                if (testResultAction == null) {
                    TestResult testResult = new hudson.tasks.junit.TestResult(true);
                    testResult.parse(new File(reportFile.getRemote()));
                    testResultAction = new TestResultAction(run, testResult, listener);

                    if (DEBUG) {
                        TcLog.debug(listener, Messages.TcTestBuilder_Debug_JUNIT_ResultCreated() + ' ' +
                                String.format(Messages.TcTestBuilder_Debug_JUNIT_ResultInfo(),
                                        testResultAction.getFailCount(),
                                        testResultAction.getSkipCount(),
                                        testResultAction.getTotalCount()));
                    }

                    run.addAction(testResultAction);
                } else {
                    TestResult testResult = testResultAction.getResult();
                    testResult.parse(new File(reportFile.getRemote()));
                    testResult.tally();
                    testResultAction.setResult(testResult, listener);

                    if (DEBUG) {
                        TcLog.debug(listener, Messages.TcTestBuilder_Debug_JUNIT_ResultAppended() + ' ' +
                                String.format(Messages.TcTestBuilder_Debug_JUNIT_ResultInfo(),
                                        testResultAction.getFailCount(),
                                        testResultAction.getSkipCount(),
                                        testResultAction.getTotalCount()));
                    }
                }
            }

        } catch (IOException e) {
            if (DEBUG) {
                TcLog.debug(listener, Messages.TcTestBuilder_ExceptionOccurred(), e.getMessage());
            }
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // Do nothing
                }
            }
            try {
                if (reportFile.exists() && !KEEP_LOGS) {
                    if (DEBUG) {
                        TcLog.debug(listener, Messages.TcTestBuilder_Debug_JUNIT_ReportDeleted());
                    }
                    reportFile.delete();
                }
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

    private ArgumentListBuilder prepareServiceCommandLine(TaskListener listener, TcInstallation chosenInstallation, ArgumentListBuilder baseArgs, EnvVars env) throws Exception{
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

        if (DEBUG) {
            TcLog.debug(listener, Messages.TcTestBuilder_Debug_SessionScreenResolution(), sessionScreenResolution);
        }

        String sessionScreenResolutionString = sessionScreenResolution;
        if (sessionScreenResolutionString == null || sessionScreenResolutionString.isEmpty()) {
            sessionScreenResolutionString = ScreenResolution.getDefaultResolutionString();
        }

        ScreenResolution resolution = ScreenResolution.parseResolution(sessionScreenResolutionString);
        if (resolution != null) {
            if (!resolution.equals(ScreenResolution.getDefaultResolution())) {
                if (chosenInstallation.isCustomScreenResolutionSupported()) {
                    if (useActiveSession) {
                        TcLog.warning(listener, Messages.TcTestBuilder_CustomSessionScreenResolutionCanBeIgnored());
                    }

                    resultArgs.add(Constants.SERVICE_ARG_SCREEN_WIDTH).addQuoted(Integer.toString(resolution.getWidth()));
                    resultArgs.add(Constants.SERVICE_ARG_SCREEN_HEIGHT).addQuoted(Integer.toString(resolution.getHeight()));

                } else {
                    TcLog.warning(listener, Messages.TcTestBuilder_CustomSessionScreenResolutionNotSupported());
                }
            }
        } else {
            TcLog.warning(listener, Messages.TcTestBuilder_NotSupportedSessionScreenResolution(), sessionScreenResolution);
        }

        return resultArgs;
    }

    private ArgumentListBuilder prepareSessionCreatorCommandLine(TaskListener listener, TcInstallation chosenInstallation, ArgumentListBuilder baseArgs, EnvVars env) throws Exception {
        ArgumentListBuilder resultArgs = new ArgumentListBuilder();

        resultArgs.addQuoted(chosenInstallation.getSessionCreatorPath());
        resultArgs.add(Constants.SESSION_CREATOR_ARG);

        if (DEBUG) {
            resultArgs.add(Constants.SESSION_CREATOR_ARG_VERBOSE);
        }

        resultArgs.add(Constants.SESSION_CREATOR_ARG_CMD + baseArgs.toStringWithQuote());

        return resultArgs;
    }

    private void processFiles(TcInstallation installation, Run<?, ?> run, VirtualChannel channel, TaskListener listener, Workspace workspace, TcReportAction testResult, long startTime)
            throws IOException, InterruptedException {

        // reading error file

        BufferedReader br = null;
        try {
            if (workspace.getSlaveErrorFilePath().exists()) {
                br = new BufferedReader(new InputStreamReader(workspace.getSlaveErrorFilePath().read(), Charset.forName(Constants.DEFAULT_CHARSET_NAME)));
                String errorString = br.readLine().trim();
                TcLog.warning(listener, Messages.TcTestBuilder_ErrorMessage(), errorString);
                testResult.setError(errorString);
            }
        } finally {
            if (br != null) {
                br.close();
            }

            if (!KEEP_LOGS) {
                workspace.getSlaveErrorFilePath().delete();
            }
        }

        //copying tclogx file

        if (workspace.getSlaveLogXFilePath().exists()) {
            try {
                workspace.getSlaveLogXFilePath().copyTo(workspace.getMasterLogXFilePath());
                String logFileName = workspace.getMasterLogXFilePath().getName();
                testResult.setTcLogXFileName(logFileName);
                EnvVars env = run.getEnvironment(listener);
                String suiteFileName = new FilePath(new File(env.expand(getSuite()))).getBaseName();
                boolean errorOnWarnings = BuildStepAction.MAKE_FAILED.name().equals(actionOnWarnings);

                ILogParser logParser;
                ParserSettings parserSettings = new ParserSettings(new File(workspace.getMasterLogXFilePath().getRemote()),
                        suiteFileName, env.expand(getProject()), getPublishJUnitReports(), errorOnWarnings);

                int timezoneOffset = Utils.getTimezoneOffset(channel, listener);

                if (installation.hasNewLogVersion()) {
                    logParser = new LogParser2(parserSettings, timezoneOffset);
                } else {
                    logParser = new LogParser(parserSettings, timezoneOffset);
                }

                testResult.setLogInfo(logParser.parse(listener));
            } finally {
                if (!KEEP_LOGS) {
                    workspace.getSlaveLogXFilePath().delete();
                }
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
                if (!KEEP_LOGS) {
                    workspace.getSlaveHtmlXFilePath().delete();
                }
            }
        } else {
            TcLog.warning(listener, Messages.TcTestBuilder_UnableToFindLogFile(),
                    workspace.getSlaveHtmlXFilePath().getName());
        }

        //copying mht file

        if (getGenerateMHT()) {
            if (workspace.getSlaveMHTFilePath().exists()) {
                try {
                    workspace.getSlaveMHTFilePath().copyTo(workspace.getMasterMHTFilePath());
                    String logFileName = workspace.getMasterMHTFilePath().getName();
                    testResult.setMhtFileName(logFileName);
                } finally {
                    if (!KEEP_LOGS) {
                        workspace.getSlaveMHTFilePath().delete();
                    }
                }
            } else {
                TcLog.warning(listener, Messages.TcTestBuilder_UnableToFindLogFile(),
                        workspace.getSlaveMHTFilePath().getName());
            }
        }
    }

    private String makeDisplayName(Run<?, ?> run, TaskListener listener) throws IOException, InterruptedException {
        StringBuilder builder = new StringBuilder();
        EnvVars env = run.getEnvironment(listener);

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
        } else if (TcInstallation.LaunchType.lcTags.name().equals(launchType)) {
            builder.append("/");
            builder.append(env.expand(getProject()));
            builder.append("/Tags|");
            builder.append(env.expand(getTags()));
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

    private long getTimeoutValue(TaskListener listener, EnvVars env) {
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
                TcLog.warning(listener, Messages.TcTestBuilder_InvalidTimeoutValue(), env.expand(getTimeout()));
            }
        }
        return -1; // infinite
    }

    private void checkParameter(String value, String parameterName, Class<?> targetEnum, String additionalValue) throws InvalidConfigurationException {
        if (value == null) {
            throw new InvalidConfigurationException(String.format(Messages.TcTestBuilder_InvalidParameterValue(), "", parameterName));
        }

        for (Object targetValue : targetEnum.getEnumConstants()) {
            if (value.equals(targetValue.toString())) {
                return;
            }
        }

        if (value.equals(additionalValue)) {
            return;
        }

        throw new InvalidConfigurationException(String.format(Messages.TcTestBuilder_InvalidParameterValue(), value, parameterName));
    }

    private void addArg(ArgumentListBuilder args, String value, boolean newFormat) {
        if (newFormat) {
            args.addQuoted(value);
        } else {
            args.add(value);
        }
    }

    private ArgumentListBuilder makeCommandLineArgs(Run<?, ?> run,
                                                    Launcher launcher,
                                                    TaskListener listener,
                                                    Workspace workspace,
                                                    TcInstallation installation,
                                                    boolean useNewCommandLineFormat) throws IOException, InterruptedException, CBTException, TagsException {
        ArgumentListBuilder args = new ArgumentListBuilder();

        FilePath execPath = new FilePath(launcher.getChannel(), installation.getExecutorPath());
        addArg(args, execPath.getRemote(), useNewCommandLineFormat);

        EnvVars env = run.getEnvironment(listener);

        addArg(args, new FilePath(workspace.getSlaveWorkspacePath(), env.expand(getSuite())).getRemote(), useNewCommandLineFormat);

        args.add(RUN_ARG);
        args.add(SILENT_MODE_ARG);
        args.add(FORCE_CONVERSION_ARG);
        args.add(NS_ARG);
        args.add(EXIT_ARG);

        addArg(args, EXPORT_LOG_ARG + workspace.getSlaveLogXFilePath().getRemote(), useNewCommandLineFormat);
        addArg(args, EXPORT_LOG_ARG + workspace.getSlaveHtmlXFilePath().getRemote(), useNewCommandLineFormat);
        addArg(args, ERROR_LOG_ARG + workspace.getSlaveErrorFilePath(), useNewCommandLineFormat);

        if (getGenerateMHT()) {
            addArg(args, EXPORT_LOG_ARG + workspace.getSlaveMHTFilePath().getRemote(), useNewCommandLineFormat);
        }

        if (getUseTimeout()) {
            long timeout = getTimeoutValue(listener, env);
            if (timeout != -1) {
                args.add(TIMEOUT_ARG + timeout);
            }
        }

        if (TcInstallation.LaunchType.lcProject.name().equals(launchType)) {
            addArg(args, PROJECT_ARG + env.expand(getProject()), useNewCommandLineFormat);
        } else if (TcInstallation.LaunchType.lcRoutine.name().equals(launchType)) {
            addArg(args, PROJECT_ARG + env.expand(getProject()), useNewCommandLineFormat);
            addArg(args, UNIT_ARG + env.expand(getUnit()), useNewCommandLineFormat);
            addArg(args, ROUTINE_ARG + env.expand(getRoutine()), useNewCommandLineFormat);
        } else if (TcInstallation.LaunchType.lcKdt.name().equals(launchType)) {
            addArg(args, PROJECT_ARG + env.expand(getProject()), useNewCommandLineFormat);
            addArg(args, TEST_ARG + "KeyWordTests|" + env.expand(getTest()), useNewCommandLineFormat);
        } else if (TcInstallation.LaunchType.lcTags.name().equals(launchType)) {
            if (installation.compareVersion("14.20", false) < 0) {
                throw new TagsException(Messages.TcTestBuilder_Tags_NotSupportedTCVersion());
            }

            addArg(args, PROJECT_ARG + env.expand(getProject()), useNewCommandLineFormat);
            addArg(args, TAGS_ARG + env.expand(getTags()), useNewCommandLineFormat);
        } else if (TcInstallation.LaunchType.lcItem.name().equals(launchType)) {
            addArg(args, PROJECT_ARG + env.expand(getProject()), useNewCommandLineFormat);
            addArg(args, TEST_ARG + env.expand(getTest()), useNewCommandLineFormat);
        } else if (TcInstallation.LaunchType.lcCBT.name().equals(launchType)) {
            if (installation.getType().equals(TcInstallation.ExecutorType.TE)) {
                throw new CBTException(Messages.TcTestBuilder_CBT_UnableToUseTE());
            }

            if (installation.compareVersion("12.20", false) < 0) {
                throw new CBTException(Messages.TcTestBuilder_CBT_NotSupportedTCVersion());
            }

            args.add(USE_CBT_INTEGRATION_ARG);
        }

        if (installation.getType() == TcInstallation.ExecutorType.TE) {
            args.add(NO_LOG_ARG);
        }

        // Custom arguments
        if (useNewCommandLineFormat) {

            String[] tokenizedArgs = Util.tokenize(env.expand(getCommandLineArguments()));

            for (String arg : tokenizedArgs) {
                String escapedCommandLineArgument = arg.replace("\"", "\\\\\\\"");
                if (!escapedCommandLineArgument.isEmpty()) {
                    args.add("\"" + escapedCommandLineArgument + "\"");
                }
            }
        } else {
            args.addTokenized(env.expand(getCommandLineArguments()));
        }

        String version = Utils.getPluginVersionOrNull();
        if (version != null) {
            args.add(VERSION_ARG + version);
        } else {
            if (DEBUG) {
                TcLog.debug(listener, Messages.TcTestBuilder_Debug_FailedToDefineSelfVersion());
            }
        }

        if (DEBUG) {
            TcLog.debug(listener, Messages.TcTestBuilder_Debug_AdditionalCommandLineArguments(), commandLineArguments);
        }

        return args;
    }

    private TcSummaryAction getOrCreateAction(Run<?, ?> run) {
        TcSummaryAction currentAction = run.getAction(TcSummaryAction.class);
        if (currentAction == null) {
            currentAction = new TcSummaryAction(run);
            run.addAction(currentAction);
        }
        return currentAction;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension @Symbol("testcompletetest")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            super(TcTestBuilder.class);
            load();
        }

        public String getPluginName() {
            return Constants.PLUGIN_NAME;
        }

        @Override
        public Builder newInstance(StaplerRequest req, @Nonnull JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        public FormValidation doCheckSuite(@QueryParameter String value) {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckProject(@QueryParameter String value) {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckUnit(@QueryParameter String value) {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckRoutine(@QueryParameter String value) {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTest(@QueryParameter String value) {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.TcTestBuilder_Descriptor_ValueNotSpecified());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTimeout(@QueryParameter String value) {
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

        public ListBoxModel doFillSessionScreenResolutionItems() {
            ListBoxModel model = new ListBoxModel();

            for (ScreenResolution resolution : ScreenResolution.getList()) {
                model.add(resolution.toString());
            }

            return model;
        }

        public ListBoxModel doFillExecutorVersionItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.TcTestBuilder_Descriptor_LatestTagText(), Constants.ANY_CONSTANT);
            model.add("14", "14.0");
            model.add("12", "12.0");
            model.add("11", "11.0");
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

        @Nonnull
        public String getDisplayName() {
            return Messages.TcTestBuilder_DisplayName();
        }

    }

}