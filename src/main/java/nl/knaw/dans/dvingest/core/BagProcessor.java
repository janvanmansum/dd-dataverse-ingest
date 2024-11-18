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

import lombok.extern.slf4j.Slf4j;
import nl.knaw.dans.dvingest.core.service.DataverseService;
import nl.knaw.dans.dvingest.core.service.PathIterator;
import nl.knaw.dans.dvingest.core.service.UtilityServices;
import nl.knaw.dans.dvingest.core.yaml.Edit;
import nl.knaw.dans.dvingest.core.yaml.FilesInstructions;
import nl.knaw.dans.dvingest.core.yaml.UpdateState;
import nl.knaw.dans.lib.dataverse.DataverseException;
import nl.knaw.dans.lib.dataverse.model.dataset.Dataset;
import nl.knaw.dans.lib.dataverse.model.dataset.UpdateType;
import nl.knaw.dans.lib.dataverse.model.file.FileMeta;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class BagProcessor {
    private final UUID depositId;
    private final Path dataDir;
    private final DataverseService dataverseService;
    private final UtilityServices utilityServices;
    private final Dataset dataset;
    private final Edit edit;
    private final FilesInstructions filesInstructions;
    private final UpdateState updateState;

    // Only retrieve the file list once, because Dataverse is slow in building it up for large numbers of files.
    private final Map<String, FileMeta> filesInDataset = new HashMap<>();
    private boolean filesRetrieved = false;

    private String pid;

    public BagProcessor(UUID depositId, DepositBag bag, DataverseService dataverseService, UtilityServices utilityServices) throws IOException {
        this.depositId = depositId;
        this.dataDir = bag.getDataDir();
        this.dataverseService = dataverseService;
        this.utilityServices = utilityServices;

        this.dataset = bag.getDatasetMetadata();
        this.edit = bag.getEditInstructions();
        this.filesInstructions = bag.getFilesInstructions();
        this.updateState = bag.getUpdateState();
    }

    public String run(String targetPid) throws IOException, DataverseException {
        // 1. Create new dataset or update existing dataset
        if (targetPid == null) {
            pid = createNewDataset();
        }
        else {
            pid = targetPid;
            updateDatasetMetadata();
        }
        // 2. Process edit instructions
        processEdit();
        // 3. Add files
        addUnrestrictedFiles();
        // 4. Update file metadata
        updateFileMetadata();
        // 5. Role assignments
        // TODO: Implement role assignments
        // 6. Update state
        processUpdateState();
        return pid;
    }

    private String createNewDataset() throws IOException, DataverseException {
        log.debug("Creating new dataset");
        return dataverseService.createDataset(dataset);
    }

    private void updateDatasetMetadata() throws IOException, DataverseException {
        log.debug("Start updating dataset metadata for deposit {}", depositId);
        dataverseService.updateMetadata(pid, dataset.getDatasetVersion());
        log.debug("End updating dataset metadata for deposit {}", depositId);
    }

    private void processEdit() throws IOException, DataverseException {
        if (edit == null) {
            log.debug("No edit instructions found. Skipping edit processing.");
            return;
        }
        log.debug("Start processing edit instructions for deposit {}", depositId);
        deleteFiles();
        replaceFiles();
        addRestrictedFiles();
    }

    private void deleteFiles() throws IOException, DataverseException {
        log.debug("Start deleting files for deposit {}", depositId);
        for (var file : edit.getDeleteFiles()) {
            log.debug("Deleting file: {}", file);
            var fileToDelete = getFilesInDataset().get(file);
            dataverseService.deleteFile(fileToDelete.getDataFile().getId());
            filesInDataset.remove(file);
        }
        log.debug("End deleting files for deposit {}", depositId);
    }

    private void replaceFiles() throws IOException, DataverseException {
        log.debug("Start replacing files for deposit {}", depositId);
        for (var file : edit.getReplaceFiles()) {
            log.debug("Replacing file: {}", file);
            var fileMeta = getFilesInDataset().get(file);
            dataverseService.replaceFile(pid, fileMeta, dataDir.resolve(file));
        }
        log.debug("End replacing files for deposit {}", depositId);
    }

    private void addRestrictedFiles() throws IOException, DataverseException {
        log.debug("Start adding restricted files for deposit {}", depositId);
        var iterator = new PathIterator(getRestrictedFilesToUpload());
        while (iterator.hasNext()) {
            uploadFileBatch(iterator, true);
        }
        log.debug("End adding restricted files for deposit {}", depositId);
    }

    private void addUnrestrictedFiles() throws IOException, DataverseException {
        log.debug("Start uploading files for deposit {}", depositId);
        var iterator = new PathIterator(getUnrestrictedFilesToUpload());
        while (iterator.hasNext()) {
            uploadFileBatch(iterator, false);
        }
        log.debug("End uploading unrestricted files for deposit {}", depositId);
    }

    private Iterator<File> getUnrestrictedFilesToUpload() {
        return IteratorUtils.filteredIterator(
            FileUtils.iterateFiles(dataDir.toFile(), null, true),
            // Skip files that have been replaced in the edit steps
            path -> edit == null ||
                !edit.getReplaceFiles().contains(dataDir.relativize(path.toPath()).toString())
                    && !edit.getAddRestrictedFiles().contains(dataDir.relativize(path.toPath()).toString()));
    }

    private Iterator<File> getRestrictedFilesToUpload() {
        return IteratorUtils.filteredIterator(
            FileUtils.iterateFiles(dataDir.toFile(), null, true),
            // Skip files that have been replaced in the edit steps
            path -> edit == null ||
                !edit.getReplaceFiles().contains(dataDir.relativize(path.toPath()).toString())
                    && edit.getAddRestrictedFiles().contains(dataDir.relativize(path.toPath()).toString()));
    }

    private void uploadFileBatch(PathIterator iterator, boolean restrict) throws IOException, DataverseException {
        var tempZipFile = utilityServices.createTempZipFile();
        try {
            var zipFile = utilityServices.createPathIteratorZipperBuilder()
                .rootDir(dataDir)
                .sourceIterator(iterator)
                .targetZipFile(tempZipFile)
                .build()
                .zip();
            var fileMeta = new FileMeta();
            fileMeta.setRestricted(restrict);
            var fileLIst = dataverseService.addFile(pid, zipFile, fileMeta);
            log.debug("Uploaded {} files (cumulative)", iterator.getIteratedCount());
            for (var file : fileLIst.getFiles()) {
                filesInDataset.put(getPath(file), file);
            }
        }
        finally {
            Files.deleteIfExists(tempZipFile);
        }
    }

    private Map<String, FileMeta> getFilesInDataset() throws IOException, DataverseException {
        if (!filesRetrieved) {
            log.debug("Start getting files in dataset for deposit {}", depositId);
            var files = dataverseService.getFiles(pid);
            for (var file : files) {
                filesInDataset.put(getPath(file), file);
            }
            filesRetrieved = true;
            log.debug("End getting files in dataset for deposit {}", depositId);
        }
        else {
            log.debug("Files in dataset already retrieved for deposit {}", depositId);
        }
        return filesInDataset;
    }

    private String getPath(FileMeta file) {
        if (file.getDirectoryLabel() != null) {
            return file.getDirectoryLabel() + "/" + file.getLabel();
        }
        return file.getLabel();
    }

    private void updateFileMetadata() throws IOException, DataverseException {
        log.debug("Start updating file metadata for deposit {}", depositId);
        if (filesInstructions == null) {
            log.debug("No file metadata instructions found. Skipping file metadata update.");
            return;
        }
        for (var file : filesInstructions.getFiles()) {
            var id = getFilesInDataset().get(getPath(file)).getDataFile().getId();
            dataverseService.updateFileMetadata(id, file);
        }
        log.debug("End updating file metadata for deposit {}", depositId);
    }

    private void processUpdateState() throws DataverseException, IOException {
        if (updateState == null) {
            log.debug("No update state found. Skipping update state processing.");
            return;
        }
        if ("publish-major".equals(updateState.getAction())) {
            publishVersion(UpdateType.major);
        }
        else if ("publish-minor".equals(updateState.getAction())) {
            publishVersion(UpdateType.minor);
        }
        else if ("submit-for-review".equals(updateState.getAction())) {
            // TODO: Implement submit for review
        }
    }

    private void publishVersion(UpdateType updateType) throws DataverseException, IOException {
        log.debug("Start publishing version for deposit {}", depositId);
        dataverseService.publishDataset(pid, updateType);
        dataverseService.waitForState(pid, "RELEASED");
        log.debug("End publishing version for deposit {}", depositId);
    }
}