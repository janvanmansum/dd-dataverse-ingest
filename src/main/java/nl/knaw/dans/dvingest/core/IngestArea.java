/*
 * Copyright (C) 2024 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.dvingest.core;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.knaw.dans.dvingest.api.ImportCommandDto;
import nl.knaw.dans.dvingest.api.ImportJobStatusDto;
import nl.knaw.dans.lib.dataverse.DataverseClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
@Builder
public class IngestArea {
    @NonNull
    private final ExecutorService executorService;
    @NonNull
    private final DataverseClient dataverseClient;
    @NonNull
    private final Path inbox;
    @NonNull
    private final Path outbox;

    private final Map<String, ImportJob> importJobs = new java.util.concurrent.ConcurrentHashMap<>();

    private IngestArea(ExecutorService executorService, DataverseClient dataverseClient, Path inbox, Path outbox) {
        try {
            this.executorService = executorService;
            this.dataverseClient = dataverseClient;
            this.inbox = inbox.toAbsolutePath().toRealPath();
            this.outbox = outbox.toAbsolutePath().toRealPath();
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to create ingest area", e);
        }
    }

    public void submit(ImportCommandDto importCommand) {
        log.debug("Received import command: {}", importCommand);
        if (importJobs.containsKey(importCommand.getPath())) {
            throw new IllegalArgumentException("Already submitted " + importCommand.getPath());
        }
        validatePath(importCommand.getPath());
        log.debug("Path validation successful");
        var importJob = createImportJob(importCommand);
        log.debug("Created import job: {}", importJob);
        importJobs.put(importCommand.getPath(), importJob);
        log.debug("Submitted import job");
        executorService.submit(importJob);
    }

    public ImportJobStatusDto getStatus(String path) {
        var importJob = importJobs.get(path);
        if (importJob == null) {
            throw new IllegalArgumentException("No job for " + path);
        }
        return importJob.getStatus();
    }

    private ImportJob createImportJob(ImportCommandDto importCommand) {
        Path relativePath;
        if (importCommand.getSingleObject()) {
            relativePath = inbox.relativize(Path.of(importCommand.getPath()).getParent());
        }
        else {
            relativePath = inbox.relativize(Path.of(importCommand.getPath()));
        }
        return new ImportJob(importCommand, outbox.resolve(relativePath).toAbsolutePath(), dataverseClient);
    }

    private void validatePath(String path) {
        var pathObj = Path.of(path);
        checkPathIsAbsolute(pathObj);
        checkPathInInbox(pathObj);
    }

    private void checkPathIsAbsolute(Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Path must be absolute: " + path);
        }
    }

    private void checkPathInInbox(Path path) {
        if (!path.startsWith(inbox)) {
            throw new IllegalArgumentException("Path must be in inbox: " + path);
        }
    }
}