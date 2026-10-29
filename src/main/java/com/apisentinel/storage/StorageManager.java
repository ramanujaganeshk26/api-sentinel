package com.apisentinel.storage;

import com.apisentinel.model.Incident;
import com.apisentinel.model.ServiceEndpoint;
import com.apisentinel.util.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Manages atomic persistence of endpoints and incidents to disk.
 */
public class StorageManager {
    private final Path storageFile;

    public StorageManager(String filePath) {
        this.storageFile = Path.of(filePath);
        try {
            if (storageFile.getParent() != null) {
                Files.createDirectories(storageFile.getParent());
            }
        } catch (IOException e) {
            System.err.println("Could not create storage directory: " + e.getMessage());
        }
    }

    public synchronized void save(List<ServiceEndpoint> endpoints, List<Incident> incidents) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();

            List<Map<String, Object>> epList = new ArrayList<>();
            for (ServiceEndpoint ep : endpoints) {
                epList.add(ep.toMap());
            }
            root.put("endpoints", epList);

            List<Map<String, Object>> incList = new ArrayList<>();
            for (Incident inc : incidents) {
                incList.add(inc.toMap());
            }
            root.put("incidents", incList);

            String json = JsonUtil.toJson(root);

            Path tempFile = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);
            Files.move(tempFile, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            System.err.println("Error saving storage: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized StorageData load() {
        if (!Files.exists(storageFile)) {
            return new StorageData(new ArrayList<>(), new ArrayList<>());
        }
        try {
            String json = Files.readString(storageFile, StandardCharsets.UTF_8);
            Map<String, Object> map = JsonUtil.parseObject(json);

            List<ServiceEndpoint> endpoints = new ArrayList<>();
            Object epsObj = map.get("endpoints");
            if (epsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        endpoints.add(ServiceEndpoint.fromMap((Map<String, Object>) m));
                    }
                }
            }

            List<Incident> incidents = new ArrayList<>();
            Object incObj = map.get("incidents");
            if (incObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        incidents.add(Incident.fromMap((Map<String, Object>) m));
                    }
                }
            }

            return new StorageData(endpoints, incidents);
        } catch (Exception e) {
            System.err.println("Failed to parse storage, starting fresh: " + e.getMessage());
            return new StorageData(new ArrayList<>(), new ArrayList<>());
        }
    }

    public record StorageData(List<ServiceEndpoint> endpoints, List<Incident> incidents) {}
}
