package ua.profitsoft.ootest.openofficetest;

import com.artofsolving.jodconverter.DefaultDocumentFormatRegistry;
import com.artofsolving.jodconverter.DocumentFamily;
import com.artofsolving.jodconverter.DocumentFormat;

/**
 * Extends DefaultDocumentFormatRegistry with new format 'xlsx'
 * @author a.lipavets
 */
public class ExtendedDocumentFormatRegistry extends DefaultDocumentFormatRegistry {

    public ExtendedDocumentFormatRegistry() {
        final DocumentFormat xlsx = new DocumentFormat("Microsoft Excel 2007 XML", DocumentFamily.SPREADSHEET, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        final DocumentFormat docx = new DocumentFormat("Microsoft Word 2007 XML", DocumentFamily.TEXT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document","docx");
        addDocumentFormat(xlsx);
        addDocumentFormat(docx);
    }
}
