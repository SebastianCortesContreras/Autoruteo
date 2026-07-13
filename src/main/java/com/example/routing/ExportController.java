package com.example.routing;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private String getDeliveryWindowLabel(String windowCode) {
        if (windowCode == null) {
            return "";
        }
        return switch (windowCode.trim()) {
            case "WINDOW_1" -> "08:00-11:59";
            case "WINDOW_3" -> "12:30-16:59";
            default -> "08:01-17:00"; // WINDOW_2 o cualquier otro
        };
    }

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody List<Map<String, Object>> routes) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Reporte de Rutas Optimizadas", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            for (Map<String, Object> route : routes) {
                String vehicleId = (String) route.get("vehicleId");
                Object distObj = route.get("totalDistance");
                double distance = 0.0;
                if (distObj instanceof Number) {
                    distance = ((Number) distObj).doubleValue();
                }
                Object loadObj = route.get("totalLoad");
                double totalLoad = 0.0;
                if (loadObj instanceof Number) {
                    totalLoad = ((Number) loadObj).doubleValue();
                }

                PdfPTable table = new PdfPTable(6);
                table.setWidthPercentage(100);
                table.setSpacingBefore(10f);
                table.setSpacingAfter(10f);

                PdfPCell header = new PdfPCell(new Phrase("Vehículo: " + vehicleId + " - Distancia: " + String.format("%.2f", distance / 1000.0) + " km - Peso Total: " + String.format("%.0f", totalLoad) + " kg"));
                header.setColspan(6);
                header.setBackgroundColor(new java.awt.Color(33, 150, 243));
                header.setPadding(8f);
                table.addCell(header);

                // Table Headers
                PdfPCell cellHeader1 = new PdfPCell(new Phrase("ID Pedido"));
                cellHeader1.setBackgroundColor(new java.awt.Color(200, 200, 200));
                table.addCell(cellHeader1);
                
                PdfPCell cellHeader2 = new PdfPCell(new Phrase("Dirección"));
                cellHeader2.setBackgroundColor(new java.awt.Color(200, 200, 200));
                table.addCell(cellHeader2);
                
                PdfPCell cellHeader3 = new PdfPCell(new Phrase("Cliente"));
                cellHeader3.setBackgroundColor(new java.awt.Color(200, 200, 200));
                table.addCell(cellHeader3);
                
                PdfPCell cellHeader4 = new PdfPCell(new Phrase("Peso"));
                cellHeader4.setBackgroundColor(new java.awt.Color(200, 200, 200));
                table.addCell(cellHeader4);
                
                PdfPCell cellHeader5 = new PdfPCell(new Phrase("Franja Entrega"));
                cellHeader5.setBackgroundColor(new java.awt.Color(200, 200, 200));
                table.addCell(cellHeader5);
                
                PdfPCell cellHeader6 = new PdfPCell(new Phrase("Km Acumulados"));
                cellHeader6.setBackgroundColor(new java.awt.Color(200, 200, 200));
                table.addCell(cellHeader6);

                List<Map<String, Object>> orders = (List<Map<String, Object>>) route.get("orders");
                if (orders != null) {
                    for (Map<String, Object> order : orders) {
                        table.addCell(String.valueOf(order.get("id")));
                        table.addCell(String.valueOf(order.get("address")));
                        table.addCell(String.valueOf(order.get("name")));
                        table.addCell(String.valueOf(order.get("demand")));
                        table.addCell(getDeliveryWindowLabel(order.get("deliveryWindow") != null ? String.valueOf(order.get("deliveryWindow")) : ""));
                        table.addCell(String.valueOf(order.get("accumulatedDistanceKm") != null ? order.get("accumulatedDistanceKm") : ""));
                    }
                }
                document.add(table);
            }

            document.close();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rutas.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(out.toByteArray());
        } catch (DocumentException e) {
            throw new IOException("Error creating PDF", e);
        }
    }

    @PostMapping("/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestBody List<Map<String, Object>> routes) throws IOException {
         try (Workbook workbook = new XSSFWorkbook();
              ByteArrayOutputStream out = new ByteArrayOutputStream()) {
             
             Sheet sheet = workbook.createSheet("Rutas");
             
             int rowNum = 0;
             
             // Estilos
             CellStyle headerStyle = workbook.createCellStyle();
             org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
             headerFont.setBold(true);
             headerStyle.setFont(headerFont);
             headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
             headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

             for (Map<String, Object> route : routes) {
                 String vehicleId = (String) route.get("vehicleId");
                 Object distObj = route.get("totalDistance");
                 double distance = 0.0;
                 if (distObj instanceof Number) {
                     distance = ((Number) distObj).doubleValue();
                 }
                 Object loadObj = route.get("totalLoad");
                 double totalLoad = 0.0;
                 if (loadObj instanceof Number) {
                     totalLoad = ((Number) loadObj).doubleValue();
                 }
                 
                 org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
                 org.apache.poi.ss.usermodel.Cell cell0 = headerRow.createCell(0);
                 cell0.setCellValue("Vehículo: " + vehicleId);
                 cell0.setCellStyle(headerStyle);
                 
                 org.apache.poi.ss.usermodel.Cell cell1 = headerRow.createCell(1);
                 cell1.setCellValue("Distancia: " + String.format("%.2f", distance / 1000.0) + " km");
                 cell1.setCellStyle(headerStyle);
                 
                 org.apache.poi.ss.usermodel.Cell cell2 = headerRow.createCell(2);
                 cell2.setCellValue("Peso Total: " + String.format("%.0f", totalLoad) + " kg");
                 cell2.setCellStyle(headerStyle);
                 
                 org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
                 titleRow.createCell(0).setCellValue("ID Pedido");
                 titleRow.createCell(1).setCellValue("Dirección");
                 titleRow.createCell(2).setCellValue("Cliente");
                 titleRow.createCell(3).setCellValue("Peso");
                 titleRow.createCell(4).setCellValue("Franja Entrega");
                 titleRow.createCell(5).setCellValue("Km Acumulados");
                 
                 List<Map<String, Object>> orders = (List<Map<String, Object>>) route.get("orders");
                 if (orders != null) {
                     for (Map<String, Object> order : orders) {
                         org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                         row.createCell(0).setCellValue(String.valueOf(order.get("id")));
                         row.createCell(1).setCellValue(String.valueOf(order.get("address")));
                         row.createCell(2).setCellValue(String.valueOf(order.get("name")));
                         row.createCell(3).setCellValue(String.valueOf(order.get("demand")));
                         row.createCell(4).setCellValue(getDeliveryWindowLabel(order.get("deliveryWindow") != null ? String.valueOf(order.get("deliveryWindow")) : ""));
                         row.createCell(5).setCellValue(String.valueOf(order.get("accumulatedDistanceKm") != null ? order.get("accumulatedDistanceKm") : ""));
                     }
                 }
                 rowNum++; // Empty row
             }
             
             // Auto-size columns
             for(int i = 0; i < 6; i++) {
                 sheet.autoSizeColumn(i);
             }
             
             workbook.write(out);
             return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rutas.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
         }
    }
}
