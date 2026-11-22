package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * AI 助手通用文件上传（+ 按钮 / 粘贴附件）：落盘到 zhixue.chat.file-path
 * （E:/ai文件 图片缓存/chat-files），消息里保存 /files/chat/xxx 直链。
 * 纯文本/代码类与 docx / pptx / pdf / xlsx 抽取正文片段，由前端注入 AI 上下文；
 * docx 与 pptx 纯 JDK 解析，pdf 用 PDFBox、xlsx 用 POI（均为既有依赖）。
 */
@RestController
@RequestMapping("/api/ai")
public class ChatFileController {

    @Value("${zhixue.chat.file-path:E:/ai文件 图片缓存/chat-files}")
    private String fileDir;

    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private static final int EXCERPT_LIMIT = 12000;

    /** 纯文本/代码类扩展名：直接按 UTF-8 抽取正文 */
    private static final Set<String> TEXT_EXT = Set.of(
        "txt", "md", "csv", "json", "log", "xml", "yml", "yaml", "html", "htm", "css", "sql",
        "js", "ts", "jsx", "tsx", "py", "java", "c", "h", "cpp", "cs", "go", "sh", "properties");

    @PostMapping("/files")
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ApiResponse.fail("请选择文件");
        if (file.getSize() > MAX_SIZE) return ApiResponse.fail("文件不能超过 10MB");
        String orig = file.getOriginalFilename();
        String name = orig == null || orig.isBlank() ? "file" : orig;
        try {
            // 安全化存储名：去路径分隔/危险字符、限长，保留原名用于展示
            String base = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
            if (base.length() > 80) base = base.substring(base.length() - 80);
            String ext = base.contains(".")
                    ? base.substring(base.lastIndexOf('.') + 1).toLowerCase() : "";
            String stored = "f_" + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 8) + "_" + base;

            Path dir = Paths.get(fileDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path dest = dir.resolve(stored);
            // transferTo 相对路径会落到上传临时目录，这里确保绝对路径
            file.transferTo(dest.toFile());

            Map<String, Object> data = Map.of(
                    "url", "/files/chat/" + stored,
                    "name", name,
                    "size", file.getSize());
            String excerpt = extractText(ext, dest);
            if (excerpt != null) {
                java.util.Map<String, Object> full = new java.util.LinkedHashMap<>(data);
                full.put("excerpt", excerpt);
                return ApiResponse.ok(full);
            }
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("文件保存失败：" + e.getMessage());
        }
    }

    /** 可解析类型返回正文片段（超长截断），不可解析返回 null（仅存档不注入 AI） */
    private String extractText(String ext, Path p) {
        try {
            if (TEXT_EXT.contains(ext)) {
                return cap(Files.readString(p, StandardCharsets.UTF_8).trim());
            }
            if ("docx".equals(ext)) {
                // docx 本质是 zip：取 word/document.xml，按段落换行后剥标签
                try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(p))) {
                    ZipEntry e;
                    while ((e = zip.getNextEntry()) != null) {
                        if (!"word/document.xml".equals(e.getName())) continue;
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n; long total = 0;
                        while ((n = zip.read(buf)) > 0 && total < 4_000_000L) {
                            bos.write(buf, 0, n); total += n;
                        }
                        String xml = bos.toString(StandardCharsets.UTF_8);
                        xml = xml.replaceAll("(?i)</w:p>", "\n");
                        xml = xml.replaceAll("<[^>]+>", "");
                        xml = unescape(xml);
                        return cap(xml.replaceAll("\n{3,}", "\n\n").trim());
                    }
                }
            }
            if ("pptx".equals(ext)) {
                return cap(extractPptx(p));
            }
            if ("pdf".equals(ext)) {
                return cap(extractPdf(p));
            }
            if ("xlsx".equals(ext)) {
                return cap(extractXlsx(p));
            }
        } catch (Exception ignore) {
            // 抽取失败不影响文件存储，仅不注入 AI
        }
        return null;
    }

    /** pptx = zip：ppt/slides/slideN.xml 里正文在 <a:t>，段落按 </a:p> 换行，按页码顺序输出 */
    private String extractPptx(Path p) throws Exception {
        TreeMap<Integer, String> slides = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(p))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String n = e.getName();
                if (!n.startsWith("ppt/slides/slide") || !n.endsWith(".xml")) continue;
                int num = Integer.parseInt(n.replaceAll("\\D", ""));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int k; long total = 0;
                while ((k = zip.read(buf)) > 0 && total < 2_000_000L) {
                    bos.write(buf, 0, k); total += k;
                }
                String xml = bos.toString(StandardCharsets.UTF_8);
                xml = xml.replaceAll("(?i)</a:p>", "\n");
                xml = xml.replaceAll("<[^>]+>", "");
                xml = unescape(xml);
                slides.put(num, xml.replaceAll("\n{3,}", "\n\n").trim());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (var en : slides.entrySet()) {
            sb.append("〔第 ").append(en.getKey()).append(" 页〕\n")
              .append(en.getValue().isBlank() ? "（本页无文字）" : en.getValue()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** pdf 用 PDFBox 按页抽取 */
    private String extractPdf(Path p) throws Exception {
        try (PDDocument doc = PDDocument.load(Files.newInputStream(p))) {
            if (doc.isEncrypted()) doc.setAllSecurityToBeRemoved(true);
            PDFTextStripper ts = new PDFTextStripper();
            ts.setStartPage(1);
            ts.setEndPage(doc.getNumberOfPages());
            return cap(ts.getText(doc).trim());
        }
    }

    /** xlsx = POI：逐个工作表按行拼接（DataFormatter 规避科学计数法） */
    private String extractXlsx(Path p) throws Exception {
        DataFormatter fmt = new DataFormatter();
        StringBuilder sb = new StringBuilder();
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(p))) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                sb.append("〔工作表 ").append(sheet.getSheetName()).append("〕\n");
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                        var cell = row.getCell(c);
                        cells.add(cell == null ? "" : fmt.formatCellValue(cell));
                    }
                    String line = String.join(" | ", cells);
                    if (!line.isBlank()) sb.append(line).append('\n');
                }
                sb.append('\n');
            }
        }
        return cap(sb.toString().trim());
    }

    /** XML 实体反转义（docx/pptx 剥标签后的收尾处理） */
    private String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&#160;", " ");
    }

    private String cap(String s) {
        if (s == null || s.isEmpty()) return null;
        return s.length() <= EXCERPT_LIMIT ? s : s.substring(0, EXCERPT_LIMIT) + "\n...[内容过长已截断]";
    }
}
