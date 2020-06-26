package ua.profitsoft.ootest.openofficetest;

/**
 * Ошибка формирования документа
 */
public class DocumentGenerationException extends RuntimeException {

    public DocumentGenerationException(String generationError, Throwable cause) {
        super(generationError, cause);
    }

}
