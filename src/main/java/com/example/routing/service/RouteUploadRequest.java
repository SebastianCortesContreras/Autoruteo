package com.example.routing.service;

public record RouteUploadRequest(
        String originalFilename,
        byte[] fileBytes,
        String depotAddress,
        Integer carryCount,
        Integer nhrCount,
        Integer nprCount) {
}
