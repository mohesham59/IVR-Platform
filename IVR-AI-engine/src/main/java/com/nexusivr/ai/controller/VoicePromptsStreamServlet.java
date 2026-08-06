package com.nexusivr.ai.controller;

import com.nexusivr.ai.dao.VoicePromptDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

@WebServlet(urlPatterns = {"/api/v1/voice-prompts/stream"})
public class VoicePromptsStreamServlet extends BaseAiServlet {

    private static final String[] TARGET_DIRS = {
            "/home/seif/NetBeansProjects/IVR/assets/custom voice prompts",
            "/var/lib/asterisk/sounds/en",
            "/var/lib/asterisk/sounds",
            "/tmp/nexusivr/sounds"
    };

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String fileName = req.getParameter("name");
        if (fileName == null || fileName.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing prompt name");
            return;
        }

        VoicePromptDao dao = new VoicePromptDao();
        String filePath = dao.getFilePathByName(fileName);

        File file = null;
        if (filePath != null && !filePath.isBlank()) {
            file = new File(filePath);
        }

        if (file == null || !file.exists() || !file.isFile()) {
            // Fallback search in TARGET_DIRS
            String cleanName = new File(fileName).getName();
            for (String dir : TARGET_DIRS) {
                File testFile = new File(dir, cleanName);
                if (testFile.exists() && testFile.isFile()) {
                    file = testFile;
                    break;
                }
            }
        }

        if (file == null || !file.exists() || !file.isFile()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Audio prompt file not found");
            return;
        }

        String mimeType = getServletContext().getMimeType(file.getName());
        if (mimeType == null) {
            if (file.getName().toLowerCase().endsWith(".wav")) {
                mimeType = "audio/wav";
            } else if (file.getName().toLowerCase().endsWith(".mp3")) {
                mimeType = "audio/mpeg";
            } else {
                mimeType = "audio/octet-stream";
            }
        }

        resp.setContentType(mimeType);
        resp.setHeader("Accept-Ranges", "bytes");

        long fileLength = file.length();
        String rangeHeader = req.getHeader("Range");

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring(6).split("-");
            long start = Long.parseLong(ranges[0]);
            long end = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileLength - 1;

            if (start >= fileLength || end >= fileLength || start > end) {
                resp.setHeader("Content-Range", "bytes */" + fileLength);
                resp.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            long contentLength = end - start + 1;
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            resp.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, fileLength));
            resp.setContentLengthLong(contentLength);

            try (InputStream input = Files.newInputStream(file.toPath());
                 OutputStream output = resp.getOutputStream()) {
                long skipped = input.skip(start);
                if (skipped < start) {
                    throw new IOException("Unable to skip to specified range position");
                }
                byte[] buffer = new byte[8192];
                long bytesToRead = contentLength;
                int read;
                while (bytesToRead > 0 && (read = input.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead))) != -1) {
                    output.write(buffer, 0, read);
                    bytesToRead -= read;
                }
            }
        } else {
            resp.setContentLengthLong(fileLength);
            try (OutputStream output = resp.getOutputStream()) {
                Files.copy(file.toPath(), output);
            }
        }
    }
}
