package com.example.routing.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RouteUploadJobService {

    private static final Logger log = LoggerFactory.getLogger(RouteUploadJobService.class);

    private final RouteUploadProcessingService routeUploadProcessingService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ConcurrentMap<String, JobState> jobs = new ConcurrentHashMap<>();

    public RouteUploadJobService(RouteUploadProcessingService routeUploadProcessingService) {
        this.routeUploadProcessingService = routeUploadProcessingService;
    }

    public Map<String, Object> startJob(RouteUploadRequest request) {
        String jobId = UUID.randomUUID().toString();
        JobState jobState = new JobState(jobId);
        jobState.markQueued("Archivo recibido. La cola de procesamiento ya fue creada.");
        jobs.put(jobId, jobState);

        executorService.submit(() -> processJob(jobState, request));
        return jobState.toResponse();
    }

    public Map<String, Object> getJob(String jobId) {
        JobState jobState = jobs.get(jobId);
        return jobState != null ? jobState.toResponse() : null;
    }

    private void processJob(JobState jobState, RouteUploadRequest request) {
        try {
            jobState.markRunning("STARTING", 1, "Iniciando procesamiento en segundo plano.");
            Map<String, Object> result = routeUploadProcessingService.process(request, progress ->
                    jobState.markRunning(progress.stage(), progress.progress(), progress.message()));
            jobState.markCompleted(result);
            log.info("Job {} completado correctamente.", jobState.jobId);
        } catch (Exception exception) {
            log.error("Job {} fallo durante el procesamiento.", jobState.jobId, exception);
            jobState.markFailed(exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private static final class JobState {
        private final String jobId;
        private volatile String status;
        private volatile String stage;
        private volatile int progress;
        private volatile String message;
        private volatile Map<String, Object> result;
        private volatile Map<String, Object> error;
        private volatile Instant createdAt;
        private volatile Instant updatedAt;
        private volatile Instant completedAt;

        private JobState(String jobId) {
            this.jobId = jobId;
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
            this.status = "QUEUED";
            this.stage = "QUEUED";
            this.progress = 0;
            this.message = "Job creado.";
        }

        private synchronized void markQueued(String message) {
            this.status = "QUEUED";
            this.stage = "QUEUED";
            this.progress = 0;
            this.message = message;
            this.updatedAt = Instant.now();
        }

        private synchronized void markRunning(String stage, int progress, String message) {
            this.status = "RUNNING";
            this.stage = stage;
            this.progress = Math.max(0, Math.min(progress, 99));
            this.message = message;
            this.updatedAt = Instant.now();
        }

        private synchronized void markCompleted(Map<String, Object> result) {
            this.status = "COMPLETED";
            this.stage = "COMPLETED";
            this.progress = 100;
            this.message = "Procesamiento completado.";
            this.result = result;
            this.completedAt = Instant.now();
            this.updatedAt = this.completedAt;
            this.error = null;
        }

        private synchronized void markFailed(Exception exception) {
            this.status = "FAILED";
            this.stage = "FAILED";
            this.progress = Math.max(this.progress, 1);
            this.message = exception.getMessage() != null ? exception.getMessage() : "Error inesperado procesando el archivo.";
            this.completedAt = Instant.now();
            this.updatedAt = this.completedAt;

            Map<String, Object> errorPayload = new LinkedHashMap<>();
            errorPayload.put("message", this.message);
            errorPayload.put("type", exception.getClass().getSimpleName());
            errorPayload.put("technicalDetails", exception.toString());
            this.error = errorPayload;
        }

        private synchronized Map<String, Object> toResponse() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jobId", jobId);
            response.put("status", status);
            response.put("stage", stage);
            response.put("progress", progress);
            response.put("message", message);
            response.put("createdAt", createdAt.toString());
            response.put("updatedAt", updatedAt.toString());
            response.put("completedAt", completedAt != null ? completedAt.toString() : null);
            response.put("result", result);
            response.put("error", error);
            return response;
        }
    }
}
