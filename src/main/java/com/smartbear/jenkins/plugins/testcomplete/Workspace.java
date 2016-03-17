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

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.File;
import java.io.IOException;

/**
 * @author Igor Filin
 */
public class Workspace {

    private final FilePath slaveWorkspacePath;
    private final String logId;
    private final FilePath slaveLogXFilePath;
    private final FilePath slaveHtmlXFilePath;
    private final FilePath masterLogXFilePath;
    private final FilePath masterHtmlXFilePath;
    private final FilePath slaveErrorFilePath;
    private final FilePath slaveExitCodeFilePath;

    private final FilePath masterLogDirectory;
    private final FilePath slaveMHTFilePath;
    private final FilePath masterMHTFilePath;

    public Workspace(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {

        this.slaveWorkspacePath = getSlaveWorkspace(build, launcher, listener);

        this.logId = Long.toString(System.currentTimeMillis() % 10000000);

        String logXName = this.logId + Constants.LOGX_FILE_EXTENSION;
        String htmlXName = this.logId + Constants.HTMLX_FILE_EXTENSION;
        String mhtName = this.logId + Constants.MHT_FILE_EXTENSION;

        this.slaveLogXFilePath = new FilePath(slaveWorkspacePath, logXName);
        this.slaveHtmlXFilePath = new FilePath(slaveWorkspacePath, htmlXName);
        this.slaveMHTFilePath = new FilePath(slaveWorkspacePath, mhtName);

        this.masterLogDirectory = getMasterLogDirectory(build);

        this.masterLogXFilePath = new FilePath(masterLogDirectory, logXName);
        this.masterHtmlXFilePath = new FilePath(masterLogDirectory, htmlXName);
        this.masterMHTFilePath = new FilePath(masterLogDirectory, mhtName);

        this.slaveErrorFilePath = new FilePath(slaveWorkspacePath, this.logId + Constants.ERROR_FILE_EXTENSION);
        this.slaveExitCodeFilePath = new FilePath(slaveWorkspacePath, this.logId + "_exitcode" + Constants.ERROR_FILE_EXTENSION);
    }

    private FilePath getMasterLogDirectory(AbstractBuild build)
            throws IOException, InterruptedException {

        String buildDir = build.getRootDir().getAbsolutePath();
        FilePath masterLogDirectory = new FilePath(new File(buildDir +
                File.separator + Constants.REPORTS_DIRECTORY_NAME));

        masterLogDirectory.mkdirs();
        return masterLogDirectory;
    }

    private FilePath getSlaveWorkspace(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {

        String workspacePath = build.getEnvironment(listener).get("WORKSPACE");
        if (workspacePath == null) {
            throw new IOException(Messages.TcTestBuilder_InternalError());
        }

        FilePath projectWorkspaceOnSlave = new FilePath(launcher.getChannel(), workspacePath);
        projectWorkspaceOnSlave.mkdirs();
        return projectWorkspaceOnSlave.absolutize();
    }

    public FilePath getSlaveWorkspacePath() {
        return slaveWorkspacePath;
    }

    public String getLogId() {
        return logId;
    }

    public FilePath getSlaveLogXFilePath() {
        return slaveLogXFilePath;
    }

    public FilePath getSlaveHtmlXFilePath() {
        return slaveHtmlXFilePath;
    }

    public FilePath getMasterLogXFilePath() {
        return masterLogXFilePath;
    }

    public FilePath getMasterHtmlXFilePath() {
        return masterHtmlXFilePath;
    }

    public FilePath getSlaveErrorFilePath() {
        return slaveErrorFilePath;
    }

    public FilePath getSlaveExitCodeFilePath() {
        return slaveExitCodeFilePath;
    }

    public FilePath getSlaveMHTFilePath() {
        return slaveMHTFilePath;
    }

    public FilePath getMasterMHTFilePath() {
        return masterMHTFilePath;
    }

    public FilePath getMasterLogDirectory() {
        return masterLogDirectory;
    }
}