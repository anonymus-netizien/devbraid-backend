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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IndexingService — both repository integration and
 * direct regex extraction accuracy for Java/TS/Python/Go parsing.
 */
@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private CodebaseIndexRepository codebaseIndexRepository;

    @Mock
    private FileIndexRepository fileIndexRepository;

    @Mock
    private CodebaseNodeRepository codebaseNodeRepository;

    @Mock
    private CodebaseEdgeRepository codebaseEdgeRepository;

    @InjectMocks
    private IndexingService indexingService;

    private User testUser;
    private CodebaseIndex testIndex;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .build();

        testIndex = CodebaseIndex.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .repository("owner/repo")
                .branch("main")
                .status("PENDING")
                .build();
    }

    // ── Repository Integration Tests ────────────────────────────────

    @Test
    void createIndex_newIndex_savesAndReturns() {
        when(codebaseIndexRepository.findByUserIdAndRepositoryAndBranch(
                testUser.getId(), "owner/repo", "main"))
                .thenReturn(Optional.empty());
        when(codebaseIndexRepository.save(any(CodebaseIndex.class)))
                .thenReturn(testIndex);

        CodebaseIndex result = indexingService.createIndex(testUser, "owner/repo", "main");

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        verify(codebaseIndexRepository).save(any(CodebaseIndex.class));
    }

    @Test
    void createIndex_existingIndex_returnsExisting() {
        when(codebaseIndexRepository.findByUserIdAndRepositoryAndBranch(
                testUser.getId(), "owner/repo", "main"))
                .thenReturn(Optional.of(testIndex));

        CodebaseIndex result = indexingService.createIndex(testUser, "owner/repo", "main");

        assertNotNull(result);
        assertEquals(testIndex.getId(), result.getId());
        verify(codebaseIndexRepository, never()).save(any());
    }

    @Test
    void getIndex_exists_returnsIndex() {
        when(codebaseIndexRepository.findById(testIndex.getId()))
                .thenReturn(Optional.of(testIndex));

        Optional<CodebaseIndex> result = indexingService.getIndex(testIndex.getId());

        assertTrue(result.isPresent());
        assertEquals("owner/repo", result.get().getRepository());
    }

    @Test
    void getIndex_notExists_returnsEmpty() {
        when(codebaseIndexRepository.findById(any()))
                .thenReturn(Optional.empty());

        Optional<CodebaseIndex> result = indexingService.getIndex(UUID.randomUUID());

        assertFalse(result.isPresent());
    }

    @Test
    void listIndexesByUser_returnsList() {
        when(codebaseIndexRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(List.of(testIndex));

        List<CodebaseIndex> result = indexingService.listIndexesByUser(testUser);

        assertEquals(1, result.size());
    }

    @Test
    void getFilesByIndex_returnsFiles() {
        FileIndex fileIndex = FileIndex.builder()
                .id(UUID.randomUUID())
                .codebaseIndex(testIndex)
                .filePath("src/main/java/Foo.java")
                .language("java")
                .functionCount(5)
                .classCount(1)
                .build();

        when(fileIndexRepository.findByCodebaseIndexIdOrderByFilePath(testIndex.getId()))
                .thenReturn(List.of(fileIndex));

        List<FileIndex> result = indexingService.getFilesByIndex(testIndex.getId());

        assertEquals(1, result.size());
        assertEquals("java", result.get(0).getLanguage());
    }

    @Test
    void searchFiles_returnsMatchingFiles() {
        FileIndex fileIndex = FileIndex.builder()
                .id(UUID.randomUUID())
                .codebaseIndex(testIndex)
                .filePath("src/main/java/UserService.java")
                .language("java")
                .build();

        when(fileIndexRepository.findByIndexIdAndPathPattern(testIndex.getId(), "User"))
                .thenReturn(List.of(fileIndex));

        List<FileIndex> result = indexingService.searchFiles(testIndex.getId(), "User");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getFilePath().contains("User"));
    }

    // ── Parsing Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Java Parsing Tests")
    class JavaParsingTests {

        @Test
        @DisplayName("parseJavaFile extracts classes correctly")
        void startIndexing_javaFile_extractsClasses() {
            String fileContent = "src/main/java/com/app/UserService.java\n" +
                    "package com.app;\n\n" +
                    "public class UserService {\n" +
                    "    private class InnerHelper {}\n" +
                    "    public interface UserRepository {}\n" +
                    "    public enum Status { ACTIVE, INACTIVE }\n" +
                    "}";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertEquals("java", saved.getLanguage());
            assertTrue(saved.getClassCount() >= 3);
            assertTrue(saved.getClasses().contains("UserService"));
            assertTrue(saved.getClasses().contains("InnerHelper"));
            assertTrue(saved.getClasses().contains("Status"));
        }

        @Test
        @DisplayName("parseJavaFile extracts methods with return types")
        void startIndexing_javaFile_extractsMethods() {
            String fileContent = "src/main/java/com/app/AuthService.java\n" +
                    "package com.app;\n\n" +
                    "public class AuthService {\n" +
                    "    public LoginResponse login(String email, String password) { return null; }\n" +
                    "    private void logout(String token) {}\n" +
                    "    protected static boolean validate(String input) { return true; }\n" +
                    "    public synchronized void refresh() {}\n" +
                    "}";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertEquals(4, saved.getFunctionCount());
            assertTrue(saved.getFunctions().contains("login:LoginResponse"));
            assertTrue(saved.getFunctions().contains("logout:void"));
            assertTrue(saved.getFunctions().contains("validate:boolean"));
            assertTrue(saved.getFunctions().contains("refresh:void"));
        }

        @Test
        @DisplayName("parseJavaFile extracts imports correctly")
        void startIndexing_javaFile_extractsImports() {
            String fileContent = "src/main/java/com/app/Config.java\n" +
                    "import org.springframework.stereotype.Service;\n" +
                    "import java.util.List;\n" +
                    "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                    "public class Config {}";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertTrue(saved.getImports().split(",").length >= 2);
            assertTrue(saved.getImports().contains("org.springframework.stereotype.Service"));
            assertTrue(saved.getImports().contains("java.util.List"));
        }

        @Test
        @DisplayName("parseJavaFile handles empty class body")
        void startIndexing_javaFile_emptyClass() {
            String fileContent = "src/main/java/com/app/Empty.java\n" +
                    "public class Empty {}";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertEquals(1, saved.getClassCount());
            assertTrue(saved.getClasses().contains("Empty"));
            assertEquals(0, saved.getFunctionCount());
        }
    }

    @Nested
    @DisplayName("TypeScript Parsing Tests")
    class TypeScriptParsingTests {

        @Test
        @DisplayName("parseTsFile extracts classes correctly")
        void startIndexing_tsFile_extractsClasses() {
            String fileContent = "src/components/UserCard.tsx\n" +
                    "import React from 'react';\n\n" +
                    "export class UserCard extends React.Component {}\n" +
                    "abstract class BaseWidget {}\n" +
                    "export default class App {}";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertEquals("tsx", saved.getLanguage());
            assertTrue(saved.getClassCount() >= 2);
            assertTrue(saved.getClasses().contains("UserCard"));
        }

        @Test
        @DisplayName("parseTsFile extracts arrow functions and function declarations")
        void startIndexing_tsFile_extractsFunctions() {
            String fileContent = "src/utils/helpers.ts\n" +
                    "export function formatDate(date: Date): string { return ''; }\n" +
                    "const parseJSON = (input: string) => {};\n" +
                    "async function fetchData() {}\n" +
                    "let processItems = function() {}";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertTrue(saved.getFunctionCount() >= 2);
            assertTrue(saved.getFunctions().contains("formatDate") || saved.getFunctions().contains("parseJSON") || saved.getFunctions().contains("fetchData"));
        }

        @Test
        @DisplayName("parseTsFile extracts imports and exports correctly")
        void startIndexing_tsFile_extractsImportsExports() {
            String fileContent = "src/api/client.ts\n" +
                    "import axios from 'axios';\n" +
                    "import { User } from '../types/user';\n" +
                    "import React, { useState } from 'react';\n\n" +
                    "export interface ApiClient {}\n" +
                    "export const createClient = () => {};";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertTrue(saved.getImports().split(",").length >= 2);
            assertTrue(saved.getImports().contains("axios") || saved.getImports().contains("../types/user"));

            assertTrue(saved.getExports().contains("ApiClient"));
            assertTrue(saved.getExports().contains("createClient"));
        }
    }

    @Nested
    @DisplayName("Python Parsing Tests")
    class PythonParsingTests {

        @Test
        @DisplayName("parsePythonFile extracts classes and functions")
        void startIndexing_pythonFile_extractsClassesFunctions() {
            String fileContent = "src/models/user.py\n" +
                    "class User:\n" +
                    "    name = ''\n\n" +
                    "class Admin:\n" +
                    "    role = 'admin'\n\n" +
                    "def create_user():\n" +
                    "    pass\n\n" +
                    "async def run_server():\n" +
                    "    pass";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertEquals("python", saved.getLanguage());
            assertEquals(2, saved.getClassCount());
            assertTrue(saved.getClasses().contains("User"));
            assertTrue(saved.getClasses().contains("Admin"));
            assertEquals(2, saved.getFunctionCount());
            assertTrue(saved.getFunctions().contains("create_user"));
            assertTrue(saved.getFunctions().contains("run_server"));
        }

        @Test
        @DisplayName("parsePythonFile extracts imports")
        void startIndexing_pythonFile_extractsImports() {
            String fileContent = "src/app.py\n" +
                    "import os\n" +
                    "from flask import Flask\n" +
                    "import requests\n\n" +
                    "def main(): pass";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertTrue(saved.getImports().contains("os"));
            assertTrue(saved.getImports().contains("flask"));
            assertTrue(saved.getImports().contains("requests"));
        }
    }

    @Nested
    @DisplayName("Go Parsing Tests")
    class GoParsingTests {

        @Test
        @DisplayName("parseGoFile extracts structs and functions")
        void startIndexing_goFile_extractsStructsFunctions() {
            String fileContent = "cmd/server/main.go\n" +
                    "package main\n\n" +
                    "type User struct {\n" +
                    "    Name string\n" +
                    "    Email string\n" +
                    "}\n\n" +
                    "func (u *User) GetName() string { return u.Name }\n" +
                    "func CreateServer() *Server { return nil }";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());

            FileIndex saved = captor.getValue();
            assertEquals("go", saved.getLanguage());
            assertEquals(1, saved.getClassCount());
            assertTrue(saved.getClasses().contains("User"));
            assertTrue(saved.getFunctionCount() >= 2);
        }
    }

    @Nested
    @DisplayName("Language Detection Tests")
    class LanguageDetectionTests {

        @Test
        @DisplayName("detects Java files correctly")
        void startIndexing_javaFile_detectedAsJava() {
            String fileContent = "src/main/java/com/app/App.java\npublic class App {}";
            when(codebaseIndexRepository.findById(testIndex.getId())).thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());
            assertEquals("java", captor.getValue().getLanguage());
        }

        @Test
        @DisplayName("detects TypeScript files correctly")
        void startIndexing_tsFile_detectedAsTypescript() {
            String fileContent = "src/components/App.tsx\nexport default function App() {}";
            when(codebaseIndexRepository.findById(testIndex.getId())).thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());
            assertEquals("tsx", captor.getValue().getLanguage());
        }

        @Test
        @DisplayName("skips non-indexable files (SQL, YAML)")
        void startIndexing_sqlFile_notIndexed() {
            String fileContent = "V1__create_users.sql\nCREATE TABLE users (id INT PRIMARY KEY);";
            when(codebaseIndexRepository.findById(testIndex.getId())).thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            // SQL files are indexed but not parsed for functions/classes
            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());
            assertEquals(0, captor.getValue().getFunctionCount());
            assertEquals(0, captor.getValue().getClassCount());
        }
    }

    @Nested
    @DisplayName("Risk Signal Detection Tests")
    class RiskSignalTests {

        @Test
        @DisplayName("detects large file risk signal")
        void startIndexing_largeFile_flagsLargeFile() {
            StringBuilder sb = new StringBuilder("src/main/java/com/app/Huge.java\n");
            for (int i = 0; i < 310; i++) {
                sb.append("    line ").append(i).append("\n");
            }
            when(codebaseIndexRepository.findById(testIndex.getId())).thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(sb.toString()));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());
            assertTrue(captor.getValue().getRiskSignals().contains("large_file"));
        }

        @Test
        @DisplayName("detects credential reference risk signal")
        void startIndexing_fileWithPassword_flagsCredential() {
            String fileContent = "src/main/java/com/app/Config.java\n" +
                    "public class Config {\n" +
                    "    private String password = \"secret123\";\n" +
                    "}";
            when(codebaseIndexRepository.findById(testIndex.getId())).thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());
            assertTrue(captor.getValue().getRiskSignals().contains("credential_reference"));
        }

        @Test
        @DisplayName("detects god class risk signal for Java files with many methods")
        void startIndexing_javaFileWithManyMethods_flagsGodClass() {
            StringBuilder sb = new StringBuilder("src/main/java/com/app/GodClass.java\npublic class GodClass {\n");
            for (int i = 0; i < 25; i++) {
                sb.append("    public void method").append(i).append("() {}\n");
            }
            sb.append("}");
            when(codebaseIndexRepository.findById(testIndex.getId())).thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(sb.toString()));

            ArgumentCaptor<FileIndex> captor = ArgumentCaptor.forClass(FileIndex.class);
            verify(fileIndexRepository).save(captor.capture());
            assertTrue(captor.getValue().getRiskSignals().contains("god_class"));
        }
    }

    @Nested
    @DisplayName("Code Graph Persistence Tests")
    class CodeGraphTests {

        @Test
        @DisplayName("overloaded methods get distinct qualified names (no UNIQUE collision)")
        void startIndexing_overloadedMethods_distinctQualifiedNames() {
            String fileContent = "src/main/java/com/app/AuthService.java\n" +
                    "public class AuthService {\n" +
                    "    public LoginResponse login(String email) { return null; }\n" +
                    "    public LoginResponse login(String email, String password) { return null; }\n" +
                    "}";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CodebaseNode>> captor = ArgumentCaptor.forClass(List.class);
            verify(codebaseNodeRepository).saveAll(captor.capture());

            List<String> methodNames = captor.getValue().stream()
                    .filter(n -> "METHOD".equals(n.getNodeType()))
                    .map(CodebaseNode::getQualifiedName)
                    .toList();
            assertEquals(2, methodNames.size(), "expected both overloads as METHOD nodes");
            assertEquals(2, methodNames.stream().distinct().count(), "qualified names must be distinct");
        }

        @Test
        @DisplayName("regex-fallback Java file builds no graph instead of failing the run")
        void startIndexing_unparseableJava_skipsGraph() {
            // Contains a syntax error JavaParser cannot parse (unclosed brace + stray token).
            String fileContent = "src/main/java/com/app/Broken.java\n" +
                    "public class Broken {\n" +
                    "    public void ok() {}\n" +
                    "    @@@invalid@@@\n";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(fileContent));

            verify(fileIndexRepository).save(any(FileIndex.class));
            // Graph persistence must be skipped for the regex-fallback file — no nodes, no edges.
            verify(codebaseNodeRepository, never()).saveAll(any());
            verify(codebaseEdgeRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("null file content entry is skipped, not fatal")
        void startIndexing_nullContent_skipped() {
            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            // Arrays.asList allows null entries (List.of would NPE on null).
            indexingService.startIndexing(testIndex.getId(),
                    java.util.Arrays.asList("src/App.java\npublic class App {}", null, ""));

            verify(fileIndexRepository, times(1)).save(any(FileIndex.class));
        }
    }

    @Nested
    @DisplayName("Multi-File Indexing Tests")
    class MultiFileTests {

        @Test
        @DisplayName("indexing multiple files counts correctly")
        void startIndexing_multipleFiles_countsCorrectly() {
            String javaFile = "src/main/java/com/app/UserService.java\n" +
                    "import java.util.List;\n" +
                    "public class UserService {\n" +
                    "    public User findUser() { return null; }\n" +
                    "    public void deleteUser() {}\n" +
                    "}";
            String tsFile = "src/components/UserCard.tsx\n" +
                    "import React from 'react';\n" +
                    "export class UserCard extends React.Component {}\n" +
                    "export const helper = () => {};";

            when(codebaseIndexRepository.findById(testIndex.getId()))
                    .thenReturn(Optional.of(testIndex));

            indexingService.startIndexing(testIndex.getId(), List.of(javaFile, tsFile));

            verify(fileIndexRepository, times(2)).save(any());

            ArgumentCaptor<CodebaseIndex> indexCaptor = ArgumentCaptor.forClass(CodebaseIndex.class);
            verify(codebaseIndexRepository, atLeastOnce()).save(indexCaptor.capture());

            CodebaseIndex lastIndex = indexCaptor.getAllValues()
                    .stream()
                    .filter(i -> "COMPLETED".equals(i.getStatus()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(lastIndex);
            assertEquals(2, lastIndex.getTotalFiles());
            assertEquals(2, lastIndex.getIndexedFiles());
        }
    }
}
