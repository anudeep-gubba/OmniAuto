package com.framework.testdata;

import com.framework.exceptions.TestDataException;
import com.framework.utils.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code .xlsx}/{@code .xls} test data: row-oriented like CSV, taking the first
 * sheet's row 0 as headers and every subsequent non-blank row as one record. A column
 * literally named {@code name} opts a workbook into {@link TestData#get(String)} lookups,
 * same convention as {@link CsvDataReader} - including its dotted-column nesting convention
 * (e.g. {@code metadata.testCaseId}, {@code data.email}) via {@link TestDataReader#unflatten}.
 *
 * <p>{@link DataFormatter} renders every cell to the same text Excel itself would display
 * (so a numeric cell showing {@code 50} becomes the string {@code "50"}, not
 * {@code "50.0"}) - values come back as {@link String} for the same reason
 * {@link CsvDataReader} does, and the same lenient conversion path handles typed
 * {@link TestData#get(String, Class)} access.</p>
 */
final class ExcelDataReader implements TestDataReader {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    @Override
    public List<Map<String, Object>> read(String classpathResource) {
        try (InputStream in = FileUtils.openClasspathResource(classpathResource);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new TestDataException(
                        "Excel test data file '" + classpathResource + "' has no header row in its first sheet.");
            }
            List<String> headers = readHeaders(headerRow);

            List<Map<String, Object>> records = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, headers.size())) {
                    continue;
                }
                records.add(TestDataReader.unflatten(toRecord(row, headers)));
            }
            return records;
        } catch (IOException e) {
            throw new TestDataException("Failed to read/parse Excel test data file '" + classpathResource + "'.", e);
        }
    }

    private static List<String> readHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            String header = DATA_FORMATTER.formatCellValue(cell).trim();
            if (!header.isEmpty()) {
                headers.add(header);
            }
        }
        return headers;
    }

    private static Map<String, String> toRecord(Row row, List<String> headers) {
        Map<String, String> record = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i);
            record.put(headers.get(i), cell == null ? "" : DATA_FORMATTER.formatCellValue(cell));
        }
        return record;
    }

    private static boolean isBlankRow(Row row, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !DATA_FORMATTER.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }
}
