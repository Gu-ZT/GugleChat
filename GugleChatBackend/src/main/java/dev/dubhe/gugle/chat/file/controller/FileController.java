package dev.dubhe.gugle.chat.file.controller;

import dev.dubhe.gugle.chat.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String id = UUID.randomUUID().toString();
            String ext = getExtension(file.getOriginalFilename());
            String filename = id + (ext.isEmpty() ? "" : "." + ext);
            Path target = dir.resolve(filename);
            file.transferTo(target);

            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("filename", filename);
            result.put("originalName", file.getOriginalFilename());
            result.put("size", file.getSize());
            result.put("contentType", file.getContentType());
            result.put("url", "/api/files/" + id);
            return ApiResponse.ok(result);
        } catch (IOException e) {
            return ApiResponse.error(500, "Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public void download(@PathVariable String id, jakarta.servlet.http.HttpServletResponse response) {
        try {
            Path dir = Paths.get(uploadDir);
            Optional<Path> file = Files.list(dir)
                    .filter(p -> p.getFileName().toString().startsWith(id))
                    .findFirst();
            if (file.isEmpty()) { response.sendError(404); return; }
            Path p = file.get();
            String contentType = Files.probeContentType(p);
            response.setContentType(contentType != null ? contentType : "application/octet-stream");
            response.setHeader("Content-Disposition", "inline; filename=\"" + p.getFileName() + "\"");
            Files.copy(p, response.getOutputStream());
        } catch (IOException e) {
            try { response.sendError(500); } catch (IOException ignored) {}
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
