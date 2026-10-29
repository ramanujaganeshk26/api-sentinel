package com.apisentinel.service;

import com.apisentinel.model.Incident;
import com.apisentinel.model.ServiceEndpoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages incident detection, status transition tracking, and incident history.
 */
public class IncidentService {
    private final List<Incident> incidents = new CopyOnWriteArrayList<>();
    private final Map<String, Incident> activeIncidents = new ConcurrentHashMap<>();

    public IncidentService(List<Incident> initialIncidents) {
        if (initialIncidents != null) {
            this.incidents.addAll(initialIncidents);
            for (Incident inc : initialIncidents) {
                if ("OPEN".equalsIgnoreCase(inc.getStatus())) {
                    activeIncidents.put(inc.getServiceId(), inc);
                }
            }
        }
    }

    public synchronized void recordStatusTransition(ServiceEndpoint endpoint, String previousStatus, String newStatus, String reason) {
        if ("DOWN".equals(newStatus) && !"DOWN".equals(previousStatus)) {
            // Outage started
            if (!activeIncidents.containsKey(endpoint.getId())) {
                Incident incident = new Incident(endpoint.getId(), endpoint.getName(), reason);
                incidents.add(0, incident);
                activeIncidents.put(endpoint.getId(), incident);
                System.out.printf("[INCIDENT CREATED] Service '%s' is DOWN. Reason: %s%n", endpoint.getName(), reason);
            }
        } else if ("OPERATIONAL".equals(newStatus) && "DOWN".equals(previousStatus)) {
            // Recovery
            Incident active = activeIncidents.remove(endpoint.getId());
            if (active != null) {
                active.resolve();
                System.out.printf("[INCIDENT RESOLVED] Service '%s' recovered after %d seconds.%n", endpoint.getName(), active.getDurationSeconds());
            }
        }
    }

    public List<Incident> getAllIncidents() {
        return new ArrayList<>(incidents);
    }

    public List<Incident> getActiveIncidents() {
        return new ArrayList<>(activeIncidents.values());
    }
}
