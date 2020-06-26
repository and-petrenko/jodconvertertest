package ua.profitsoft.ootest.openofficetest;

import com.artofsolving.jodconverter.DocumentFormatRegistry;
import com.artofsolving.jodconverter.openoffice.connection.OpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.connection.SocketOpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.converter.StreamOpenOfficeDocumentConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Отвечает за конвертаццию документов с помощью OpenOffice или LibreOffice.
 */
@Slf4j
@Component
public class JodReportsDocumentConverter {

    private static final DocumentFormatRegistry FORMAT_REGISTRY = new ExtendedDocumentFormatRegistry();

    private JodReportConnectionParams connectionParams;

    private int timeoutSeconds;

    private int conversionAttempts;

    private ExecutorService threadPool;

    public JodReportsDocumentConverter(@Autowired JodReportConnectionParams connectionParams,
                                       @Value("${jodConverter.timeoutSeconds}") int timeoutSeconds,
                                       @Value("${jodConverter.conversionAttempts}") int conversionAttempts,
                                       @Value("${jodConverter.threadsCount}") int threadsCount) {

        if (conversionAttempts < 1) {
            throw new IllegalArgumentException("conversionAttempts must be 1 or more, but was " + conversionAttempts);
        }
        if (threadsCount < 1) {
            throw new IllegalArgumentException("threadsCount must be 1 or more, but was " + threadsCount);
        }

        this.connectionParams = connectionParams;
        this.timeoutSeconds = timeoutSeconds;
        this.conversionAttempts = conversionAttempts;
        this.threadPool = Executors.newFixedThreadPool(threadsCount);
    }

    public void convertDocument(FileSystemResource inputStreamSource, String fromExtension,
                                OutputStream out, String toExtension) {

        // конвертируем документ в цикле, чтобы, если с первого раза не получится, попробовать еще раз (и так, максимум, 3 раза)
        // на 3-й раз будем бросать exception
        for (int attempt = 1; attempt <= conversionAttempts; attempt++) {
            try (InputStream io = inputStreamSource.getInputStream()){
                // поскольку JodConverter не очень надежный, и, теоретически, может "отпасть" в процессе формирования,
                // а мы для надежности вызываем его несколько раз, то чтобы случайно не записать в out несколько фрагментов одного и того же файла,
                // сначала будем его писать в ByteArrayOutputStream, и только если все ок - то скопируем в целевой out
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                convertWithTimeout(io, fromExtension, bos, toExtension, attempt);
                // если все сформировалось без ошибок - копируем в out и выходим из цикла
                IOUtils.copy(new ByteArrayInputStream(bos.toByteArray()), out);
                break;
            } catch (DocumentGenerationException e) {
                if (attempt == conversionAttempts) {
                    log.error("File '{}' wasn't converted after {} attempts", inputStreamSource.getFilename(), conversionAttempts);
                    throw e;
                }
            } catch (IOException e) {
                // IOException в принципе не должно быть, но если вылезло - то сразу пробрасываем наверх
                throw new DocumentGenerationException("ERROR_GENERATION", e);
            }
        }
    }

    /**
     * Конвертирует документ с помощью JodConverter, в пределах заданного timeout (см. timeoutSeconds).
     * Использует для этого отдельный thead и CompletableFuture c таймаутом.
     * @throws DocumentGenerationException если возникает ошибка при конвертации, либо оно не вкладывается в отведенный временной промежуток.
     */
    private void convertWithTimeout(InputStream documentInputStream, String fromExtension,
                                    OutputStream out, String toExtension,
                                    int attempt) {

        // используем не CompletableFuture, а ExecutorService.submit(..), чтобы метод cancel() прерывал вложенный Thread
        // (в CompletableFuture он этого не делает, и можно потерять Thread-ы в пуле)
        Future<?> future = threadPool.submit(() -> connectAndConvert(documentInputStream, fromExtension, out, toExtension, connectionParams));
        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            log.error("Document conversion attempt #" + attempt + " was interrupted " +
                    "or couldn't be executed within timeout of " + timeoutSeconds + " seconds", e);
            // если свалился по таймауту - пробуем остановить сам thread,
            // иначе у нас забьется пул, и все вообще перестанет выполняться
            future.cancel(true);
            throw new DocumentGenerationException("ERROR_CONNECTION", e);
        } catch (ExecutionException e) {
            log.warn("Document conversion attempt #" + attempt + " failed", e);
            if (e.getCause() != null && e.getCause() instanceof DocumentGenerationException) {
                throw (DocumentGenerationException)e.getCause();
            }
            throw new DocumentGenerationException("ERROR_GENERATION", e);
        }
    }

    private void connectAndConvert(InputStream documentInputStream, String fromExtension, OutputStream out, String toExtension, JodReportConnectionParams connectionParams) {
        OpenOfficeConnection connection = new SocketOpenOfficeConnection(connectionParams.getHost(), connectionParams.getPort());
        boolean connected = false;
        try {
            connection.connect();
            connected = true;
            new StreamOpenOfficeDocumentConverter(connection, FORMAT_REGISTRY)
                    .convert(documentInputStream,
                            FORMAT_REGISTRY.getFormatByFileExtension(fromExtension),
                            out,
                            FORMAT_REGISTRY.getFormatByFileExtension(toExtension));
        } catch (ConnectException e) {
            log.error("Error connecting to Document Generation service with params " + connectionParams);
            throw new DocumentGenerationException("ERROR_CONNECTION", e);
        } finally {
            if (connected) {
                connection.disconnect();
            }
        }
    }

}
