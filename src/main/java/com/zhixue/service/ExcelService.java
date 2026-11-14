package com.zhixue.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 生成真正的 .xlsx（Excel）文件字节流 */
@Service
public class ExcelService {

    /** @param sheetName 工作表名
     *  @param rows      第一行视为表头；其余为数据行（每行的单元格用字符串表示） */
    public byte[] toXlsx(String sheetName, List<List<String>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName == null || sheetName.isEmpty() ? "Sheet1" : sheetName);

            // 表头样式：品牌紫底 + 白字加粗
            CellStyle headStyle = wb.createCellStyle();
            ((XSSFCellStyle) headStyle).setFillForegroundColor(new XSSFColor(new java.awt.Color(102, 126, 234), null));
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headStyle.setAlignment(HorizontalAlignment.CENTER);
            headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headStyle.setBorderBottom(BorderStyle.THIN);
            headStyle.setBorderTop(BorderStyle.THIN);
            headStyle.setBorderLeft(BorderStyle.THIN);
            headStyle.setBorderRight(BorderStyle.THIN);
            Font headFont = wb.createFont();
            headFont.setColor(IndexedColors.WHITE.getIndex());
            headFont.setBold(true);
            headStyle.setFont(headFont);

            // 数据行斑马纹 + 边框
            CellStyle evenStyle = wb.createCellStyle();
            ((XSSFCellStyle) evenStyle).setFillForegroundColor(new XSSFColor(new java.awt.Color(245, 247, 252), null));
            evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            evenStyle.setBorderBottom(BorderStyle.THIN);
            evenStyle.setBorderTop(BorderStyle.THIN);
            evenStyle.setBorderLeft(BorderStyle.THIN);
            evenStyle.setBorderRight(BorderStyle.THIN);
            CellStyle oddStyle = wb.createCellStyle();
            oddStyle.setBorderBottom(BorderStyle.THIN);
            oddStyle.setBorderTop(BorderStyle.THIN);
            oddStyle.setBorderLeft(BorderStyle.THIN);
            oddStyle.setBorderRight(BorderStyle.THIN);

            int maxCols = 0;
            for (int i = 0; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                Row r = sheet.createRow(i);
                if (row.size() > maxCols) maxCols = row.size();
                for (int j = 0; j < row.size(); j++) {
                    Cell cell = r.createCell(j);
                    String v = row.get(j);
                    cell.setCellValue(v == null ? "" : v);
                    if (i == 0) cell.setCellStyle(headStyle);
                    else cell.setCellStyle(i % 2 == 0 ? evenStyle : oddStyle);
                }
            }
            // 自动列宽（上限 60 字符宽，避免极宽）
            for (int j = 0; j < maxCols; j++) {
                sheet.autoSizeColumn(j);
                int w = sheet.getColumnWidth(j);
                if (w > 60 * 256) sheet.setColumnWidth(j, 60 * 256);
            }
            sheet.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成 Excel 失败：" + e.getMessage(), e);
        }
    }

    /** 读取 .xlsx 首个工作表，返回「行 × 列」字符串矩阵（供批量导入按列名映射字段）。
     *  整数数值按原样输出，避免学号 / 年级被读成 2.023090101E9 之类的科学计数法。 */
    public List<List<String>> readXlsx(byte[] bytes) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (wb.getNumberOfSheets() == 0) return rows;
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = Math.max(0, row.getFirstCellNum()); c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) { cells.add(""); continue; }
                        if (cell.getCellType() == CellType.NUMERIC) {
                            double d = cell.getNumericCellValue();
                            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                                cells.add(BigDecimal.valueOf(d).toBigInteger().toString());
                            } else cells.add(fmt.formatCellValue(cell));
                        } else cells.add(fmt.formatCellValue(cell).trim());
                    }
                }
                rows.add(cells);
            }
        }
        return rows;
    }
}
