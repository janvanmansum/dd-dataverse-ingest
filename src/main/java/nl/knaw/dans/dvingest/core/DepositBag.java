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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import nl.knaw.dans.lib.dataverse.MetadataFieldDeserializer;
import nl.knaw.dans.lib.dataverse.model.dataset.Dataset;
import nl.knaw.dans.lib.dataverse.model.dataset.MetadataField;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class DepositBag implements Comparable<DepositBag> {
    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper(new YAMLFactory());
        SimpleModule module = new SimpleModule();
        module.addDeserializer(MetadataField.class, new MetadataFieldDeserializer());
        MAPPER.registerModule(module);
    }

    private final Path bagDir;

    public DepositBag(Path bagDir) {
        this.bagDir = bagDir;
        // Minimal check to see if it is a bag
        if (!Files.exists(bagDir.resolve("bagit.txt"))) {
            throw new IllegalStateException("Not a bag: " + bagDir);
        }
    }

    public Dataset getDatasetMetadata() throws IOException {
        var dataset = MAPPER.readValue(FileUtils.readFileToString(bagDir.resolve("dataset.yml").toFile(), StandardCharsets.UTF_8), Dataset.class);
        dataset.getDatasetVersion().setFiles(Collections.emptyList()); // files = null or a list of files is not allowed
        return dataset;
    }

    public Edit getEditInstructions() throws IOException {
        if (!Files.exists(bagDir.resolve("edit.yml"))) {
            return null;
        }
        var editInstructions = MAPPER.readValue(FileUtils.readFileToString(bagDir.resolve("edit.yml").toFile(), StandardCharsets.UTF_8), EditInstructions.class);
        return editInstructions.getEdit();
    }

    @Override
    public int compareTo(DepositBag depositBag) {
        return bagDir.getFileName().toString().compareTo(depositBag.bagDir.getFileName().toString());
    }

    public Path getDataDir() {
        return bagDir.resolve("data");
    }

    public String toString() {
        return bagDir.getFileName().toString();
    }
}