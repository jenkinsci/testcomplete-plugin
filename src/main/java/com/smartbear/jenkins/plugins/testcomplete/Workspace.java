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
import hudson.model.Run;

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

    public Workspace(Run<?, ?> run, FilePath filePath) throws IOException, InterruptedException {

        this.slaveWorkspacePath = getSlaveWorkspace(filePath);

        this.logId = Long.toString(System.currentTimeMillis());

        String logXName = this.logId + Constants.LOGX_FILE_EXTENSION;
        String htmlXName = this.logId + Constants.HTMLX_FILE_EXTENSION;
        String mhtName = this.logId + Constants.MHT_FILE_EXTENSION;

        this.slaveLogXFilePath = new FilePath(slaveWorkspacePath, logXName);
        this.slaveHtmlXFilePath = new FilePath(slaveWorkspacePath, htmlXName);
        this.slaveMHTFilePath = new FilePath(slaveWorkspacePath, mhtName);

        this.masterLogDirectory = getMasterLogDirectory(run);

        this.masterLogXFilePath = new FilePath(masterLogDirectory, logXName);
        this.masterHtmlXFilePath = new FilePath(masterLogDirectory, htmlXName);
        this.masterMHTFilePath = new FilePath(masterLogDirectory, mhtName);

        this.slaveErrorFilePath = new FilePath(slaveWorkspacePath, this.logId + Constants.ERROR_FILE_EXTENSION);
        this.slaveExitCodeFilePath = new FilePath(slaveWorkspacePath, this.logId + "_exitcode" + Constants.ERROR_FILE_EXTENSION);
    }

    private FilePath getMasterLogDirectory(Run<?, ?> run) throws IOException, InterruptedException {

        String buildDir = run.getRootDir().getAbsolutePath();
        FilePath masterLogDirectory = new FilePath(new File(buildDir + File.separator + Constants.REPORTS_DIRECTORY_NAME));

        masterLogDirectory.mkdirs();
        return masterLogDirectory;
    }

    private FilePath getSlaveWorkspace(FilePath filePath) throws IOException, InterruptedException {
        if (filePath == null) {
            throw new IOException(Messages.TcTestBuilder_WorkspaceNotSpecified());
        }

        filePath.mkdirs();
        return filePath.absolutize();
    }

    FilePath getSlaveWorkspacePath() {
        return slaveWorkspacePath;
    }

    String getLogId() {
        return logId;
    }

    FilePath getSlaveLogXFilePath() {
        return slaveLogXFilePath;
    }

    FilePath getSlaveHtmlXFilePath() {
        return slaveHtmlXFilePath;
    }

    FilePath getMasterLogXFilePath() {
        return masterLogXFilePath;
    }

    FilePath getMasterHtmlXFilePath() {
        return masterHtmlXFilePath;
    }

    FilePath getSlaveErrorFilePath() {
        return slaveErrorFilePath;
    }

    FilePath getSlaveExitCodeFilePath() {
        return slaveExitCodeFilePath;
    }

    FilePath getSlaveMHTFilePath() {
        return slaveMHTFilePath;
    }

    FilePath getMasterMHTFilePath() {
        return masterMHTFilePath;
    }

    FilePath getMasterLogDirectory() {
        return masterLogDirectory;
    }
}