package ua.profitsoft.ootest.openofficetest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@SpringBootApplication
public class OpenofficetestApplication implements CommandLineRunner {

  private static final List<String> DOCUMENT_EXTENSIONS = Arrays.asList("xls", "xlsx", "doc", "docx");

  public static void main(String[] args) throws Exception {
    Files.createDirectories(Paths.get("./out"));
    SpringApplication.run(OpenofficetestApplication.class, args);
  }

  @Value("${jodConverter.threadsCount}")
  private int threadsCount;

  @Autowired
  private JodReportsDocumentConverter documentConverter;

  @Override
  public void run(String... args) throws Exception {
    String path = getArg("path", args);
    if (path == null) {
      log.error("parameter --path=<directory path> must be specified");
      return;
    }

    long totalFiles = getFilesStream(path).count();
    log.debug("Will process {} files in directory '{}'", totalFiles, path);

    // запускаем параллельно в несколько потоков
    ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
    List<CompletableFuture<Boolean>> futures = getFilesStream(path)
        .map(file -> CompletableFuture.supplyAsync(() -> convertToPdf(file), executor))
        .collect(Collectors.toList());

    long successfullyConverted = futures.stream()
        .map(CompletableFuture::join)
        .filter(Boolean::booleanValue)
        .count();

    log.info("Converted successfully {} files from {}", successfullyConverted, totalFiles);
    System.exit(0);
  }

  private Stream<Path> getFilesStream(String path) throws IOException {
    return Files.walk(Paths.get(path), 1, FileVisitOption.FOLLOW_LINKS)
        .filter(f -> !Files.isDirectory(f) && DOCUMENT_EXTENSIONS.contains(getExtension(f)));
  }

  private String getExtension(Path f) {
    return FilenameUtils.getExtension(f.getFileName().toString());
  }

  private boolean convertToPdf(Path file) {
    String targetFilePath = "./out/" + FilenameUtils.getBaseName(file.getFileName().toString()) + ".pdf";
    log.debug("Start converting document {} to {}", file.getFileName().toString(), targetFilePath);
    long time = System.currentTimeMillis();
    try (OutputStream out = new FileOutputStream(targetFilePath)){
      documentConverter.convertDocument(
          new FileSystemResource(file), getExtension(file),
          out, "pdf");
      log.debug("Finished converting document {} in {}ms", targetFilePath, System.currentTimeMillis() - time);
      return true;
    } catch (Exception e) {
      log.error("Error converting document", e);
      return false;
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
