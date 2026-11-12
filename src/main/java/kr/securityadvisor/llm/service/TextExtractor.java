package kr.securityadvisor.llm.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** 업로드 문서 텍스트 추출(PDF·DOCX·HWPX·HWP·TXT/MD). 길이는 모델 입력 한도에 맞춰 자른다. HWPX 는 OWPML 본문(section*.xml)의 문단 텍스트를 순서대로 모은다. */
@Component
public class TextExtractor {

    public static final int MAX_CHARS = 20_000;

    public String extract(String extension, InputStream in) throws IOException {
        String text = switch (extension) {
            case "pdf" -> {
                try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
                    yield ScannedPdfExtractor.extract(doc);
                }
            }
            case "docx" -> {
                try (XWPFDocument doc = new XWPFDocument(in);
                        XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    yield extractor.getText();
                }
            }
            case "hwpx" -> hwpx(in.readAllBytes());
            case "hwp" -> HwpTextExtractor.extract(in);
            case "txt", "md" -> new String(in.readAllBytes(), StandardCharsets.UTF_8);
            default -> throw new IOException("텍스트를 추출할 수 없는 형식입니다: " + extension);
        };
        String normalized = text == null ? "" : text.replace("\r\n", "\n").strip();
        return normalized.length() > MAX_CHARS ? normalized.substring(0, MAX_CHARS) : normalized;
    }

    private static final Pattern SECTION = Pattern.compile("Contents/section(\\d+)\\.xml");

    /** HWPX(zip) 안의 Contents/section N.xml 을 번호순으로 읽어 문단(hp:p)마다 한 줄, 문단 안의 텍스트(hp:t)를 이어 붙인다. */
    private static String hwpx(byte[] bytes) throws IOException {
        Map<Integer, byte[]> sections = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Matcher m = SECTION.matcher(entry.getName());
                if (m.matches()) {
                    sections.put(Integer.parseInt(m.group(1)), zip.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        if (sections.isEmpty()) {
            throw new IOException("HWPX 본문(Contents/section*.xml)을 찾을 수 없습니다");
        }
        StringBuilder out = new StringBuilder();
        for (byte[] xml : sections.values()) {
            Document doc;
            try {
                doc = secureParser().parse(new ByteArrayInputStream(xml));
            } catch (SAXException e) {
                throw new IOException("HWPX 본문 XML 을 읽을 수 없습니다", e);
            }
            NodeList paragraphs = doc.getElementsByTagNameNS("*", "p");
            for (int i = 0; i < paragraphs.getLength(); i++) {
                Node p = paragraphs.item(i);
                StringBuilder line = new StringBuilder();
                NodeList texts = ((Element) p).getElementsByTagNameNS("*", "t");
                for (int j = 0; j < texts.getLength(); j++) {
                    line.append(texts.item(j).getTextContent());
                }
                if (!line.isEmpty()) {
                    out.append(line).append('\n');
                }
            }
        }
        return out.toString();
    }

    /** XXE·외부 DTD 를 막은 DOM 파서(SECURE_CODING — 외부 문서는 신뢰하지 않는다). */
    private static DocumentBuilder secureParser() throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IOException("XML 파서를 만들 수 없습니다", e);
        }
    }
}
