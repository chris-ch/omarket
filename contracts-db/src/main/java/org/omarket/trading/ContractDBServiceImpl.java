package org.omarket.trading;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.omarket.quotes.Security;
import rx.Observable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ContractDBServiceImpl implements ContractDBService {

    @Override
    public final ContractFilter filterCurrency(String currencyCode) {
        return new ContractFilter() {
            @Override
            protected boolean accept(String content) {
                return getCurrency().equals(currencyCode);
            }
        };
    }

    @Override
    public final ContractFilter filterExchange(String exchangeCode) {
        return new ContractFilter() {
            @Override
            protected boolean accept(String content) {
                return getPrimaryExchange().equals(exchangeCode);
            }
        };
    }

    @Override
    public final ContractFilter filterSecurityType(String securityType) {
        return new ContractFilter() {
            @Override
            protected boolean accept(String content) {
                return getSecurityType().equals(securityType);
            }
        };
    }

    @Override
    public ContractFilter composeFilter(ContractFilter... filters) {
        return new ContractFilter() {

            @Override
            protected String prepare(Path path) throws IOException {
                int count = path.getNameCount();
                for (ContractFilter filter : filters) {
                    filter.prepare(path);
                    filter.setFilename(path.getFileName());
                    filter.setPrimaryExchange(path.getName(count - 3));
                    filter.setCurrency(path.getName(count - 4));
                    filter.setSecurityType(path.getName(count - 5));
                }
                return super.prepare(path);
            }

            @Override
            protected boolean accept(String content) {
                boolean accepted = true;
                for (ContractFilter filter : filters) {
                    if (!filter.accept(content)) {
                        accepted = false;
                        break;
                    }
                }
                return accepted;
            }
        };
    }

    @Override
    public Security loadContract(Path contractsDirPath, String productCode) throws IOException {
        final Path[] targetFile = new Path[1];
        Files.walkFileTree(contractsDirPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (file.getFileName().toString().equals(productCode + ".json")) {
                    targetFile[0] = file;
                    return FileVisitResult.TERMINATE;
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }
        });
        Path descriptionFilePath = targetFile[0];
        if (descriptionFilePath == null) {
            throw new IOException("missing data for contract: " + productCode);
        }
        String content = Files.lines(descriptionFilePath, StandardCharsets.UTF_8).collect(Collectors.joining());
        JsonParser parser = new JsonParser();
        JsonObject jsonSecurity = parser.parse(content).getAsJsonObject();
        return Security.fromJson(jsonSecurity);
    }

    @Override
    public void saveContract(Path contractsDirPath, Security product) throws IOException {
        String primaryExchange = product.getExchange();
        String securityType = product.getSecurityType();
        String currency = product.getCurrency();
        String fileBaseName = product.getCode();
        String fileName = fileBaseName + ".json";
        Path exchangePath = contractsDirPath.resolve(securityType).resolve(currency).resolve(primaryExchange);
        String initials;
        if (fileBaseName.length() < 3) {
            initials = fileBaseName;
        } else {
            initials = fileBaseName.substring(0, 3);
        }
        Path targetPath = exchangePath.resolve(initials);
        if (Files.notExists(targetPath)) {
            Files.createDirectories(targetPath);
        }
        Path filePath = targetPath.resolve(fileName);
        if (Files.notExists(filePath)) {
            Files.createFile(filePath);
        }
        BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        writer.write(product.toJson());
        writer.close();
        log.info("saved contract: " + filePath);
    }

    @Override
    public Observable<Security> loadContracts(Path contractsDirPath, ContractFilter filter) throws IOException {
        List<Security> contracts = new LinkedList<>();
        Files.walkFileTree(contractsDirPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.json");
                if (!matcher.matches(file.getFileName())) {
                    return FileVisitResult.CONTINUE;
                }
                String content = filter.prepare(file);
                if (filter.accept(content)) {
                    JsonParser parser = new JsonParser();
                    JsonObject jsonContract = parser.parse(content).getAsJsonObject();
                    contracts.add(Security.fromJson(jsonContract));
                    log.debug("added contract: {}", jsonContract);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return Observable.from(contracts);
    }

}
