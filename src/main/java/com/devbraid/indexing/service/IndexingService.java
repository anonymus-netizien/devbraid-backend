package com.devbraid.indexing.service;

import com.devbraid.indexing.entity.CodebaseIndex;
import com.devbraid.indexing.entity.FileIndex;
import com.devbraid.indexing.repository.CodebaseIndexRepository;
import com.devbraid.indexing.repository.FileIndexRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for indexing codebase files — extracts functions, classes, imports,
 * and dependencies from source files to build a code graph for AI analysis.
 * Runs asynchronously to avoid blocking the request thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {

    // Language detection patterns
    private static final Map<String, Pattern> LANGUAGE_PATTERNS;
    // Java-specific patterns
    private static final Pattern JAVA_CLASS = Pattern.compile(
            "(?:public|private|protected)?\\s*(?:abstract|final|static)?\\s*(?:class|interface|enum)\\s+(\\w+)"
    );
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?(\\S+)\\s+(\\w+)\\s*\\("
    );
    private static final Pattern JAVA_IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);", Pattern.MULTILINE);
    // TypeScript/JavaScript patterns
    private static final Pattern TS_CLASS = Pattern.compile(
            "(?:export\\s+)?(?:abstract\\s+)?class\\s+(\\w+)"
    );
    private static final Pattern TS_FUNCTION = Pattern.compile(
            "(?:export\\s+)?(?:async\\s+)?(?:function\\s+|(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s+)?(?:function|\\())"
    );
    private static final Pattern TS_IMPORT = Pattern.compile(
            "import\\s+(?:\\{[^}]*\\}|\\w+)\\s+from\\s+['\"]([^'\"]+)['\"]", Pattern.MULTILINE
    );
    private static final Pattern TS_EXPORT = Pattern.compile(
            "export\\s+(?:default\\s+)?(?:class|function|const|let|var|interface|type)\\s+(\\w+)", Pattern.MULTILINE
    );
    // Python patterns
    private static final Pattern PY_CLASS = Pattern.compile("^class\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern PY_FUNCTION = Pattern.compile("^(?:def|async\\s+def)\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern PY_IMPORT = Pattern.compile("^(?:import|from)\\s+(\\S+)", Pattern.MULTILINE);
    // Go patterns
    private static final Pattern GO_FUNCTION = Pattern.compile("^func\\s+(?:\\(\\w+\\s+\\*?\\w+\\)\\s+)?(\\w+)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern GO_STRUCT = Pattern.compile("^type\\s+(\\w+)\\s+struct", Pattern.MULTILINE);
    private static final Pattern GO_IMPORT = Pattern.compile("\"([^\"]+)\"", Pattern.MULTILINE);

    static {
        Map<String, Pattern> temp = new LinkedHashMap<>();
        temp.put("java", Pattern.compile("\\.java$"));
        temp.put("typescript", Pattern.compile("\\.ts$"));
        temp.put("javascript", Pattern.compile("\\.js$"));
        temp.put("tsx", Pattern.compile("\\.tsx$"));
        temp.put("jsx", Pattern.compile("\\.jsx$"));
        temp.put("python", Pattern.compile("\\.py$"));
        temp.put("go", Pattern.compile("\\.go$"));
        temp.put("rust", Pattern.compile("\\.rs$"));
        temp.put("sql", Pattern.compile("\\.sql$"));
        temp.put("yaml", Pattern.compile("\\.(yml|yaml)$"));
        temp.put("json", Pattern.compile("\\.json$"));
        temp.put("xml", Pattern.compile("\\.(xml|xsl|xslt)$"));
        temp.put("markdown", Pattern.compile("\\.md$"));
        temp.put("dockerfile", Pattern.compile("(?i)Dockerfile$"));
        temp.put("shell", Pattern.compile("\\.(sh|bash)$"));
        LANGUAGE_PATTERNS = Collections.unmodifiableMap(temp);
    }

    private final CodebaseIndexRepository codebaseIndexRepository;
    private final FileIndexRepository fileIndexRepository;

    /**
     * Create a new codebase index entry (PENDING status).
     */
    @Transactional
    public CodebaseIndex createIndex(User user, String repository, String branch) {
        var existing = codebaseIndexRepository.findByUserIdAndRepositoryAndBranch(
                user.getId(), repository, branch);
        if (existing.isPresent()) {
            return existing.get();
        }

        CodebaseIndex index = CodebaseIndex.builder()
                .user(user)
                .repository(repository)
                .branch(branch)
                .status("PENDING")
                .build();
        return codebaseIndexRepository.save(index);
    }

    /**
     * Start indexing — marks the index as INDEXING and processes files asynchronously.
     */
    @Async
    public void startIndexing(UUID indexId, List<String> fileContents) {
        CodebaseIndex index = codebaseIndexRepository.findById(indexId).orElse(null);
        if (index == null) {
            log.error("Codebase index not found: {}", indexId);
            return;
        }

        index.setStatus("INDEXING");
        codebaseIndexRepository.save(index);

        try {
            int totalFunctions = 0;
            int totalClasses = 0;
            int indexedFiles = 0;

            for (String fileContent : fileContents) {
                try {
                    FileIndex fileIndex = parseFile(index, fileContent);
                    if (fileIndex != null) {
                        fileIndexRepository.save(fileIndex);
                        totalFunctions += fileIndex.getFunctionCount();
                        totalClasses += fileIndex.getClassCount();
                        indexedFiles++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse file: {}", e.getMessage());
                }
            }

            index.setTotalFiles(fileContents.size());
            index.setIndexedFiles(indexedFiles);
            index.setTotalFunctions(totalFunctions);
            index.setTotalClasses(totalClasses);
            index.setStatus("COMPLETED");
            codebaseIndexRepository.save(index);

            log.info("Indexed {} files for {}/{} — {} functions, {} classes",
                    indexedFiles, index.getRepository(), index.getBranch(), totalFunctions, totalClasses);

        } catch (Exception e) {
            index.setStatus("FAILED");
            index.setErrorMessage(e.getMessage());
            codebaseIndexRepository.save(index);
            log.error("Indexing failed for {}/{}: {}", index.getRepository(), index.getBranch(), e.getMessage());
        }
    }

    /**
     * Parse a single file and extract structural information.
     */
    private FileIndex parseFile(CodebaseIndex codebaseIndex, String fileContent) {
        String[] lines = fileContent.split("\n");
        String filePath = lines.length > 0 ? extractFilePath(fileContent) : "unknown";

        String language = detectLanguage(filePath);
        if (language == null) {
            return null;
        }

        FileIndex fileIndex = FileIndex.builder()
                .codebaseIndex(codebaseIndex)
                .filePath(filePath)
                .fileType(extractFileType(filePath))
                .language(language)
                .lineCount(lines.length)
                .build();

        switch (language) {
            case "java" -> parseJavaFile(fileIndex, fileContent);
            case "typescript", "javascript", "tsx", "jsx" -> parseTsFile(fileIndex, fileContent);
            case "python" -> parsePythonFile(fileIndex, fileContent);
            case "go" -> parseGoFile(fileIndex, fileContent);
            default -> { /* skip non-indexable languages */ }
        }

        fileIndex.setRiskSignals(detectRiskSignals(fileContent, language));
        return fileIndex;
    }

    private void parseJavaFile(FileIndex fileIndex, String content) {
        List<String> classes = new ArrayList<>();
        Matcher classMatcher = JAVA_CLASS.matcher(content);
        while (classMatcher.find()) {
            classes.add(classMatcher.group(1));
        }
        fileIndex.setClasses(String.join(",", classes));
        fileIndex.setClassCount(classes.size());

        List<String> methods = new ArrayList<>();
        Matcher methodMatcher = JAVA_METHOD.matcher(content);
        while (methodMatcher.find()) {
            methods.add(methodMatcher.group(2) + ":" + methodMatcher.group(1));
        }
        fileIndex.setFunctions(String.join(",", methods));
        fileIndex.setFunctionCount(methods.size());

        List<String> imports = new ArrayList<>();
        Matcher importMatcher = JAVA_IMPORT.matcher(content);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        fileIndex.setImports(String.join(",", imports));
    }

    private void parseTsFile(FileIndex fileIndex, String content) {
        List<String> classes = new ArrayList<>();
        Matcher classMatcher = TS_CLASS.matcher(content);
        while (classMatcher.find()) {
            classes.add(classMatcher.group(1));
        }
        fileIndex.setClasses(String.join(",", classes));
        fileIndex.setClassCount(classes.size());

        List<String> functions = new ArrayList<>();
        Matcher funcMatcher = TS_FUNCTION.matcher(content);
        while (funcMatcher.find()) {
            String name = funcMatcher.group(1);
            if (name != null) functions.add(name);
        }
        fileIndex.setFunctions(String.join(",", functions));
        fileIndex.setFunctionCount(functions.size());

        List<String> imports = new ArrayList<>();
        Matcher importMatcher = TS_IMPORT.matcher(content);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        fileIndex.setImports(String.join(",", imports));

        List<String> exports = new ArrayList<>();
        Matcher exportMatcher = TS_EXPORT.matcher(content);
        while (exportMatcher.find()) {
            exports.add(exportMatcher.group(1));
        }
        fileIndex.setExports(String.join(",", exports));
    }

    private void parsePythonFile(FileIndex fileIndex, String content) {
        List<String> classes = new ArrayList<>();
        Matcher classMatcher = PY_CLASS.matcher(content);
        while (classMatcher.find()) {
            classes.add(classMatcher.group(1));
        }
        fileIndex.setClasses(String.join(",", classes));
        fileIndex.setClassCount(classes.size());

        List<String> functions = new ArrayList<>();
        Matcher funcMatcher = PY_FUNCTION.matcher(content);
        while (funcMatcher.find()) {
            functions.add(funcMatcher.group(1));
        }
        fileIndex.setFunctions(String.join(",", functions));
        fileIndex.setFunctionCount(functions.size());

        List<String> imports = new ArrayList<>();
        Matcher importMatcher = PY_IMPORT.matcher(content);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        fileIndex.setImports(String.join(",", imports));
    }

    private void parseGoFile(FileIndex fileIndex, String content) {
        List<String> structs = new ArrayList<>();
        Matcher structMatcher = GO_STRUCT.matcher(content);
        while (structMatcher.find()) {
            structs.add(structMatcher.group(1));
        }
        fileIndex.setClasses(String.join(",", structs));
        fileIndex.setClassCount(structs.size());

        List<String> functions = new ArrayList<>();
        Matcher funcMatcher = GO_FUNCTION.matcher(content);
        while (funcMatcher.find()) {
            functions.add(funcMatcher.group(1));
        }
        fileIndex.setFunctions(String.join(",", functions));
        fileIndex.setFunctionCount(functions.size());

        List<String> imports = new ArrayList<>();
        Matcher importMatcher = GO_IMPORT.matcher(content);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        fileIndex.setImports(String.join(",", imports));
    }

    /**
     * Detect risk signals in file content.
     */
    private String detectRiskSignals(String content, String language) {
        List<String> signals = new ArrayList<>();

        if (content.split("\n").length > 300) {
            signals.add("large_file");
        }

        if (language.equals("java") && JAVA_METHOD.matcher(content).results().count() > 20) {
            signals.add("god_class");
        }

        if (content.contains("password") || content.contains("secret") || content.contains("api_key")) {
            signals.add("credential_reference");
        }

        if (content.contains("@Deprecated") || content.contains("@SuppressWarnings")) {
            signals.add("deprecated_or_suppressed");
        }

        if (content.contains("        ") && content.contains("                ")) {
            signals.add("deep_nesting");
        }

        return signals.isEmpty() ? null : String.join(",", signals);
    }

    private String detectLanguage(String filePath) {
        for (Map.Entry<String, Pattern> entry : LANGUAGE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(filePath).find()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String extractFilePath(String content) {
        int firstNewline = content.indexOf('\n');
        return firstNewline > 0 ? content.substring(0, firstNewline).trim() : "unknown";
    }

    private String extractFileType(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(lastDot + 1).toLowerCase() : "unknown";
    }

    // ── Query methods ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<CodebaseIndex> getIndex(UUID indexId) {
        return codebaseIndexRepository.findById(indexId);
    }

    @Transactional(readOnly = true)
    public List<CodebaseIndex> listIndexesByUser(User user) {
        return codebaseIndexRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public List<FileIndex> getFilesByIndex(UUID indexId) {
        return fileIndexRepository.findByCodebaseIndexIdOrderByFilePath(indexId);
    }

    @Transactional(readOnly = true)
    public List<FileIndex> searchFiles(UUID indexId, String pattern) {
        return fileIndexRepository.findByIndexIdAndPathPattern(indexId, pattern);
    }

    @Transactional(readOnly = true)
    public List<FileIndex> getFilesByLanguage(UUID indexId, String language) {
        return fileIndexRepository.findByIndexIdAndLanguage(indexId, language);
    }
}
