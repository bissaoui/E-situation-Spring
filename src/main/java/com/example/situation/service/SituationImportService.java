package com.example.situation.service;

import com.example.situation.model.Situation;
import com.example.situation.repository.SituationRepository;
import com.example.situation.security.ModelSanitizer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SituationImportService {

    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".csv", ".xls", ".xlsx");
    private static final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(Set.of(
        "text/csv",
        "application/csv",
        "text/plain",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/octet-stream"
    ));

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("d/M/yy"),
        DateTimeFormatter.ofPattern("MM/dd/yy"),
        DateTimeFormatter.ofPattern("M/d/yy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("d-M-yy"),
        DateTimeFormatter.ofPattern("d-M-yyyy")
    };

    private final SituationRepository situationRepository;
    private final ModelSanitizer modelSanitizer;

    public SituationImportService(SituationRepository situationRepository, ModelSanitizer modelSanitizer) {
        this.situationRepository = situationRepository;
        this.modelSanitizer = modelSanitizer;
    }

    public int importFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a non-empty file.");
        }
        validateMultipartFile(file);

        return importFile(file.getOriginalFilename(), file.getInputStream());
    }

    public int importFile(Path filePath) throws IOException {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return importFile(filePath.getFileName().toString(), inputStream);
        }
    }

    public int importFile(String filename, InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream is required.");
        }

        String normalizedFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (normalizedFilename.endsWith(".xlsx") || normalizedFilename.endsWith(".xls")) {
            return importExcel(inputStream);
        }
        return importCsv(inputStream);
    }

    private int importExcel(InputStream inputStream) throws IOException {
        Map<String, Situation> existingByKey = loadExistingByKey();
        Map<String, Situation> stagedByKey = new HashMap<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerIndex = findExcelHeaderRow(sheet, formatter);
            if (headerIndex < 0) {
                throw new IllegalArgumentException("Header row not found in Excel file.");
            }

            Map<Integer, String> headers = new HashMap<>();
            Row headerRow = sheet.getRow(headerIndex);
            if (headerRow == null) {
                throw new IllegalArgumentException("Header row is empty.");
            }
            for (Cell cell : headerRow) {
                headers.put(cell.getColumnIndex(), normalize(cellText(cell, formatter)));
            }

            for (int i = headerIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                Situation s = mapExcelRow(row, headers, formatter);
                if (s == null) {
                    continue;
                }
                String key = buildBusinessKey(s);
                Situation target = stagedByKey.get(key);
                if (target == null) {
                    target = existingByKey.get(key);
                }
                if (target != null) {
                    copyValues(target, s);
                    stagedByKey.put(key, target);
                } else {
                    stagedByKey.put(key, s);
                }
            }
        }

        List<Situation> batch = new ArrayList<>(stagedByKey.values());
        if (!batch.isEmpty()) {
            situationRepository.saveAll(batch);
        }
        return batch.size();
    }

    private int importCsv(InputStream inputStream) throws IOException {
        List<String> lines = new ArrayList<>();
        Map<String, Situation> existingByKey = loadExistingByKey();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }
        if (lines.isEmpty()) {
            return 0;
        }

        int headerLine = findCsvHeaderLine(lines);
        if (headerLine < 0) {
            throw new IllegalArgumentException("Header row not found in CSV file.");
        }

        String rawHeader = lines.get(headerLine);
        char delimiter = detectDelimiter(rawHeader);
        String[] headers = rawHeader.split(String.valueOf(delimiter), -1);

        StringBuilder payload = new StringBuilder();
        payload.append(rawHeader).append('\n');
        for (int i = headerLine + 1; i < lines.size(); i++) {
            payload.append(lines.get(i)).append('\n');
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setDelimiter(delimiter)
            .setHeader(headers)
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build();

        Map<String, Situation> stagedByKey = new HashMap<>();
        try (CSVParser parser = format.parse(new InputStreamReader(
            new java.io.ByteArrayInputStream(payload.toString().getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8
        ))) {
            for (CSVRecord row : parser) {
                Situation s = mapCsvRow(row);
                if (s == null) {
                    continue;
                }
                String key = buildBusinessKey(s);
                Situation target = stagedByKey.get(key);
                if (target == null) {
                    target = existingByKey.get(key);
                }
                if (target != null) {
                    copyValues(target, s);
                    stagedByKey.put(key, target);
                } else {
                    stagedByKey.put(key, s);
                }
            }
        }

        List<Situation> batch = new ArrayList<>(stagedByKey.values());
        if (!batch.isEmpty()) {
            situationRepository.saveAll(batch);
        }
        return batch.size();
    }

    private Situation mapCsvRow(CSVRecord row) {
        String beneficiaire = value(row, "BENEFICAIRE", "beneficiaire");
        if (isBlank(beneficiaire)) {
            return null;
        }

        Situation s = new Situation();
        s.setBeneficiaire(beneficiaire);
        s.setBe(value(row, "BE", "be"));
        s.setDateOp(parseDate(value(row, "DATE_OP", "DATE_OV", "DATE", "date_op", "date_ov", "date")));
        s.setNumeroOv(value(row, "N°_OV", "N° OV", "N_OV", "OV", "numero_ov", "ov"));
        s.setCheque(value(row, "CHEQUE", "cheque"));
        s.setMontantOvCheque(parseDecimal(value(
            row, "MONTANT_OV/CHEQUE", "MONTANT OVCHEQUE", "MONTANT_OV_CHEQUE", "montant_ov_cheque"
        )));
        s.setBudget(value(row, "BUDGET", "budget"));
        s.setRubriqueBudg(value(row, "Rubrique_budg", "Rubrique budg", "rubrique_budg"));
        s.setNumeroOp(value(row, "N°_OP", "N° OP", "N_OP", "numero_op"));
        s.setMontantOp(parseDecimal(value(row, "Montant_OP", "Montant", "montant_op", "montant")));
        s.setObjetDepense(value(row, "Objet_dépense", "Objet dépense", "Objet_depense", "objet_depense"));
        s.setAnneeOrigine(value(row, "ANNEE_D'ORIGINE", "ANNEE D'ORIG", "ANNEE_D_ORIGINE", "annee_origine"));
        s.setSituation(value(row, "Situation", "situation"));
        s.setDateVirement(parseDate(value(row, "Date_Virement", "Virement", "date_virement")));
        s.setProjet(value(row, "Projet", "projet"));
        s.setBeUrl(value(row, "BE_URL", "BE URL", "BE_LINK", "BE LINK", "URL", "url"));
        modelSanitizer.sanitizeSituation(s);
        return s;
    }

    private Situation mapExcelRow(Row row, Map<Integer, String> headers, DataFormatter formatter) {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<Integer, String> e : headers.entrySet()) {
            Cell cell = row.getCell(e.getKey());
            values.put(e.getValue(), normalize(cellText(cell, formatter)));
            values.put("raw_" + e.getValue(), cellText(cell, formatter));
        }

        String beneficiaire = pick(values, "beneficaire");
        if (isBlank(beneficiaire)) {
            return null;
        }

        Situation s = new Situation();
        s.setBeneficiaire(beneficiaire);
        s.setBe(pick(values, "be"));
        s.setDateOp(parseDate(pick(values, "dateop", "dateov", "date")));
        s.setNumeroOv(pick(values, "nov", "numeroov", "ov"));
        s.setCheque(pick(values, "cheque"));
        s.setMontantOvCheque(parseDecimal(pick(values, "montantovcheque", "montantovcheq")));
        s.setBudget(pick(values, "budget"));
        s.setRubriqueBudg(pick(values, "rubriquebudg", "rubriquebudg"));
        s.setNumeroOp(pick(values, "nop", "numeroop"));
        s.setMontantOp(parseDecimal(pick(values, "montantop", "montant")));
        s.setObjetDepense(pick(values, "objetdepense"));
        s.setAnneeOrigine(pick(values, "anneedorigine", "anneedorig", "anneeorigine"));
        s.setSituation(pick(values, "situation"));
        s.setDateVirement(parseDate(pick(values, "datevirement", "virement")));
        s.setProjet(pick(values, "projet"));
        s.setBeUrl(pick(values, "beurl", "belink", "url"));
        modelSanitizer.sanitizeSituation(s);
        return s;
    }

    private static int findExcelHeaderRow(Sheet sheet, DataFormatter formatter) {
        int max = Math.min(sheet.getLastRowNum(), 25);
        for (int i = 0; i <= max; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String combined = "";
            for (Cell cell : row) {
                combined += " " + normalize(cellText(cell, formatter));
            }
            if (combined.contains("beneficaire") && combined.contains("budget")) {
                return i;
            }
        }
        return -1;
    }

    private static int findCsvHeaderLine(List<String> lines) {
        int max = Math.min(lines.size() - 1, 25);
        for (int i = 0; i <= max; i++) {
            String n = normalize(lines.get(i));
            if (n.contains("beneficaire") && n.contains("budget")) {
                return i;
            }
        }
        return -1;
    }

    private static String pick(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get("raw_" + key);
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String value(CSVRecord row, String... keys) {
        for (String key : keys) {
            if (row.isMapped(key)) {
                String v = row.get(key);
                if (v != null && !v.trim().isEmpty()) {
                    return v.trim();
                }
            }
        }
        return null;
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        String text = formatter.formatCellValue(cell);
        return text == null ? null : text.trim();
    }

    private static LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }

        String raw = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }

        String normalizedText = raw
            .replace(".", "")
            .replace('_', '-')
            .replace('/', '-')
            .replaceAll("\\s+", "-");

        String[] patterns = new String[] {
            "d-MMM-yy", "d-MMM-yyyy", "d-MMMM-yy", "d-MMMM-yyyy"
        };
        Locale[] locales = new Locale[] { Locale.FRENCH, Locale.ENGLISH };

        for (Locale locale : locales) {
            for (String pattern : patterns) {
                try {
                    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(pattern)
                        .toFormatter(locale);
                    return LocalDate.parse(normalizedText, formatter);
                } catch (DateTimeParseException ignored) {
                    // Try next locale/pattern.
                }
            }
        }

        LocalDate parsedNamed = parseNamedMonthDate(normalizedText);
        if (parsedNamed != null) {
            return parsedNamed;
        }

        throw new IllegalArgumentException("Invalid date value: " + value);
    }

    private static LocalDate parseNamedMonthDate(String value) {
        String[] parts = value.split("-");
        if (parts.length != 3) {
            return null;
        }

        Integer day = parseIntSafe(parts[0]);
        Integer month = monthFromToken(parts[1]);
        Integer year = parseYearSafe(parts[2]);
        if (day == null || month == null || year == null) {
            return null;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Integer parseIntSafe(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseYearSafe(String token) {
        Integer y = parseIntSafe(token);
        if (y == null) {
            return null;
        }
        if (token.length() == 2) {
            return y >= 70 ? 1900 + y : 2000 + y;
        }
        return y;
    }

    private static Integer monthFromToken(String token) {
        String m = normalize(token);
        if (m.length() < 3) {
            return null;
        }
        if (m.startsWith("jan")) {
            return 1;
        }
        if (m.startsWith("fev") || m.startsWith("feb")) {
            return 2;
        }
        if (m.startsWith("mar")) {
            return 3;
        }
        if (m.startsWith("avr") || m.startsWith("apr")) {
            return 4;
        }
        if (m.startsWith("mai") || m.startsWith("may")) {
            return 5;
        }
        if (m.startsWith("jun") || m.startsWith("juin")) {
            return 6;
        }
        if (m.equals("jui") || m.startsWith("juil") || m.startsWith("jul")) {
            return 7;
        }
        if (m.startsWith("aou") || m.startsWith("aug")) {
            return 8;
        }
        if (m.startsWith("sep")) {
            return 9;
        }
        if (m.startsWith("oct")) {
            return 10;
        }
        if (m.startsWith("nov")) {
            return 11;
        }
        if (m.startsWith("dec")) {
            return 12;
        }
        return null;
    }

    private static BigDecimal parseDecimal(String value) {
        if (isBlank(value)) {
            return null;
        }
        String v = value.replace(" ", "");
        int lastComma = v.lastIndexOf(',');
        int lastDot = v.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                v = v.replace(".", "").replace(",", ".");
            } else {
                v = v.replace(",", "");
            }
        } else if (lastComma >= 0) {
            v = v.replace(",", ".");
        }
        return new BigDecimal(v);
    }

    private static char detectDelimiter(String line) {
        long commas = line.chars().filter(ch -> ch == ',').count();
        long semicolons = line.chars().filter(ch -> ch == ';').count();
        return semicolons > commas ? ';' : ',';
    }

    private static void validateMultipartFile(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        boolean allowedExtension = ALLOWED_EXTENSIONS.stream().anyMatch(filename::endsWith);
        if (!allowedExtension) {
            throw new IllegalArgumentException("Unsupported file type.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String normalizedType = contentType.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
                throw new IllegalArgumentException("Unsupported file type.");
            }
        }

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File is too large.");
        }
    }

    private static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String s = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
        return s.replaceAll("[^a-z0-9]", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Map<String, Situation> loadExistingByKey() {
        Map<String, Situation> map = new HashMap<>();
        for (Situation s : situationRepository.findAll()) {
            map.put(buildBusinessKey(s), s);
        }
        return map;
    }

    private static String buildBusinessKey(Situation s) {
        if (!isBlank(s.getNumeroOp())) {
            return "op|" + str(s.getNumeroOp());
        }
        if (!isBlank(s.getNumeroOv())) {
            return "ov|" + str(s.getNumeroOv());
        }
        if (!isBlank(s.getCheque())) {
            return "ch|" + str(s.getCheque());
        }
        return "sig|" + String.join("|",
            str(s.getBeneficiaire()),
            str(s.getBe()),
            str(s.getDateOp()),
            str(s.getMontantOvCheque()),
            str(s.getMontantOp()),
            str(s.getBudget()),
            str(s.getProjet()),
            str(s.getBeUrl())
        );
    }

    private static void copyValues(Situation target, Situation src) {
        target.setBeneficiaire(src.getBeneficiaire());
        target.setBe(src.getBe());
        target.setDateOp(src.getDateOp());
        target.setNumeroOv(src.getNumeroOv());
        target.setCheque(src.getCheque());
        target.setMontantOvCheque(src.getMontantOvCheque());
        target.setBudget(src.getBudget());
        target.setRubriqueBudg(src.getRubriqueBudg());
        target.setNumeroOp(src.getNumeroOp());
        target.setMontantOp(src.getMontantOp());
        target.setObjetDepense(src.getObjetDepense());
        target.setAnneeOrigine(src.getAnneeOrigine());
        target.setSituation(src.getSituation());
        target.setDateVirement(src.getDateVirement());
        target.setProjet(src.getProjet());
        target.setBeUrl(src.getBeUrl());
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }
}
