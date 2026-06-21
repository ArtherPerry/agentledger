package com.agentledger.service;

import com.agentledger.i18n.Fmt;

import com.agentledger.i18n.I18n;

import com.agentledger.model.PlatformRow;
import com.agentledger.model.ReportSummary;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PdfExport {
    private PdfExport() {}

    private static BaseFont mmBase() throws Exception {
        // Extract the bundled TTF to a temp file, then register it with OpenPDF (embedded).
        Path tmp = Files.createTempFile("notomm", ".ttf");
        try (InputStream in = PdfExport.class.getResourceAsStream("/fonts/NotoSansMyanmar-Regular.ttf")) {
            if (in == null) throw new IllegalStateException("Myanmar font not found at /fonts/NotoSansMyanmar-Regular.ttf");
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return BaseFont.createFont(tmp.toAbsolutePath().toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }

    public static void writeSummary(File file, String branchName, String from, String to,
                                    ReportSummary s, List<PlatformRow> rows) throws Exception {
        BaseFont bf = mmBase();
        Font title = new Font(bf, 15, Font.BOLD);
        Font normal = new Font(bf, 11);
        Font small = new Font(bf, 9, Font.NORMAL, new Color(90, 105, 103));
        Font th = new Font(bf, 10, Font.BOLD, Color.WHITE);

        Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
        PdfWriter.getInstance(doc, new FileOutputStream(file));
        doc.open();

        doc.add(new Paragraph("AgentLedger — " + branchName, title));
        doc.add(new Paragraph(from + "  —  " + to, small));
        doc.add(new Paragraph(" ", normal));

        doc.add(new Paragraph(I18n.t("pdf.txnCount") + "  " + s.txnCount(), normal));
        doc.add(new Paragraph(I18n.t("pdf.totalFee") + "  " + Fmt.kyat(s.totalFeePya()), normal));
        doc.add(new Paragraph(I18n.t("pdf.totalComm") + "  " + Fmt.kyat(s.totalCommissionPya()), normal));
        doc.add(new Paragraph(" ", normal));

        com.lowagie.text.pdf.PdfPTable t = new com.lowagie.text.pdf.PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{3, 2, 3, 3});
        Color head = new Color(12, 59, 60);
        for (String c : new String[]{I18n.t("pdf.col.platform"), I18n.t("pdf.col.count"), I18n.t("pdf.col.amount"), I18n.t("pdf.col.comm")}) {
            com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new Phrase(c, th));
            cell.setBackgroundColor(head); cell.setPadding(6); t.addCell(cell);
        }
        for (PlatformRow pr : rows) {
            t.addCell(cell(pr.platform(), normal));
            t.addCell(cell(String.valueOf(pr.count()), normal));
            t.addCell(cell(Fmt.kyat(pr.amountPya()), normal));
            t.addCell(cell(Fmt.kyat(pr.commissionPya()), normal));
        }
        doc.add(t);
        doc.close();
    }

    private static com.lowagie.text.pdf.PdfPCell cell(String text, Font f) {
        com.lowagie.text.pdf.PdfPCell c = new com.lowagie.text.pdf.PdfPCell(new Phrase(text, f));
        c.setPadding(5);
        return c;
    }

    private static String kyat(long pya) { return String.format("%d.%02d", pya / 100, Math.abs(pya % 100)); }
}