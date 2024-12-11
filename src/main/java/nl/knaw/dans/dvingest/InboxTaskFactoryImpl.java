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
package nl.knaw.dans.dvingest;

import lombok.AllArgsConstructor;
import nl.knaw.dans.dvingest.core.DataverseIngestDepositFactory;
import nl.knaw.dans.dvingest.core.DepositTaskFactory;
import nl.knaw.dans.lib.util.inbox.InboxTaskFactory;

import java.nio.file.Path;

@AllArgsConstructor
public class InboxTaskFactoryImpl implements InboxTaskFactory {
    private final DataverseIngestDepositFactory dataverseIngestDepositFactory;
    private final DepositTaskFactory depositTaskFactory;
    private final Path outputDir;

    @Override
    public Runnable createInboxTask(Path path) {
        var dataVerseIngestDeposit = dataverseIngestDepositFactory.createDataverseIngestDeposit(path);
        return depositTaskFactory.createDepositTask(dataVerseIngestDeposit, outputDir, false);
    }
}