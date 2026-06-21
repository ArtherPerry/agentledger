package com.agentledger.service;

import com.agentledger.model.PlatformRow;
import com.agentledger.model.ReportSummary;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public final class ExcelExport {
    private ExcelExport() {}

    public static void writeSummary(File file, String branchName, String from, String to,
                                    ReportSummary s, List<PlatformRow> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Report");

            Font bold = wb.createFont(); bold.setBold(true);
            CellStyle h = wb.createCellStyle(); h.setFont(bold);

            int r = 0;
            row(sh, r++, "AgentLedger — " + branchName);
            row(sh, r++, "Period: " + from + "  to  " + to);
            r++;
            row(sh, r++, "Txn count", String.valueOf(s.txnCount()));
            row(sh, r++, "Total fee (kyat)", kyat(s.totalFeePya()));
            row(sh, r++, "Total commission (kyat)", kyat(s.totalCommissionPya()));
            r++;

            Row head = sh.createRow(r++);
            String[] cols = {"Platform", "Count", "Amount (kyat)", "Commission (kyat)"};
            for (int i = 0; i < cols.length; i++) { Cell c = head.createCell(i); c.setCellValue(cols[i]); c.setCellStyle(h); }

            for (PlatformRow pr : rows) {
                Row rr = sh.createRow(r++);
                rr.createCell(0).setCellValue(pr.platform());
                rr.createCell(1).setCellValue(pr.count());
                rr.createCell(2).setCellValue(Double.parseDouble(kyat(pr.amountPya())));
                rr.createCell(3).setCellValue(Double.parseDouble(kyat(pr.commissionPya())));
            }
            for (int i = 0; i < 4; i++) sh.autoSizeColumn(i);

            try (FileOutputStream out = new FileOutputStream(file)) { wb.write(out); }
        }
    }

    private static void row(Sheet sh, int r, String a) { sh.createRow(r).createCell(0).setCellValue(a); }
    private static void row(Sheet sh, int r, String a, String b) {
        Row rr = sh.createRow(r); rr.createCell(0).setCellValue(a); rr.createCell(1).setCellValue(b);
    }
    /** pya -> plain "1234.56" string for Excel numeric cells. */
    private static String kyat(long pya) { return String.format("%d.%02d", pya / 100, Math.abs(pya % 100)); }
}