package com.arare.features.dataimport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

/**
 * RFC 4180 CSV parsing / writing plus the natural-key normalization rules
 * shared by every import and export path in the application.
 *
 * <p>All map keys returned by {@link #parse(String)} are header-normalized:
 * trimmed, lower-cased, stripped of spaces/underscores and of the UTF-8 BOM.
 * This makes the parser tolerant to header variations such as
 * {@code employeeId}, {@code EMPLOYEE ID} or {@code employee_id}.
 */
public final class CsvUtils {

    /** Upper bound for data rows in a single CSV payload (100k). */
    public static final int MAX_DATA_ROWS_PER_FILE = 100_000;

    private CsvUtils() {
    }

    // 
    // Parsing
    // 

    /**
     * Parses RFC 4180 CSV text into a list of rows keyed by normalized headers.
     * A trailing UTF-8 BOM on the first value is stripped during normalization.
     * Header-only content (no data rows) parses to an empty list.
     *
     * @throws IllegalArgumentException if the content is blank or has no header row
     */
    public static List<Map<String, String>> parse(String csvContent) {
        return parse(csvContent, MAX_DATA_ROWS_PER_FILE);
    }

    /**
     * Parses RFC 4180 CSV text into a list of rows keyed by normalized headers,
     * refusing payloads with more than {@code maxRows} data rows so that
     * unbounded uploads cannot exhaust memory.
     * A trailing UTF-8 BOM on the first value is stripped during normalization.
     * Header-only content (no data rows) parses to an empty list.
     *
     * @throws IllegalArgumentException if the content is blank, has no header row,
     *         or exceeds the maximum row count
     */
    public static List<Map<String, String>> parse(String csvContent, int maxRows) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new IllegalArgumentException("CSV content cannot be blank");
        }
        String[] lines = csvContent.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length < 1 || lines[0].isBlank()) {
            throw new IllegalArgumentException("CSV must include a header row");
        }

        List<String> headers = parseLine(lines[0]).stream().map(CsvUtils::normalizeHeader).toList();

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> values = parseLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), c < values.size() ? values.get(c).trim() : "");
            }
            rows.add(row);
            if (rows.size() > maxRows) {
                throw new IllegalArgumentException(
                    "CSV exceeds the maximum of " + maxRows + " data rows per file");
            }
        }
        return rows;
    }

    /**
     * Parses a single RFC 4180 line into its field values, honoring
     * quoted fields and escaped quotes ({@code ""} inside {@code "..."}).
     */
    public static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    // 
    // Writing
    // 

    /**
     * Serializes a {@code [headers, rows]} collection into RFC 4180 text,
     * prefixed with a UTF-8 BOM so that Microsoft Excel recognizes UTF-8.
     */
    public static String write(String[] headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        appendEscaped(sb, List.of(headers));
        for (List<String> row : rows) {
            appendEscaped(sb, row);
        }
        return sb.toString();
    }

    /** Serializes a single row of values into one RFC 4180 line. */
    public static String toCsvLine(List<String> values) {
        StringBuilder sb = new StringBuilder();
        appendEscaped(sb, values);
        return sb.toString();
    }

    private static void appendEscaped(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(values.get(i)));
        }
        sb.append('\n');
    }

    /** RFC 4180 field escaping: quote when the value contains , " or newlines. */
    public static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // 
    // Row value helpers
    // 

    /** Returns the trimmed value or {@code null} when the cell is empty. */
    public static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Returns a required trimmed cell value, throwing with row context. */
    public static String required(Map<String, String> row, String column, int rowNumber) {
        String value = blankToNull(row.get(column));
        if (value == null) {
            throw new IllegalArgumentException("Missing required column '" + column + "' at row " + rowNumber);
        }
        return value;
    }

    /** Splits a cell on {@code ;} or {@code |} into trimmed, non-empty tokens. */
    public static Set<String> splitTokens(String raw) {
        String value = blankToNull(raw);
        if (value == null) return Set.of();
        return Arrays.stream(value.split("[;|]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    // 
    // Scalar parsing helpers
    // 

    public static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        try {
            return Enum.valueOf(type, normalize(raw));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " value: '" + raw + "'");
        }
    }

    public static <E extends Enum<E>> E parseEnumOrNull(Class<E> type, String raw) {
        String value = blankToNull(raw);
        return value == null ? null : parseEnum(type, value);
    }

    public static <E extends Enum<E>> E parseEnumOrDefault(Class<E> type, String raw, E defaultValue) {
        String value = blankToNull(raw);
        return value == null ? defaultValue : parseEnum(type, value);
    }

    public static boolean parseBooleanOrDefault(String raw, boolean defaultValue) {
        String value = blankToNull(raw);
        if (value == null) return defaultValue;
        return switch (normalize(value)) {
            case "TRUE", "YES", "Y", "1" -> true;
            case "FALSE", "NO", "N", "0" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean value: '" + raw + "'");
        };
    }

    public static int parseIntOrDefault(String raw, int defaultValue) {
        String value = blankToNull(raw);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer value: '" + raw + "'");
        }
    }

    public static Integer optionalInt(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer value: '" + raw + "'");
        }
    }

    public static Optional<Long> parseLong(String raw) {
        try {
            return Optional.of(Long.parseLong(raw.trim()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    // 
    // Natural-key builders (mirror the schema UNIQUE constraints exactly)
    // 

    /** Normalizes any identifier for case-insensitive comparisons. */
    public static String key(String raw) {
        if (raw == null) return "";
        return raw.replace("\uFEFF", "").trim().toUpperCase(Locale.ROOT);
    }

    /** Timeslot natural key = DAY|HH:MM|HH:MM (mirrors UNIQUE(day, start_time, end_time)). */
    public static String timeslotKey(String day, String start, String end) {
        return key(day) + "|" + normalizeTime(start) + "|" + normalizeTime(end);
    }

    /** Normalizes a time to {@code HH:mm} via {@link LocalTime} so CSV "9:00" matches stored "09:00". */
    private static String normalizeTime(String time) {
        if (time == null) return "";
        try {
            return LocalTime.parse(time.trim()).toString();
        } catch (Exception ex) {
            return time.trim();
        }
    }

    /** Room natural key = BUILDING|ROOMNUMBER (mirrors UNIQUE(building_id, room_number)). */
    public static String roomKey(String buildingName, String roomNumber) {
        return key(buildingName) + "|" + key(roomNumber);
    }

    /** Subject natural key = DEPTCODE|SUBCODE (scope per department). */
    public static String subjectKey(String deptCode, String subjectCode) {
        return key(deptCode) + "|" + key(subjectCode);
    }

    /** Batch natural key = DEPTCODE|year|SECTION (mirrors UNIQUE(department_id, year, section)). */
    public static String batchKey(String deptCode, int year, String section) {
        return key(deptCode) + "|" + year + "|" + key(section);
    }

    /** Normalizes an entity name/code for case-insensitive lookups. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replace("\uFEFF", "").trim().toUpperCase(Locale.ROOT);
    }

    /** Normalizes a CSV header: lowercase, no spaces or underscores, no BOM. */
    public static String normalizeHeader(String raw) {
        if (raw == null) return "";
        String value = raw.replace("\uFEFF", "").replace("\u00EF\u00BB\u00BF", "");
        return value.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }
}