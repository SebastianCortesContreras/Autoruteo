package com.example.routing;

import com.example.routing.service.RouteUploadJobService;
import com.example.routing.service.RouteUploadRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final RouteUploadJobService routeUploadJobService;

    public RouteController(RouteUploadJobService routeUploadJobService) {
        this.routeUploadJobService = routeUploadJobService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadRoutes(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "depotAddress", required = false) String depotAddress,
            @RequestParam(value = "carryCount", required = false, defaultValue = "10") Integer carryCount,
            @RequestParam(value = "nhrCount", required = false, defaultValue = "10") Integer nhrCount,
            @RequestParam(value = "nprCount", required = false, defaultValue = "5") Integer nprCount) {

        log.info("Solicitud de carga recibida. archivo={}, size={}, depotAddress={}, carryCount={}, nhrCount={}, nprCount={}",
                file.getOriginalFilename(), file.getSize(), depotAddress, carryCount, nhrCount, nprCount);

        if (file.isEmpty()) {
            log.warn("Se recibio un archivo vacio en /api/routes/upload");
            return ResponseEntity.badRequest().body("Por favor selecciona un archivo.");
        }

        try {
            RouteUploadRequest request = new RouteUploadRequest(
                    file.getOriginalFilename(),
                    file.getBytes(),
                    depotAddress,
                    carryCount,
                    nhrCount,
                    nprCount
            );

            Map<String, Object> job = routeUploadJobService.startJob(request);
            log.info("Job {} creado para procesar el archivo {}.", job.get("jobId"), file.getOriginalFilename());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
        } catch (IOException e) {
            log.error("Error leyendo el archivo subido: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body("Error al leer el archivo subido: " + e.getMessage());
        }
    }

    @GetMapping("/upload/{jobId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable String jobId) {
        Map<String, Object> job = routeUploadJobService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontro un proceso de carga con id: " + jobId);
        }
        return ResponseEntity.ok(job);
    }
}
