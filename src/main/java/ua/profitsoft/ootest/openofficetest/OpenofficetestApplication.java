package ua.profitsoft.ootest.openofficetest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.FileSystemResource;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Slf4j
@SpringBootApplication
public class OpenofficetestApplication implements CommandLineRunner {

  private static final List<String> DOCUMENT_EXTENSIONS = Arrays.asList("xls", "xlsx", "doc", "docx");

  public static void main(String[] args) throws Exception {
    Files.createDirectories(Paths.get("./out"));
    SpringApplication.run(OpenofficetestApplication.class, args);
  }

  @Autowired
  private JodReportsDocumentConverter documentConverter;

  @Override
  public void run(String... args) throws Exception {
    String path = getArg("path", args);
    if (path == null) {
      log.error("parameter --path=<directory path> must be specified");
      return;
    }

    log.debug("Will process {} files in directory '{}'", getFilesStream(path).count(), path);

    // запускаем параллельно в 4 потока
    ExecutorService executor = Executors.newFixedThreadPool(4);
    CompletableFuture[] futures = getFilesStream(path)
        .map(file -> CompletableFuture.runAsync(() -> convertToPdf(file), executor))
        .toArray(size -> new CompletableFuture[size]);
    CompletableFuture.allOf(futures).join();

    System.exit(0);
  }

  private Stream<Path> getFilesStream(String path) throws IOException {
    return Files.walk(Paths.get(path), 1, FileVisitOption.FOLLOW_LINKS)
        .filter(f -> !Files.isDirectory(f) && DOCUMENT_EXTENSIONS.contains(getExtension(f)));
  }

  private String getExtension(Path f) {
    return FilenameUtils.getExtension(f.getFileName().toString());
  }

  private void convertToPdf(Path file) {
    String targetFilePath = "./out/" + FilenameUtils.getBaseName(file.getFileName().toString()) + ".pdf";
    log.debug("Start converting document {} to {}", file.getFileName().toString(), targetFilePath);
    long time = System.currentTimeMillis();
    try (OutputStream out = new FileOutputStream(targetFilePath)){
      documentConverter.convertDocument(
          new FileSystemResource(file), getExtension(file),
          out, "pdf");
      log.debug("Finished converting document {} in {}ms", targetFilePath, System.currentTimeMillis() - time);
    } catch (IOException e) {
      log.error("Error converting document", e);
    }
  }

  private static String getArg(String argName, String[] args) {
    String fullArgPrefix = "--" + argName + "=";
    for (String arg : args) {
      if (arg.startsWith(fullArgPrefix)) {
        return arg.substring(fullArgPrefix.length());
      }
    }
    return null;
  }

}
