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
package nl.knaw.dans.dvingest.core.service;

import lombok.Builder;
import nl.knaw.dans.lib.util.PathIteratorZipper;
import nl.knaw.dans.lib.util.PathIteratorZipper.PathIteratorZipperBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Builder
public class UtilityServicesImpl implements UtilityServices {
    private final Path tempDir;
    private final int maxNumberOfFilesPerUpload;
    private final long maxUploadSize;

    @Override
    public Path createTempZipFile() throws IOException {
        if (tempDir == null) {
            return Files.createTempFile("dvingest", ".zip");
        }
        else {
            return Files.createTempFile(tempDir, "dvingest", ".zip");
        }
    }

    @Override
    public PathIteratorZipperBuilder createPathIteratorZipperBuilder() {
        return createPathIteratorZipperBuilder(Map.of());
    }

    @Override
    public PathIteratorZipperBuilder createPathIteratorZipperBuilder(Map<String, String> renameMap) {
        return PathIteratorZipper.builder()
            .renameMap(renameMap)
            .maxNumberOfFiles(maxNumberOfFilesPerUpload)
            .maxNumberOfBytes(maxUploadSize);
    }
}
