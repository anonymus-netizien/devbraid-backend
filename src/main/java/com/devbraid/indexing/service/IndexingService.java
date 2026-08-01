package com.devbraid.indexing.service;

import com.devbraid.indexing.entity.CodebaseEdge;
import com.devbraid.indexing.entity.CodebaseIndex;
import com.devbraid.indexing.entity.CodebaseNode;
import com.devbraid.indexing.entity.FileIndex;
import com.devbraid.indexing.repository.CodebaseEdgeRepository;
import com.devbraid.indexing.repository.CodebaseIndexRepository;
import com.devbraid.indexing.repository.CodebaseNodeRepository;
import com.devbraid.indexing.repository.FileIndexRepository;
import com.devbraid.user.entity.User;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    // Java-specific pattern — used by detectRiskSignals for god-class heuristic
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?(\\S+)\\s+(\\w+)\\s*\\("
    );
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
    private final CodebaseNodeRepository codebaseNodeRepository;
    private final CodebaseEdgeRepository codebaseEdgeRepository;

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
        // ponytail: no try/catch — exceptions propagate to AsyncUncaughtExceptionHandler
        // which sets index status to FAILED via IndexingAsyncErrorHandler.
        CodebaseIndex index = codebaseIndexRepository.findById(indexId)
                .orElseThrow(() -> new IllegalStateException("Codebase index not found: " + indexId));

        index.setStatus("INDEXING");
        codebaseIndexRepository.save(index);

        // Re-index safety: clear any prior graph rows for this index before rebuilding.
        codebaseEdgeRepository.deleteByCodebaseIndexId(index.getId());
        codebaseNodeRepository.deleteByCodebaseIndexId(index.getId());
        index.setTotalDependencies(0);

        int totalFunctions = 0;
        int totalClasses = 0;
        int indexedFiles = 0;

        for (String fileContent : fileContents) {
            if (fileContent == null || fileContent.isBlank()) {
                continue;
            }
            ParsedFile parsed = parseFile(index, fileContent);
            if (parsed != null && parsed.fileIndex() != null) {
                FileIndex fileIndex = parsed.fileIndex();
                fileIndexRepository.save(fileIndex);
                totalFunctions += fileIndex.getFunctionCount();
                totalClasses += fileIndex.getClassCount();
                indexedFiles++;
                if (parsed.javaAst() != null) {
                    persistJavaGraph(index, fileIndex, parsed.javaAst());
                }
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
    }

    /**
     * Marks an index as FAILED — called by {@link com.devbraid.config.AsyncConfig}
     * when startIndexing throws. Pass indexId as the first method argument.
     */
    @Transactional
    public void markIndexFailed(UUID indexId, String errorMessage) {
        codebaseIndexRepository.findById(indexId).ifPresent(index -> {
            index.setStatus("FAILED");
            index.setErrorMessage(errorMessage);
            codebaseIndexRepository.save(index);
            log.error("Indexing failed for {}/{}: {}", index.getRepository(), index.getBranch(), errorMessage);
        });
    }

    private ParsedFile parseFile(CodebaseIndex codebaseIndex, String fileContent) {
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

        CompilationUnit javaAst = null;
        switch (language) {
            case "java" -> javaAst = parseJavaFile(fileIndex, fileContent);
            case "typescript", "javascript", "tsx", "jsx" -> parseTsFile(fileIndex, fileContent);
            case "python" -> parsePythonFile(fileIndex, fileContent);
            case "go" -> parseGoFile(fileIndex, fileContent);
            default -> { /* skip non-indexable languages */ }
        }

        fileIndex.setRiskSignals(detectRiskSignals(fileContent, language));
        return new ParsedFile(fileIndex, javaAst);
    }

    private CompilationUnit parseJavaFile(FileIndex fileIndex, String content) {
        // ponytail: no try/catch — JavaParser failures propagate to GlobalExceptionHandler.
        // Strip the leading file-path line (indexing convention: first line is the path)
        int firstNewline = content.indexOf('\n');
        String source = firstNewline > 0 ? content.substring(firstNewline + 1) : content;
        CompilationUnit cu = StaticJavaParser.parse(source);

        List<String> classes = new ArrayList<>();
        List<String> methods = new ArrayList<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                super.visit(n, arg);
                classes.add(n.getNameAsString());
            }

            @Override
            public void visit(EnumDeclaration n, Void arg) {
                super.visit(n, arg);
                classes.add(n.getNameAsString());
            }

            @Override
            public void visit(MethodDeclaration n, Void arg) {
                super.visit(n, arg);
                methods.add(n.getNameAsString() + ":" + n.getTypeAsString());
            }
        }, null);

        List<String> imports = new ArrayList<>();
        cu.getImports().forEach(imp -> imports.add(imp.getNameAsString()));

        fileIndex.setClasses(String.join(",", classes));
        fileIndex.setClassCount(classes.size());
        fileIndex.setFunctions(String.join(",", methods));
        fileIndex.setFunctionCount(methods.size());
        fileIndex.setImports(String.join(",", imports));
        return cu;
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
     * Build and persist the code graph (nodes + edges) for a Java file from its parsed AST.
     * Creates FILE, CLASS/INTERFACE, METHOD nodes with CONTAINS edges (structural, per-file).
     * Method qualified names include the begin line so overloads don't collide on the
     * UNIQUE constraint. ponytail: cross-file IMPORTS linking deferred — would need a
     * symbol solver; a file never imports its own types, so an in-file IMPORTS pass is dead code.
     */
    private void persistJavaGraph(CodebaseIndex index, FileIndex fileIndex, CompilationUnit cu) {
        List<CodebaseNode> nodes = new ArrayList<>();
        List<CodebaseEdge> edges = new ArrayList<>();

        CodebaseNode fileNode = CodebaseNode.builder()
                .codebaseIndex(index)
                .fileIndex(fileIndex)
                .nodeType("FILE")
                .qualifiedName(fileIndex.getFilePath())
                .filePath(fileIndex.getFilePath())
                .startLine(1)
                .endLine(fileIndex.getLineCount())
                .build();
        nodes.add(fileNode);

        // ponytail: enums are indexed as classes in FileIndex but not graphed — structural symmetry
        // isn't worth the extra visitor; add an EnumDeclaration visit here if graph parity is ever needed.
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                super.visit(n, arg);
                String name = n.getNameAsString();
                CodebaseNode node = CodebaseNode.builder()
                        .codebaseIndex(index)
                        .fileIndex(fileIndex)
                        .nodeType(n.isInterface() ? "INTERFACE" : "CLASS")
                        .qualifiedName(fileIndex.getFilePath() + "#" + name)
                        .filePath(fileIndex.getFilePath())
                        .startLine(n.getBegin().map(p -> p.line).orElse(0))
                        .endLine(n.getEnd().map(p -> p.line).orElse(0))
                        .build();
                nodes.add(node);
                edges.add(edge(index, fileNode, node, "CONTAINS"));
            }

            @Override
            public void visit(MethodDeclaration n, Void arg) {
                super.visit(n, arg);
                String name = n.getNameAsString();
                int line = n.getBegin().map(p -> p.line).orElse(0);
                CodebaseNode node = CodebaseNode.builder()
                        .codebaseIndex(index)
                        .fileIndex(fileIndex)
                        .nodeType("METHOD")
                        .qualifiedName(fileIndex.getFilePath() + "#" + name + ":" + line)
                        .filePath(fileIndex.getFilePath())
                        .startLine(line)
                        .endLine(n.getEnd().map(p -> p.line).orElse(0))
                        .build();
                nodes.add(node);
                edges.add(edge(index, fileNode, node, "CONTAINS"));
            }
        }, null);

        codebaseNodeRepository.saveAll(nodes);
        codebaseEdgeRepository.saveAll(edges);
        index.setTotalDependencies(index.getTotalDependencies() + edges.size());
    }

    private CodebaseEdge edge(CodebaseIndex index, CodebaseNode source, CodebaseNode target, String type) {
        return CodebaseEdge.builder()
                .codebaseIndex(index)
                .sourceNode(source)
                .targetNode(target)
                .edgeType(type)
                .build();
    }

    /**
     * Query the code graph: all nodes for an index, or transitive reachability from a node via recursive CTE.
     * ponytail: single CTE query returns depth-limited reachable set; full graph if no nodeId given.
     * Note: edges are structural FILE->X, so traversal is rooted at a FILE node;
     * a CLASS/METHOD nodeId yields an empty reachable set by design.
     */
    @Transactional(readOnly = true)
    public DependencyGraphResponse getDependencyGraph(UUID indexId, UUID nodeId, int depth) {
        List<CodebaseNode> nodes;
        if (nodeId == null) {
            nodes = codebaseNodeRepository.findByCodebaseIndexIdOrderByQualifiedName(indexId);
        } else {
            nodes = new ArrayList<>();
            for (Object[] row : codebaseEdgeRepository.findReachableNodes(indexId, nodeId, depth)) {
                nodes.add(CodebaseNode.builder()
                        .id((UUID) row[0])
                        .nodeType((String) row[1])
                        .qualifiedName((String) row[2])
                        .filePath((String) row[3])
                        .startLine(((Number) row[4]).intValue())
                        .endLine(((Number) row[5]).intValue())
                        .build());
            }
            // Include the queried root node itself so its outgoing edges survive the filter.
            codebaseNodeRepository.findById(nodeId).ifPresent(root -> nodes.add(0, root));
        }
        // Return only edges whose endpoints are in the node set — keeps the response consistent
        // for both the full-graph and the reachable-subset cases.
        Set<UUID> nodeIds = nodes.stream().map(CodebaseNode::getId).collect(Collectors.toSet());
        List<CodebaseEdge> edges = codebaseEdgeRepository.findByCodebaseIndexId(indexId).stream()
                .filter(e -> nodeIds.contains(e.getSourceNode().getId())
                        && nodeIds.contains(e.getTargetNode().getId()))
                .toList();
        return new DependencyGraphResponse(nodes, edges);
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

    // ── Graph response DTO ───────────────────────────────────────────

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

    @Transactional(readOnly = true)
    public Optional<CodebaseIndex> getIndex(UUID indexId) {
        return codebaseIndexRepository.findById(indexId);
    }

    @Transactional(readOnly = true)
    public List<CodebaseIndex> listIndexesByUser(User user) {
        return codebaseIndexRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    // ── Query methods ───────────────────────────────────────────────

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

    private record ParsedFile(FileIndex fileIndex, CompilationUnit javaAst) {
    }

    public record DependencyGraphResponse(List<CodebaseNode> nodes, List<CodebaseEdge> edges) {
    }
}
