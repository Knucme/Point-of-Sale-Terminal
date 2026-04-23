package com.sos.controller;

import com.sos.dto.ErrorResponse;
import com.sos.model.*;
import com.sos.repository.*;
import com.sos.security.JwtPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// Sales endpoints: list records, export CSV/PDF
@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private final SalesRecordRepository salesRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final MenuItemRepository menuItemRepo;

    public SalesController(SalesRecordRepository salesRepo,
                           OrderRepository orderRepo,
                           OrderItemRepository orderItemRepo,
                           MenuItemRepository menuItemRepo) {
        this.salesRepo = salesRepo;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.menuItemRepo = menuItemRepo;
    }

    // ── GET /api/sales/summary ──────────────────────────────────────────────
    // Live summary card data for the Manager dashboard.
    @GetMapping("/summary")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getSummary() {
        try {
            Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

            List<SalesRecord> todayRecords = salesRepo.findByCompletedAtGreaterThanEqual(todayStart);
            BigDecimal todayRevenue = todayRecords.stream()
                    .map(SalesRecord::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long completedCount = todayRecords.size();
            long activeOrders = orderRepo.countByStatusNot(OrderStatus.COMPLETED);

            // Top 5 items by quantity sold (all time)
            List<OrderItem> allOrderItems = orderItemRepo.findAll();
            Map<Integer, Integer> itemCounts = new HashMap<>();
            for (OrderItem oi : allOrderItems) {
                itemCounts.merge(oi.getMenuItemId(), oi.getQuantity(), Integer::sum);
            }
            List<Map.Entry<Integer, Integer>> top5 = itemCounts.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            List<Integer> topItemIds = top5.stream().map(Map.Entry::getKey).collect(Collectors.toList());
            Map<Integer, MenuItem> menuMap = menuItemRepo.findByIdIn(topItemIds).stream()
                    .collect(Collectors.toMap(MenuItem::getId, m -> m));

            List<Map<String, Object>> topItems = top5.stream().map(e -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("menuItem", menuMap.get(e.getKey()));
                item.put("totalQuantitySold", e.getValue());
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("todayRevenue", todayRevenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            result.put("todayCompletedOrders", completedCount);
            result.put("activeOrders", activeOrders);
            result.put("topItems", topItems);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate sales summary."));
        }
    }

    // ── GET /api/sales ──────────────────────────────────────────────────────
    // Manager views SalesRecords, with optional date-range filtering.
    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getSales(@RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        try {
            List<SalesRecord> records = fetchRecords(from, to);

            BigDecimal totalRevenue = records.stream()
                    .map(SalesRecord::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Peak hours breakdown
            Map<Integer, int[]> hourBuckets = new LinkedHashMap<>(); // hour -> [orders, revenueInCents]
            Map<Integer, BigDecimal> hourRevenue = new LinkedHashMap<>();
            for (SalesRecord r : records) {
                if (r.getCompletedAt() == null) continue;
                int hour = r.getCompletedAt().atZone(ZoneId.systemDefault()).getHour();
                hourBuckets.computeIfAbsent(hour, h -> new int[]{0})[0]++;
                hourRevenue.merge(hour, r.getTotalAmount(), BigDecimal::add);
            }
            List<Map<String, Object>> peakHours = hourBuckets.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                    .map(e -> {
                        int h = e.getKey();
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("hour", h);
                        m.put("orders", e.getValue()[0]);
                        m.put("revenue", hourRevenue.getOrDefault(h, BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
                        m.put("label", (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? "am" : "pm"));
                        return m;
                    }).collect(Collectors.toList());

            // Menu item breakdown
            Map<String, int[]> itemBreakdown = new LinkedHashMap<>();
            Map<String, BigDecimal> itemRevenue = new LinkedHashMap<>();
            for (SalesRecord r : records) {
                Order order = r.getOrder();
                if (order == null || order.getOrderItems() == null) continue;
                for (OrderItem oi : order.getOrderItems()) {
                    String name = oi.getMenuItem() != null ? oi.getMenuItem().getName() : "Unknown";
                    itemBreakdown.computeIfAbsent(name, n -> new int[]{0})[0] += oi.getQuantity();
                    BigDecimal rev = oi.getMenuItem() != null
                            ? oi.getMenuItem().getPrice().multiply(BigDecimal.valueOf(oi.getQuantity()))
                            : BigDecimal.ZERO;
                    itemRevenue.merge(name, rev, BigDecimal::add);
                }
            }
            List<Map<String, Object>> menuBreakdown = itemBreakdown.entrySet().stream()
                    .sorted((a, b) -> itemRevenue.getOrDefault(b.getKey(), BigDecimal.ZERO)
                            .compareTo(itemRevenue.getOrDefault(a.getKey(), BigDecimal.ZERO)))
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", e.getKey());
                        m.put("quantity", e.getValue()[0]);
                        m.put("revenue", itemRevenue.getOrDefault(e.getKey(), BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
                        return m;
                    }).collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("records", records);
            result.put("totalRevenue", totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            result.put("count", records.size());
            result.put("peakHours", peakHours);
            result.put("menuBreakdown", menuBreakdown);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch sales records."));
        }
    }

    // ── GET /api/sales/export ───────────────────────────────────────────────
    // Manager: export sales as CSV. (PDF export available as future enhancement.)
    @GetMapping("/export")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> export(@RequestParam String format,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to) {
        if (!"csv".equals(format) && !"pdf".equals(format)) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("format must be \"csv\" or \"pdf\"."));
        }

        try {
            List<SalesRecord> records = fetchRecords(from, to);
            BigDecimal totalRevenue = records.stream()
                    .map(SalesRecord::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if ("csv".equals(format)) {
                return buildCsvResponse(records, totalRevenue, from, to);
            }

            if ("pdf".equals(format)) {
                return buildPdfResponse(records, totalRevenue, from, to);
            }

            return ResponseEntity.badRequest().body(new ErrorResponse("Unsupported format."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to export sales data."));
        }
    }

    // ── GET /api/sales/:id ──────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getById(@PathVariable int id) {
        try {
            SalesRecord record = salesRepo.findById(id).orElse(null);
            if (record == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Sales record not found."));
            }
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch sales record."));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private List<SalesRecord> fetchRecords(String from, String to) {
        if (from != null || to != null) {
            Instant fromInst = from != null
                    ? LocalDate.parse(from).atStartOfDay(ZoneId.systemDefault()).toInstant()
                    : Instant.EPOCH;
            Instant toInst = to != null
                    ? LocalDate.parse(to).atTime(23, 59, 59, 999_000_000).atZone(ZoneId.systemDefault()).toInstant()
                    : Instant.now();
            return salesRepo.findByCompletedAtBetween(fromInst, toInst);
        }
        return salesRepo.findAllByOrderByCompletedAtDesc();
    }

    private ResponseEntity<byte[]> buildPdfResponse(List<SalesRecord> records, BigDecimal totalRevenue,
                                                      String from, String to) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 40, 40, 40, 40);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        // Fonts
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font subtitleFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.GRAY);
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
        Font totalFont = new Font(Font.HELVETICA, 10, Font.BOLD);

        // Title
        Paragraph title = new Paragraph("SOS — Sales Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        String rangeLabel = (from != null ? from : "all") + " to " + (to != null ? to : "all");
        Paragraph sub = new Paragraph("Period: " + rangeLabel + "  |  Total: $" +
                totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "  |  Records: " + records.size(), subtitleFont);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(12);
        doc.add(sub);

        // Table
        float[] widths = {8, 8, 35, 15, 12, 14, 8};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);

        String[] headers = {"Order #", "Table", "Items", "Server", "Amount", "Completed", "Payment"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new Color(46, 117, 182));
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        for (SalesRecord r : records) {
            Order o = r.getOrder();
            String orderId = o != null ? String.valueOf(o.getId()) : "";
            String tableNum = o != null ? String.valueOf(o.getTableNumber()) : "";
            String server = o != null && o.getSubmittedBy() != null ? o.getSubmittedBy().getName() : "";
            String items = "";
            if (o != null && o.getOrderItems() != null) {
                items = o.getOrderItems().stream()
                        .map(oi -> oi.getQuantity() + "x " +
                                (oi.getMenuItem() != null ? oi.getMenuItem().getName() : "?"))
                        .collect(Collectors.joining(", "));
            }
            String completed = r.getCompletedAt() != null
                    ? DateTimeFormatter.ofPattern("MMM d, h:mm a")
                            .format(r.getCompletedAt().atZone(ZoneId.systemDefault()))
                    : "";

            addCell(table, orderId, cellFont);
            addCell(table, tableNum, cellFont);
            addCell(table, items, cellFont);
            addCell(table, server, cellFont);
            addCell(table, "$" + r.getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), cellFont);
            addCell(table, completed, cellFont);
            String paymentDisplay = r.getPaymentMethod();
            if (r.getCardLast4() != null && !r.getCardLast4().isBlank()) {
                if ("CREDIT".equals(r.getPaymentMethod()) || "DEBIT".equals(r.getPaymentMethod())) {
                    paymentDisplay += "\nXXXX-XXXX-XXXX-" + r.getCardLast4();
                } else if ("GIFT_CARD".equals(r.getPaymentMethod())) {
                    paymentDisplay += "\n****" + r.getCardLast4();
                }
            }
            addCell(table, paymentDisplay, cellFont);
        }

        // Totals row
        PdfPCell emptyCell = new PdfPCell(new Phrase("", cellFont));
        emptyCell.setBorderWidth(0);
        for (int i = 0; i < 4; i++) table.addCell(emptyCell);
        PdfPCell totalCell = new PdfPCell(new Phrase("$" + totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), totalFont));
        totalCell.setPadding(6);
        totalCell.setBackgroundColor(new Color(230, 240, 250));
        table.addCell(totalCell);
        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", totalFont));
        totalLabel.setPadding(6);
        totalLabel.setColspan(2);
        totalLabel.setBackgroundColor(new Color(230, 240, 250));
        table.addCell(totalLabel);

        doc.add(table);
        doc.close();

        String filename = "sales-report-" + (from != null ? from : "all") + "-to-" + (to != null ? to : "all") + ".pdf";
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_PDF);
        responseHeaders.setContentDispositionFormData("attachment", filename);
        return new ResponseEntity<>(baos.toByteArray(), responseHeaders, HttpStatus.OK);
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private ResponseEntity<byte[]> buildCsvResponse(List<SalesRecord> records, BigDecimal totalRevenue,
                                                     String from, String to) {
        StringBuilder csv = new StringBuilder();
        csv.append("Order #,Table,Items,Server,Amount,Completed,Payment Method,Card Last 4\n");

        for (SalesRecord r : records) {
            Order o = r.getOrder();
            String items = "";
            String server = "";
            String tableNum = "";
            String orderId = "";

            if (o != null) {
                orderId = String.valueOf(o.getId());
                tableNum = String.valueOf(o.getTableNumber());
                server = o.getSubmittedBy() != null ? o.getSubmittedBy().getName() : "";
                if (o.getOrderItems() != null) {
                    items = o.getOrderItems().stream()
                            .map(oi -> oi.getQuantity() + "x " +
                                    (oi.getMenuItem() != null ? oi.getMenuItem().getName().replace(",", "") : "?"))
                            .collect(Collectors.joining("; "));
                }
            }

            String completed = r.getCompletedAt() != null
                    ? DateTimeFormatter.ISO_INSTANT.format(r.getCompletedAt()) : "";

            csv.append(orderId).append(",")
                    .append(tableNum).append(",")
                    .append("\"").append(items).append("\",")
                    .append("\"").append(server).append("\",")
                    .append(r.getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()).append(",")
                    .append(completed).append(",")
                    .append(r.getPaymentMethod()).append(",")
                    .append(r.getCardLast4() != null ? r.getCardLast4() : "").append("\n");
        }

        csv.append("\n,,,,").append(totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()).append(",,TOTAL\n");

        String filename = "sales-report-" + (from != null ? from : "all") + "-to-" + (to != null ? to : "all") + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);

        return new ResponseEntity<>(csv.toString().getBytes(), headers, HttpStatus.OK);
    }
}
