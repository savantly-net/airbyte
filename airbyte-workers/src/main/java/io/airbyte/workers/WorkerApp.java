/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers;

import io.airbyte.config.Configs;
import io.airbyte.config.Configs.WorkerEnvironment;
import io.airbyte.config.EnvConfigs;
import io.airbyte.config.MaxWorkersConfig;
import io.airbyte.config.helpers.LogClientSingleton;
import io.airbyte.config.persistence.ConfigPersistence;
import io.airbyte.config.persistence.ConfigRepository;
import io.airbyte.config.persistence.DatabaseConfigPersistence;
import io.airbyte.config.persistence.split_secrets.SecretPersistence;
import io.airbyte.config.persistence.split_secrets.SecretsHydrator;
import io.airbyte.db.Database;
import io.airbyte.db.instance.configs.ConfigsDatabaseInstance;
import io.airbyte.workers.process.DockerProcessFactory;
import io.airbyte.workers.process.KubeProcessFactory;
import io.airbyte.workers.process.ProcessFactory;
import io.airbyte.workers.process.WorkerHeartbeatServer;
import io.airbyte.workers.temporal.SyncWorkflow;
import io.airbyte.workers.temporal.TemporalJobType;
import io.airbyte.workers.temporal.TemporalUtils;
import io.airbyte.workers.temporal.check.connection.CheckConnectionActivityImpl;
import io.airbyte.workers.temporal.check.connection.CheckConnectionWorkflowImpl;
import io.airbyte.workers.temporal.discover.catalog.DiscoverCatalogActivityImpl;
import io.airbyte.workers.temporal.discover.catalog.DiscoverCatalogWorkflowImpl;
import io.airbyte.workers.temporal.spec.SpecActivityImpl;
import io.airbyte.workers.temporal.spec.SpecWorkflowImpl;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class WorkerApp {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkerApp.class);
  public static final int KUBE_HEARTBEAT_PORT = 9000;

  private final Path workspaceRoot;
  private final ProcessFactory processFactory;
  private final SecretsHydrator secretsHydrator;
  private final WorkflowServiceStubs temporalService;
  private final MaxWorkersConfig maxWorkers;
  private final WorkerEnvironment workerEnvironment;
  private final ConfigRepository configRepository;

  public WorkerApp(final Path workspaceRoot,
                   final ProcessFactory processFactory,
                   final SecretsHydrator secretsHydrator,
                   final WorkflowServiceStubs temporalService,
                   final MaxWorkersConfig maxWorkers,
                   final WorkerEnvironment workerEnvironment,
                   final ConfigRepository configRepository) {
    this.workspaceRoot = workspaceRoot;
    this.processFactory = processFactory;
    this.secretsHydrator = secretsHydrator;
    this.temporalService = temporalService;
    this.maxWorkers = maxWorkers;
    this.workerEnvironment = workerEnvironment;
    this.configRepository = configRepository;
  }

  public void start() {
    final Map<String, String> mdc = MDC.getCopyOfContextMap();
    Executors.newSingleThreadExecutor().submit(
        () -> {
          MDC.setContextMap(mdc);
          try {
            new WorkerHeartbeatServer(KUBE_HEARTBEAT_PORT).start();
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        });

    final WorkerFactory factory = WorkerFactory.newInstance(WorkflowClient.newInstance(temporalService));

    final Worker specWorker = factory.newWorker(TemporalJobType.GET_SPEC.name(), getWorkerOptions(maxWorkers.getMaxSpecWorkers()));
    specWorker.registerWorkflowImplementationTypes(SpecWorkflowImpl.class);
    specWorker.registerActivitiesImplementations(new SpecActivityImpl(processFactory, workspaceRoot));

    final Worker checkConnectionWorker =
        factory.newWorker(TemporalJobType.CHECK_CONNECTION.name(), getWorkerOptions(maxWorkers.getMaxCheckWorkers()));
    checkConnectionWorker.registerWorkflowImplementationTypes(CheckConnectionWorkflowImpl.class);
    checkConnectionWorker
        .registerActivitiesImplementations(new CheckConnectionActivityImpl(processFactory, secretsHydrator, workspaceRoot));

    final Worker discoverWorker = factory.newWorker(TemporalJobType.DISCOVER_SCHEMA.name(), getWorkerOptions(maxWorkers.getMaxDiscoverWorkers()));
    discoverWorker.registerWorkflowImplementationTypes(DiscoverCatalogWorkflowImpl.class);
    discoverWorker
        .registerActivitiesImplementations(new DiscoverCatalogActivityImpl(processFactory, secretsHydrator, workspaceRoot));

    final Worker syncWorker = factory.newWorker(TemporalJobType.SYNC.name(), getWorkerOptions(maxWorkers.getMaxSyncWorkers()));
    syncWorker.registerWorkflowImplementationTypes(SyncWorkflow.WorkflowImpl.class);
    syncWorker.registerActivitiesImplementations(
        new SyncWorkflow.ReplicationActivityImpl(processFactory, secretsHydrator, workspaceRoot),
        new SyncWorkflow.NormalizationActivityImpl(processFactory, secretsHydrator, workspaceRoot, workerEnvironment),
        new SyncWorkflow.DbtTransformationActivityImpl(processFactory, secretsHydrator, workspaceRoot),
        new SyncWorkflow.PersistStateActivityImpl(workspaceRoot, configRepository));
    factory.start();
  }

  private static ProcessFactory getProcessBuilderFactory(final Configs configs) throws IOException {
    if (configs.getWorkerEnvironment() == Configs.WorkerEnvironment.KUBERNETES) {
      final ApiClient officialClient = Config.defaultClient();
      final KubernetesClient fabricClient = new DefaultKubernetesClient();
      final String localIp = InetAddress.getLocalHost().getHostAddress();
      final String kubeHeartbeatUrl = localIp + ":" + KUBE_HEARTBEAT_PORT;
      LOGGER.info("Using Kubernetes namespace: {}", configs.getKubeNamespace());
      return new KubeProcessFactory(configs.getKubeNamespace(), officialClient, fabricClient, kubeHeartbeatUrl, configs.getTemporalWorkerPorts());
    } else {
      return new DockerProcessFactory(
          configs.getWorkspaceRoot(),
          configs.getWorkspaceDockerMount(),
          configs.getLocalDockerMount(),
          configs.getDockerNetwork());
    }
  }

  private static WorkerOptions getWorkerOptions(final int max) {
    return WorkerOptions.newBuilder()
        .setMaxConcurrentActivityExecutionSize(max)
        .build();
  }

  public static void main(final String[] args) throws IOException, InterruptedException {
    final Configs configs = new EnvConfigs();

    LogClientSingleton.setWorkspaceMdc(LogClientSingleton.getSchedulerLogsRoot(configs));

    final Path workspaceRoot = configs.getWorkspaceRoot();
    LOGGER.info("workspaceRoot = " + workspaceRoot);

    final String temporalHost = configs.getTemporalHost();
    LOGGER.info("temporalHost = " + temporalHost);

    final SecretsHydrator secretsHydrator = SecretPersistence.getSecretsHydrator(configs);

    final ProcessFactory processFactory = getProcessBuilderFactory(configs);

    final WorkflowServiceStubs temporalService = TemporalUtils.createTemporalService(temporalHost);

    final Database configDatabase = new ConfigsDatabaseInstance(
        configs.getConfigDatabaseUser(),
        configs.getConfigDatabasePassword(),
        configs.getConfigDatabaseUrl())
            .getInitialized();
    final ConfigPersistence configPersistence = new DatabaseConfigPersistence(configDatabase).withValidation();
    final Optional<SecretPersistence> secretPersistence = SecretPersistence.getLongLived(configs);
    final Optional<SecretPersistence> ephemeralSecretPersistence = SecretPersistence.getEphemeral(configs);
    final ConfigRepository configRepository = new ConfigRepository(configPersistence, secretsHydrator, secretPersistence, ephemeralSecretPersistence);

    new WorkerApp(
        workspaceRoot,
        processFactory,
        secretsHydrator,
        temporalService,
        configs.getMaxWorkers(),
        configs.getWorkerEnvironment(),
        configRepository).start();
  }

}
