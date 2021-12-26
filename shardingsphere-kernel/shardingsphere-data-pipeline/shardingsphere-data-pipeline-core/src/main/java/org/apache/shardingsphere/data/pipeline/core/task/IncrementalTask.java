/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.data.pipeline.core.task;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.data.pipeline.api.config.ingest.DumperConfiguration;
import org.apache.shardingsphere.data.pipeline.api.config.rulealtered.ImporterConfiguration;
import org.apache.shardingsphere.data.pipeline.api.executor.AbstractLifecycleExecutor;
import org.apache.shardingsphere.data.pipeline.api.ingest.position.PlaceholderPosition;
import org.apache.shardingsphere.data.pipeline.api.ingest.record.Record;
import org.apache.shardingsphere.data.pipeline.api.task.progress.IncrementalTaskProgress;
import org.apache.shardingsphere.data.pipeline.core.datasource.PipelineDataSourceManager;
import org.apache.shardingsphere.data.pipeline.core.exception.PipelineJobExecutionException;
import org.apache.shardingsphere.data.pipeline.core.execute.ExecuteCallback;
import org.apache.shardingsphere.data.pipeline.core.execute.ExecuteEngine;
import org.apache.shardingsphere.data.pipeline.core.ingest.channel.distribution.DistributionChannel;
import org.apache.shardingsphere.data.pipeline.spi.importer.Importer;
import org.apache.shardingsphere.data.pipeline.spi.importer.ImporterListener;
import org.apache.shardingsphere.data.pipeline.spi.ingest.dumper.Dumper;
import org.apache.shardingsphere.scaling.core.job.dumper.DumperFactory;
import org.apache.shardingsphere.scaling.core.job.importer.ImporterFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Incremental task.
 */
@Slf4j
@ToString(exclude = {"dataSourceManager", "dumper", "progress"})
public final class IncrementalTask extends AbstractLifecycleExecutor implements PipelineTask {
    
    @Getter
    private final String taskId;
    
    private final int concurrency;
    
    private final DumperConfiguration dumperConfig;
    
    private final ImporterConfiguration importerConfig;
    
    private final ExecuteEngine incrementalDumperExecuteEngine;
    
    private final PipelineDataSourceManager dataSourceManager;
    
    private Dumper dumper;
    
    @Getter
    private final IncrementalTaskProgress progress;
    
    public IncrementalTask(final int concurrency, final DumperConfiguration dumperConfig, final ImporterConfiguration importerConfig, final ExecuteEngine incrementalDumperExecuteEngine) {
        this.concurrency = concurrency;
        this.dumperConfig = dumperConfig;
        this.importerConfig = importerConfig;
        this.incrementalDumperExecuteEngine = incrementalDumperExecuteEngine;
        dataSourceManager = new PipelineDataSourceManager();
        taskId = dumperConfig.getDataSourceName();
        progress = new IncrementalTaskProgress();
        progress.setPosition(dumperConfig.getPosition());
    }
    
    @Override
    public void start() {
        progress.getIncrementalTaskDelay().setLatestActiveTimeMillis(System.currentTimeMillis());
        dumper = DumperFactory.newInstanceLogDumper(dumperConfig, progress.getPosition());
        Collection<Importer> importers = instanceImporters();
        instanceChannel(importers);
        Future<?> future = incrementalDumperExecuteEngine.submitAll(importers, getExecuteCallback());
        dumper.start();
        waitForResult(future);
        dataSourceManager.close();
    }
    
    private List<Importer> instanceImporters() {
        List<Importer> result = new ArrayList<>(concurrency);
        for (int i = 0; i < concurrency; i++) {
            result.add(ImporterFactory.newInstance(importerConfig, dataSourceManager));
        }
        return result;
    }
    
    private void instanceChannel(final Collection<Importer> importers) {
        DistributionChannel channel = new DistributionChannel(importers.size(), dumperConfig.getBlockQueueSize(), records -> {
            Record lastHandledRecord = records.get(records.size() - 1);
            if (!(lastHandledRecord.getPosition() instanceof PlaceholderPosition)) {
                progress.setPosition(lastHandledRecord.getPosition());
                progress.getIncrementalTaskDelay().setLastEventTimestamps(lastHandledRecord.getCommitTime());
            }
        });
        dumper.setChannel(channel);
        ImporterListener importerListener = records -> progress.getIncrementalTaskDelay().setLatestActiveTimeMillis(System.currentTimeMillis());
        for (Importer each : importers) {
            each.setChannel(channel);
            each.setImporterListener(importerListener);
        }
    }
    
    private ExecuteCallback getExecuteCallback() {
        return new ExecuteCallback() {
            
            @Override
            public void onSuccess() {
            }
            
            @Override
            public void onFailure(final Throwable throwable) {
                log.error("get an error when migrating the increment data", throwable);
                dumper.stop();
            }
        };
    }
    
    private void waitForResult(final Future<?> future) {
        try {
            future.get();
        } catch (final InterruptedException ignored) {
        } catch (final ExecutionException ex) {
            throw new PipelineJobExecutionException(String.format("Task %s execute failed ", taskId), ex.getCause());
        }
    }
    
    @Override
    public void stop() {
        if (null != dumper) {
            dumper.stop();
            dumper = null;
        }
    }
}