package com.zhixue.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
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
}
