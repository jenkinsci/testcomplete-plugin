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
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
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
import java.nio.charset.Charset;
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
    private static final String FORCE_CONVERSION_ARG = "/ForceConversion";
    private static final String USE_CBT_INTEGRATION_ARG = "/env";

    private static final String DEBUG_FLAG_NAME = "TESTCOMPLETE_PLUGIN_DEBUG";
    private boolean DEBUG = false;

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
    private final String commandLineArguments;

    private boolean useTimeout;
    private final String timeout;

    private boolean useTCService;
    private String userName;
    private String userPassword;
    private boolean useActiveSession;

    private String sessionScreenResolution;

    private boolean generateMHT;
    private final boolean publishJUnitReports;

    public static enum BuildStepAction {
        NONE,
        MAKE_UNSTABLE,
        MAKE_FAILED
    }

    private static class CBTException extends Exception {
        public CBTException(String message) {
            super(message);
        }
    }

    private static Utils.BusyNodeList busyNodes = new Utils.BusyNodeList();

    @DataBoundConstructor
    public TcTestBuilder(String suite, JSONObject launchConfig, String executorType, String executorVersion,
                         String actionOnWarnings, String actionOnErrors, String commandLineArguments,
                         boolean useTimeout, String timeout, boolean useTCService, String userName,
                         String userPassword, boolean useActiveSession, String sessionScreenResolution,
                         boolean generateMHT, boolean publishJUnitReports) {
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
        this.commandLineArguments = commandLineArguments != null ? commandLineArguments : "";

        this.useTimeout = useTimeout;
        if (this.useTimeout) {
            this.timeout = timeout != null ? timeout : "";
        } else {
            this.timeout = "";
        }

        this.useTCService = useTCService;

        ScreenResolution resolution = ScreenResolution.parseResolution(sessionScreenResolution);
        if (resolution == null) {
            resolution = ScreenResolution.getDefaultResolution();
        }
        this.sessionScreenResolution = resolution.toString();

        if (this.useTCService) {
            this.userName = userName != null ? userName : "";
            this.userPassword = userPassword != null ? userPassword : "";
            this.useActiveSession = useActiveSession;
        } else {
            this.userName = "";
            this.userPassword = "";
            this.useActiveSession = false;
        }

        this.generateMHT = generateMHT;
        this.publishJUnitReports = publishJUnitReports;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
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

    public String getCommandLineArguments() {
        return commandLineArguments;
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

    public String getSessionScreenResolution() {
        if (ScreenResolution.parseResolution(sessionScreenResolution) == null)
            return ScreenResolution.getDefaultResolution().toString();

        return sessionScreenResolution;
    }

    public boolean getGenerateMHT() {
        return generateMHT;
    }

    public boolean getPublishJUnitReports() {
        return publishJUnitReports;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {

        Node currentNode = build.getBuiltOn();
        busyNodes.lock(currentNode, listener);
        try {
            return performInternal(build, launcher, listener);
        } finally {
            busyNodes.release(currentNode);
        }
    }

    private int fixExitCode(int exitCode, Workspace workspace, BuildListener listener) throws IOException, InterruptedException {
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

    public boolean performInternal(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {

        final PrintStream logger = listener.getLogger();
        logger.println();

        EnvVars env = build.getEnvironment(listener);
        DEBUG = false;
        try {
            DEBUG = Boolean.parseBoolean(env.expand("${" + DEBUG_FLAG_NAME + "}"));
        } catch (Exception e) {
            // Do nothing
        }

        if (DEBUG) {
            TcLog.debug(listener, Messages.TcTestBuilder_Debug_Enabled());
        }

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
        ArgumentListBuilder args;
        try {
            args = makeCommandLineArgs(build, launcher, listener, workspace, chosenInstallation);
        } catch (CBTException e) {
            TcLog.error(listener, e.getMessage());
            build.setResult(Result.FAILURE);
            return false;
        }

        boolean isJNLPSlave = !build.getBuiltOn().toComputer().isLaunchSupported() &&
                !Utils.IsLaunchedAsSystemUser(launcher.getChannel(), listener);

        if (isJNLPSlave && useTCService) {
            TcLog.warning(listener, Messages.TcTestBuilder_SlaveConnectedWithJNLP());
        }

        if (!isJNLPSlave && !useTCService) {
            if (TcInstallation.LaunchType.lcCBT.name().equals(launchType)) {
                TcLog.error(listener, Messages.TcTestBuilder_SlaveConnectedWithService());
                TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                build.setResult(Result.FAILURE);
                return false;
            } else {
                TcLog.warning(listener, Messages.TcTestBuilder_SlaveConnectedWithService());
            }
        }

        if (useTCService && !isJNLPSlave) {
            if (!chosenInstallation.isServiceLaunchingAvailable()) {
                TcLog.info(listener, Messages.TcTestBuilder_UnableToLaunchByServiceUnsupportedVersion());
                TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
                build.setResult(Result.FAILURE);
                return false;
            } else {
                try {
                    args = prepareServiceCommandLine(listener, chosenInstallation, args, env);
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

            Launcher.ProcStarter processStarter = launcher.launch().cmds(args).envs(build.getEnvironment(listener));

            process = processStarter.start();

            if (realTimeout == -1) {
                exitCode = process.join();
            } else {
                exitCode = process.joinWithTimeout(realTimeout, TimeUnit.SECONDS, listener);
            }
            process = null;

            fixedExitCode = fixExitCode(exitCode, workspace, listener);
            String exitCodeDescription = getExitCodeDescription(fixedExitCode);

            TcLog.info(listener, Messages.TcTestBuilder_ExitCodeMessage(),
                    exitCodeDescription == null ? fixedExitCode : fixedExitCode + " (" + exitCodeDescription + ")");

            if (DEBUG) {
                TcLog.debug(listener, Messages.TcTestBuilder_Debug_FixedExitCodeMessage(), exitCode, fixedExitCode);
            }

            processFiles(build, listener, workspace, tcReportAction, startTime);

            if (fixedExitCode == 0) {
                result = true;
            } else if (fixedExitCode == 1) {
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
        } catch (InterruptedException e) {
            // The build has been aborted. Let Jenkins mark it as ABORTED
            throw e;
        } catch (Exception e) {
            TcLog.error(listener, Messages.TcTestBuilder_ExceptionOccurred(),
                    e.getCause() == null ? e.toString() : e.getCause().toString());
            TcLog.info(listener, Messages.TcTestBuilder_MarkingBuildAsFailed());
            build.setResult(Result.FAILURE);
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

            TcSummaryAction currentAction = getOrCreateAction(build);
            currentAction.addReport(tcReportAction);
            if (getPublishJUnitReports()) {
                publishResult(build, listener, workspace, tcReportAction);
            }
        }

        TcLog.info(listener, Messages.TcTestBuilder_TestExecutionFinishedMessage(), testDisplayName);
        return true;
    }

    private TestResultAction getTestResultAction(AbstractBuild<?, ?> build) {
        return build.getAction(TestResultAction.class);
    }

    private void publishResult(AbstractBuild build, BuildListener listener,
                               Workspace workspace, TcReportAction tcReportAction) {

        if (tcReportAction.getLogInfo() == null || tcReportAction.getLogInfo().getXML() == null) {
            TcLog.warning(listener, Messages.TcTestBuilder_UnableToPublishTestData());
            return;
        }

        FileOutputStream fos = null;

        File reportFile = new File(workspace.getMasterLogDirectory().getRemote(), tcReportAction.getId() + ".xml");
        try {
            fos = new FileOutputStream(reportFile);

            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                String xml = tcReportAction.getLogInfo().getXML();
                byteArrayOutputStream.write(xml.getBytes("UTF-8"));
                byteArrayOutputStream.writeTo(fos);
            } finally {
                fos.close();
            }

            synchronized (build) {
                TestResultAction testResultAction = getTestResultAction(build);

                boolean testResultActionExists = true;
                if (testResultAction == null) {
                    testResultActionExists = false;
                    TestResult testResult = new hudson.tasks.junit.TestResult(true);
                    testResult.parse(reportFile);
                    testResultAction = new TestResultAction(build, testResult, listener);
                } else {
                    TestResult testResult = testResultAction.getResult();
                    testResult.parse(reportFile);
                    testResult.tally();
                    testResultAction.setResult(testResult, listener);
                }

                if (!testResultActionExists) {
                    build.getActions().add(testResultAction);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // Do nothing
                }
            }
            if (reportFile.exists()) {
                reportFile.delete();
            }
        }
    }

    private ArgumentListBuilder prepareServiceCommandLine(BuildListener listener, TcInstallation chosenInstallation, ArgumentListBuilder baseArgs, EnvVars env) throws Exception{
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

    private void processFiles(AbstractBuild build, BuildListener listener, Workspace workspace, TcReportAction testResult, long startTime)
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
            workspace.getSlaveErrorFilePath().delete();
        }

        //copying tclogx file

        if (workspace.getSlaveLogXFilePath().exists()) {
            try {
                workspace.getSlaveLogXFilePath().copyTo(workspace.getMasterLogXFilePath());
                String logFileName = workspace.getMasterLogXFilePath().getName();
                testResult.setTcLogXFileName(logFileName);
                EnvVars env = build.getEnvironment(listener);
                String suiteFileName = new FilePath(new File(env.expand(getSuite()))).getBaseName();
                boolean errorOnWarnings = BuildStepAction.MAKE_FAILED.name().equals(actionOnWarnings);
                TcLogParser tcLogParser = new TcLogParser(new File(workspace.getMasterLogXFilePath().getRemote()),
                        suiteFileName, env.expand(getProject()), getPublishJUnitReports(), errorOnWarnings);
                testResult.setLogInfo(tcLogParser.parse(listener));
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

        //copying mht file

        if (getGenerateMHT()) {
            if (workspace.getSlaveMHTFilePath().exists()) {
                try {
                    workspace.getSlaveMHTFilePath().copyTo(workspace.getMasterMHTFilePath());
                    String logFileName = workspace.getMasterMHTFilePath().getName();
                    testResult.setMhtFileName(logFileName);
                } finally {
                    workspace.getSlaveMHTFilePath().delete();
                }
            } else {
                TcLog.warning(listener, Messages.TcTestBuilder_UnableToFindLogFile(),
                        workspace.getSlaveMHTFilePath().getName());
            }
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
                                                    TcInstallation installation) throws IOException, InterruptedException, CBTException {

        ArgumentListBuilder args = new ArgumentListBuilder();

        FilePath execPath = new FilePath(launcher.getChannel(), installation.getExecutorPath());
        args.add(execPath.getRemote());

        EnvVars env = build.getEnvironment(listener);

        args.add(new FilePath(workspace.getSlaveWorkspacePath(), env.expand(getSuite())));

        args.add(RUN_ARG);
        args.add(SILENT_MODE_ARG);
        args.add(FORCE_CONVERSION_ARG);
        args.add(NS_ARG);
        args.add(EXIT_ARG);
        args.add(EXPORT_LOG_ARG + workspace.getSlaveLogXFilePath().getRemote());
        args.add(EXPORT_LOG_ARG + workspace.getSlaveHtmlXFilePath().getRemote());
        args.add(ERROR_LOG_ARG + workspace.getSlaveErrorFilePath().getRemote());

        if (getGenerateMHT()) {
            args.add(EXPORT_LOG_ARG + workspace.getSlaveMHTFilePath().getRemote());
        }

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
        args.addTokenized(env.expand(getCommandLineArguments()));

        if (DEBUG) {
            TcLog.debug(listener, Messages.TcTestBuilder_Debug_AdditionalCommandLineArguments(), commandLineArguments);
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

        public String getPluginName() {
            return Constants.PLUGIN_NAME;
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

        public ListBoxModel doFillSessionScreenResolutionItems() {
            ListBoxModel model = new ListBoxModel();

            for (ScreenResolution resolution : ScreenResolution.getList()) {
                model.add(resolution.toString());
            }

            //model.get(ScreenResolution.getList().indexOf(ScreenResolution.getDefaultResolution())).selected = true;
            return model;
        }

        //TODO: Need manual version input
        public ListBoxModel doFillExecutorVersionItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.TcTestBuilder_Descriptor_LatestTagText(), Constants.ANY_CONSTANT);
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

        public String getDisplayName() {
            return Messages.TcTestBuilder_DisplayName();
        }
    }

}