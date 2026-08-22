package com.arare.features.dataimport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvUtilsTest {

    @Test
    void parseHandlesQuotedFieldsAndEscapedQuotes() {
        String csv = String.join("\n",
            "name,note",
            "\"Smith, John\",\"He said \"\"hi\"\"\""
        );

        List<Map<String, String>> rows = CsvUtils.parse(csv);

        assertEquals(1, rows.size());
        assertEquals("Smith, John", rows.get(0).get("name"));
        assertEquals("He said \"hi\"", rows.get(0).get("note"));
    }

    @Test
    void parseNormalizesHeadersAndStripsBom() {
        String csv = String.join("\n",
            "\uFEFFEmployee ID,Start Time",
            "EMP1,09:00"
        );

        List<Map<String, String>> rows = CsvUtils.parse(csv);

        assertEquals("EMP1", rows.get(0).get("employeeid"));
        assertEquals("09:00", rows.get(0).get("starttime"));
    }

    @Test
    void parseSkipsBlankLines() {
        String csv = "day,time\nMONDAY,09:00\n\nTUESDAY,10:00\n";
        List<Map<String, String>> rows = CsvUtils.parse(csv);
        assertEquals(2, rows.size());
    }

    @Test
    void parseRejectsBlankContentButAcceptsHeaderOnly() {
        assertThrows(IllegalArgumentException.class, () -> CsvUtils.parse("   \n"));
        assertTrue(CsvUtils.parse("a,b\n").isEmpty(), "header-only CSV must parse to an empty row list");
    }

    @Test
    void parseRejectsContentExceedingMaxRows() {
        StringBuilder csv = new StringBuilder("day,time\n");
        for (int i = 0; i < 5; i++) {
            csv.append("MONDAY,09:00\n");
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> CsvUtils.parse(csv.toString(), 3));
        assertTrue(ex.getMessage().contains("3"));
    }

    @Test
    void parseAcceptsExactlyMaxRows() {
        StringBuilder csv = new StringBuilder("day,time\n");
        for (int i = 0; i < 3; i++) {
            csv.append("MONDAY,09:00\n");
        }
        assertEquals(3, CsvUtils.parse(csv.toString(), 3).size());
    }

    @Test
    void writeProducesBomPrefixedRfc4180() {
        String csv = CsvUtils.write(
            new String[]{"name", "note"},
            List.of(List.of("Smith, John", "He said \"hi\"")));

        assertTrue(csv.startsWith("\uFEFF"), "output must start with a UTF-8 BOM");
        assertTrue(csv.contains("\"Smith, John\",\"He said \"\"hi\"\"\""), csv);
    }

    @Test
    void keyAndNaturalKeyBuildersNormalizeCase() {
        assertEquals("CSE", CsvUtils.key(" cse "));
        assertEquals("MONDAY|09:00|10:00", CsvUtils.timeslotKey("monday", "09:00", "10:00"));
        assertEquals("BLOCK A|LAB-101", CsvUtils.roomKey("block a", "lab-101"));
        assertEquals("CSE|CSE201", CsvUtils.subjectKey("cse", "cse201"));
        assertEquals("CSE|2|A", CsvUtils.batchKey("cse", 2, "a"));
    }

    @Test
    void requiredThrowsWhenColumnMissing() {
        Map<String, String> row = Map.of("name", "X");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> CsvUtils.required(row, "code", 2));
        assertTrue(ex.getMessage().contains("code"));
    }

    @Test
    void blankToNullAndSplitTokens() {
        assertNull(CsvUtils.blankToNull("   "));
        assertEquals("x", CsvUtils.blankToNull(" x "));
        assertEquals(List.of("A", "B"), List.copyOf(CsvUtils.splitTokens("A;B| B ")));
    }

    @Test
    void booleanParsingAcceptsCommonShorthands() {
        assertTrue(CsvUtils.parseBooleanOrDefault("yes", false));
        assertTrue(CsvUtils.parseBooleanOrDefault("1", false));
        assertEquals(false, CsvUtils.parseBooleanOrDefault("no", true));
        assertEquals(true, CsvUtils.parseBooleanOrDefault("", true));
    }
}