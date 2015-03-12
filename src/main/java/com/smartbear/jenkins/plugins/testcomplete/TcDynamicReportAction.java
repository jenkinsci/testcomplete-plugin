package com.smartbear.jenkins.plugins.testcomplete;

import hudson.model.Action;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Igor Filin
 */
public class TcDynamicReportAction implements Action{

    private final static String DOWNLOAD_FILE_NAME = "Test" + Constants.LOGX_FILE_EXTENSION;

    private final String baseReportsPath;

    TcDynamicReportAction(String baseReportsPath) {

        this.baseReportsPath = baseReportsPath;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {

        if (!req.getMethod().equals("GET")) {
            rsp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        String path = req.getRestOfPath();

        if (path.length() == 0 || path.contains("..") || path.length() < 1) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // remove trailing slash
        path = path.substring(1);
        String[] parts = path.split("/");
        if (parts.length == 0) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (parts.length == 1 && parts[0].endsWith(Constants.LOGX_FILE_EXTENSION)) {
            String requestedFilePath = baseReportsPath + parts[0];
            File file = new File(requestedFilePath);

            if (!file.exists() || !file.isFile() || !file.canRead()) {
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            FileInputStream fis = null;

            try {
                fis = new FileInputStream(file);
                rsp.setHeader("Content-Disposition", "filename=\"" + DOWNLOAD_FILE_NAME + "\"");
                rsp.serveFile(req, fis, file.lastModified(), 0, file.length(), "mime-type:application/force-download");
            } catch (IOException e) {
                rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                if (fis != null)
                    fis.close();
            }
        } else {
            String archiveName = parts[0] + Constants.HTMLX_FILE_EXTENSION;
            File logFile = new File(baseReportsPath, archiveName);
            if (!logFile.exists() || !logFile.isFile()) {
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String entryName;
            if (parts.length == 1) {
                entryName = "index.htm";
            } else {
                entryName = path.substring(parts[0].length() + 1);
            }

            ZipFile archive = null;
            InputStream inputStream = null;
            try {
                archive = new ZipFile(logFile);
                ZipEntry targetEntry = searchEntry(archive, entryName);
                if (targetEntry == null) {
                    rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                inputStream = archive.getInputStream(targetEntry);
                rsp.serveFile(req, inputStream, targetEntry.getTime(), 0, targetEntry.getSize(), targetEntry.getName());
            }
            catch (Exception e) {
                rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (archive != null) {
                    archive.close();
                }
            }
        }
    }

    private ZipEntry searchEntry(ZipFile archive, String entryName) {
        ZipEntry targetEntry = archive.getEntry(entryName);
        if (targetEntry == null) {
            entryName = entryName.replace("/", "\\");
            targetEntry = archive.getEntry(entryName);
        }
        return targetEntry;
    }

}