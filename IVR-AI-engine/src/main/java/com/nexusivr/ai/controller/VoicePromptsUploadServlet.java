package com.nexusivr.ai.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.nexusivr.ai.dao.VoicePromptDao;

@WebServlet(urlPatterns = {"/api/v1/voice-prompts/upload"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 2, // 2MB
        maxFileSize = 1024 * 1024 * 50,      // 50MB
        maxRequestSize = 1024 * 1024 * 100   // 100MB
)
public class VoicePromptsUploadServlet extends BaseAiServlet {

    // Base directories for sounds
    private static final String[] BASE_SOUND_DIRS = {
            "/var/lib/asterisk/sounds",
            "/tmp/nexusivr/sounds",
            "/home/seif/NetBeansProjects/IVR/assets/custom voice prompts"
    };

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Part filePart = req.getPart("file");
            if (filePart == null) {
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing file part");
                return;
            }

            String fileName = getSubmittedFileName(filePart);
            if (fileName == null || fileName.isBlank()) {
                fileName = "uploaded_" + UUID.randomUUID().toString() + ".wav";
            } else {
                fileName = new File(fileName).getName();
            }

            String language = req.getParameter("language");
            if (language == null || language.isBlank()) {
                language = "English (US)";
            }
            
            String username = req.getParameter("username");
            if (username == null || username.isBlank()) {
                username = "Tenant Admin";
            }

            // Determine language code
            String langCode = "en";
            if (language.toLowerCase().contains("arabic") || language.toLowerCase().contains("ar")) {
                langCode = "ar";
            } else if (language.toLowerCase().contains("french") || language.toLowerCase().contains("fr")) {
                langCode = "fr";
            } else if (language.toLowerCase().contains("spanish") || language.toLowerCase().contains("es")) {
                langCode = "es";
            }

            // Find a writable directory
            Path targetPath = null;
            for (String baseDirStr : BASE_SOUND_DIRS) {
                File baseDir = new File(baseDirStr);
                if (baseDir.exists() && baseDir.canWrite()) {
                    File langDir = new File(baseDir, langCode);
                    if (!langDir.exists()) {
                        langDir.mkdirs();
                    }
                    if (langDir.exists() && langDir.canWrite()) {
                        targetPath = Paths.get(langDir.getAbsolutePath(), fileName);
                        break;
                    }
                }
            }

            if (targetPath == null) {
                throw new IOException("Could not find a writable directory for saving the audio file.");
            }

            // Save the file
            try (InputStream input = filePart.getInputStream()) {
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            long fileSize = Files.size(targetPath);
            String sizeStr = formatSize(fileSize);

            String durationStr = "0:00";
            try (javax.sound.sampled.AudioInputStream audioInputStream = javax.sound.sampled.AudioSystem.getAudioInputStream(targetPath.toFile())) {
                javax.sound.sampled.AudioFormat format = audioInputStream.getFormat();
                long frames = audioInputStream.getFrameLength();
                double durationInSeconds = (frames+0.0) / format.getFrameRate();
                int totalSeconds = (int) Math.ceil(durationInSeconds);
                if (totalSeconds == 0 && durationInSeconds > 0) totalSeconds = 1;
                int minutes = totalSeconds / 60;
                int seconds = totalSeconds % 60;
                durationStr = String.format("%d:%02d", minutes, seconds);
            } catch (Exception ex) {
                logger.warn("Could not determine duration for audio file {}: {}", fileName, ex.getMessage());
            }

            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("success", true);
            responseData.put("fileName", fileName);
            responseData.put("filePath", targetPath.toString());
            responseData.put("size", sizeStr);
            responseData.put("duration", durationStr);
            responseData.put("type", "Uploaded");
            responseData.put("createdBy", username);

            new VoicePromptDao().upsert(fileName, language, durationStr, "Uploaded", username, targetPath.toString(), fileSize);

            sendJsonResponse(resp, HttpServletResponse.SC_OK, responseData);

        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            VoicePromptDao dao = new VoicePromptDao();
            String downloadFile = req.getParameter("download");
            if (downloadFile != null && !downloadFile.isBlank()) {
                String fp = dao.getFilePathByName(downloadFile);
                if (fp == null) {
                    fp = new File(BASE_SOUND_DIRS[0], new File(downloadFile).getName()).getAbsolutePath();
                }
                File f = new File(fp);
                if (f.exists() && f.isFile()) {
                    resp.setContentType("audio/wav");
                    resp.setHeader("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
                    Files.copy(f.toPath(), resp.getOutputStream());
                    return;
                } else {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
            
            java.util.List<Map<String, Object>> filesList = new java.util.ArrayList<>();
            java.util.List<Map<String, Object>> dbPrompts = dao.findAll();
            for (Map<String, Object> dbp : dbPrompts) {
                Map<String, Object> fileData = new LinkedHashMap<>();
                long id = System.currentTimeMillis();
                if (dbp.get("updatedAt") != null) {
                    id = ((java.sql.Timestamp) dbp.get("updatedAt")).getTime();
                }
                fileData.put("id", id);
                fileData.put("name", dbp.get("name"));
                fileData.put("duration", dbp.get("duration"));
                fileData.put("type", dbp.get("type"));
                fileData.put("createdBy", dbp.get("createdBy"));
                fileData.put("size", formatSize((Long) dbp.get("sizeBytes")));
                fileData.put("language", dbp.get("language"));
                fileData.put("usedIn", new java.util.ArrayList<>());
                filesList.add(fileData);
            }
            
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("success", true);
            responseData.put("prompts", filesList);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, responseData);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String fileName = req.getParameter("fileName");
            if (fileName == null || fileName.isBlank()) {
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing fileName");
                return;
            }
            
            File targetFile = new File(BASE_SOUND_DIRS[0], new File(fileName).getName());
            boolean deleted = false;
            if (targetFile.exists() && targetFile.isFile()) {
                deleted = targetFile.delete();
            }
            
            new VoicePromptDao().deleteByName(fileName);
            
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("success", deleted);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, responseData);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private String getSubmittedFileName(Part part) {
        for (String cd : part.getHeader("content-disposition").split(";")) {
            if (cd.trim().startsWith("filename")) {
                return cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }
    
    private String formatSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return (sizeBytes / 1024) + " KB";
        return String.format("%.1f MB", (double) sizeBytes / (1024 * 1024));
    }
}
