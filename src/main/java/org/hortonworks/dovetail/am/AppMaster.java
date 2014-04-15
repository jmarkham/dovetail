package org.hortonworks.dovetail.am;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.ContainerManagementProtocol;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerExitStatus;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.client.api.async.NMClientAsync;
import org.apache.hadoop.yarn.client.api.async.impl.NMClientAsyncImpl;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.ContainerLocalizer;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.hortonworks.dovetail.util.DovetailConfiguration;

/**
 * JBoss AS 7.1 Application Master. Spin up domain mode (i.e. clustered)
 * services via YARN.
 */

public class AppMaster {

	private static final Logger LOG = Logger.getLogger(AppMaster.class
			.getName());

	private DovetailConfiguration conf;

	@SuppressWarnings("rawtypes")
	private AMRMClientAsync resourceManager;
	private NMClientAsync nmClientAsync;
	private NMCallbackHandler containerListener;

	private ApplicationAttemptId appAttemptID;

	private String appMasterHostname = "";
	private int appMasterRpcPort = 0;
	private String appMasterTrackingUrl = "";

	private int numContainers;
	private int containerMemory;
	private int containerPriority;

	private AtomicInteger numCompletedContainers = new AtomicInteger();
	private AtomicInteger numAllocatedContainers = new AtomicInteger();
	private AtomicInteger numFailedContainers = new AtomicInteger();
	private AtomicInteger numRequestedContainers = new AtomicInteger();

	private Map<String, String> shellEnv = new HashMap<String, String>();

	private String amJar;
	private String domainController;

	private volatile boolean done;
	private volatile boolean success;

	private List<Thread> launchThreads = new ArrayList<Thread>();

	/**
	 * @param args
	 *            Command line args
	 */
	public static void main(String[] args) {
		boolean result = false;
		try {
			AppMaster appMaster = new AppMaster();
			LOG.info("Initializing Dovetail Application Master");
			boolean doRun = appMaster.init(args);
			if (!doRun) {
				System.exit(0);
			}
			result = appMaster.run();
		} catch (Throwable t) {
			LOG.log(Level.SEVERE, "Error running ApplicationMaster", t);
			System.exit(1);
		}
		if (result) {
			LOG.info("Application Master completed successfully. exiting");
			System.exit(0);
		} else {
			LOG.info("Application Master failed. exiting");
			System.exit(2);
		}
	}

	/**
	 * Dump out contents of $CWD and the environment to stdout for debugging
	 */
	private void dumpOutDebugInfo() {

		LOG.info("Dump debug output");
		Map<String, String> envs = System.getenv();
		for (Map.Entry<String, String> env : envs.entrySet()) {
			LOG.info("System env: key=" + env.getKey() + ", val="
					+ env.getValue());
			System.out.println("System env: key=" + env.getKey() + ", val="
					+ env.getValue());
		}

		String cmd = "ls -al";
		Runtime run = Runtime.getRuntime();
		Process pr = null;
		try {
			pr = run.exec(cmd);
			pr.waitFor();

			BufferedReader buf = new BufferedReader(new InputStreamReader(
					pr.getInputStream()));
			String line = "";
			while ((line = buf.readLine()) != null) {
				LOG.info("System CWD content: " + line);
				System.out.println("System CWD content: " + line);
			}
			buf.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public AppMaster() throws Exception {
		conf = new DovetailConfiguration();
	}

	/**
	 * Parse command line options
	 * 
	 * @param args
	 *            Command line args
	 * @return Whether init successful and run should be invoked
	 * @throws IOException
	 */
	public boolean init(String[] args) throws IOException {

		Map<String, String> envs = System.getenv();

		ContainerId containerId = ConverterUtils.toContainerId(envs
				.get(Environment.CONTAINER_ID.name()));

		containerId = ConverterUtils.toContainerId(envs
				.get(Environment.CONTAINER_ID.name()));
		appAttemptID = containerId.getApplicationAttemptId();

		if (!envs.containsKey(ApplicationConstants.APP_SUBMIT_TIME_ENV)) {
			throw new RuntimeException(ApplicationConstants.APP_SUBMIT_TIME_ENV
					+ " not set in the environment");
		}
		if (!envs.containsKey(Environment.NM_HOST.name())) {
			throw new RuntimeException(Environment.NM_HOST.name()
					+ " not set in the environment");
		}
		if (!envs.containsKey(Environment.NM_HTTP_PORT.name())) {
			throw new RuntimeException(Environment.NM_HTTP_PORT
					+ " not set in the environment");
		}
		if (!envs.containsKey(Environment.NM_PORT.name())) {
			throw new RuntimeException(Environment.NM_PORT.name()
					+ " not set in the environment");
		}

		LOG.info("Application master for app" + ", appId="
				+ appAttemptID.getApplicationId().getId()
				+ ", clustertimestamp="
				+ appAttemptID.getApplicationId().getClusterTimestamp()
				+ ", attemptId=" + appAttemptID.getAttemptId());

		numContainers = conf.getInt(
				DovetailConfiguration.DOVETAIL_CONTAINER_COUNT,
				DovetailConfiguration.DEFAULT_CONTAINER_COUNT);

		containerMemory = conf.getInt(
				DovetailConfiguration.DOVETAIL_CONTAINER_MEMORY,
				DovetailConfiguration.DEFAULT_CONTAINER_MEMORY);

		containerMemory = conf.getInt(
				DovetailConfiguration.DOVETAIL_CONTAINER_PRIORITY,
				DovetailConfiguration.DEFAULT_DOVETAIL_CONTAINER_PRIORITY);

		amJar = conf.get(DovetailConfiguration.DOVETAIL_AM_HDFS_DIR)
				+ Path.SEPARATOR
				+ System.getProperty(DovetailConfiguration.DOVETAIL_AM_JAR);

		return true;
	}

	/**
	 * Main run function for the application master
	 * 
	 * @throws YarnException
	 * @throws IOException
	 */
	@SuppressWarnings({ "unchecked" })
	public boolean run() throws YarnException, IOException {
		LOG.info("Starting ApplicationMaster");

		AMRMClientAsync.CallbackHandler allocListener = new RMCallbackHandler();
		resourceManager = AMRMClientAsync.createAMRMClientAsync(1000,
				allocListener);
		resourceManager.init(conf);
		resourceManager.start();

		containerListener = new NMCallbackHandler();
		nmClientAsync = new NMClientAsyncImpl(containerListener);
		nmClientAsync.init(conf);
		nmClientAsync.start();

		RegisterApplicationMasterResponse response = resourceManager
				.registerApplicationMaster(appMasterHostname, appMasterRpcPort,
						appMasterTrackingUrl);

		int maxMem = response.getMaximumResourceCapability().getMemory();
		LOG.info("Max mem capabililty of resources in this cluster " + maxMem);

		// A resource ask cannot exceed the max.
		if (containerMemory > maxMem) {
			LOG.info("Container memory specified above max threshold of cluster."
					+ " Using max value."
					+ ", specified="
					+ containerMemory
					+ ", max=" + maxMem);
			containerMemory = maxMem;
		}

		for (int i = 0; i < numContainers; ++i) {
			ContainerRequest containerAsk = setupContainerAskForRM();
			resourceManager.addContainerRequest(containerAsk);
		}
		numRequestedContainers.set(numContainers);

		while (!done) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException ex) {
			}
		}
		finish();

		return success;
	}

	private void finish() {
		for (Thread launchThread : launchThreads) {
			try {
				launchThread.join(10000);
			} catch (InterruptedException e) {
				LOG.info("Exception thrown in thread join: " + e.getMessage());
				e.printStackTrace();
			}
		}

		LOG.info("Application completed. Stopping running containers");
		nmClientAsync.stop();

		LOG.info("Application completed. Signalling finish to RM");

		FinalApplicationStatus appStatus;
		String appMessage = null;
		success = true;
		if (numFailedContainers.get() == 0
				&& numCompletedContainers.get() == numContainers) {
			appStatus = FinalApplicationStatus.SUCCEEDED;
		} else {
			appStatus = FinalApplicationStatus.FAILED;
			appMessage = "Diagnostics." + ", total=" + numContainers
					+ ", completed=" + numCompletedContainers.get()
					+ ", allocated=" + numAllocatedContainers.get()
					+ ", failed=" + numFailedContainers.get();
			success = false;
		}
		try {
			resourceManager.unregisterApplicationMaster(appStatus, appMessage,
					null);
		} catch (YarnException ex) {
			LOG.log(Level.SEVERE, "Failed to unregister application", ex);
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "Failed to unregister application", e);
		}

		done = true;
		resourceManager.stop();
	}

	private class RMCallbackHandler implements AMRMClientAsync.CallbackHandler {
		@SuppressWarnings("unchecked")
		public void onContainersCompleted(
				List<ContainerStatus> completedContainers) {
			LOG.info("Got response from RM for container ask, completedCnt="
					+ completedContainers.size());
			for (ContainerStatus containerStatus : completedContainers) {
				LOG.info("Got container status for containerID="
						+ containerStatus.getContainerId() + ", state="
						+ containerStatus.getState() + ", exitStatus="
						+ containerStatus.getExitStatus() + ", diagnostics="
						+ containerStatus.getDiagnostics());

				assert (containerStatus.getState() == ContainerState.COMPLETE);

				int exitStatus = containerStatus.getExitStatus();
				if (0 != exitStatus) {
					if (ContainerExitStatus.ABORTED != exitStatus) {
						numCompletedContainers.incrementAndGet();
						numFailedContainers.incrementAndGet();
					} else {
						numAllocatedContainers.decrementAndGet();
						numRequestedContainers.decrementAndGet();
					}
				} else {
					numCompletedContainers.incrementAndGet();
					LOG.info("Container completed successfully."
							+ ", containerId="
							+ containerStatus.getContainerId());
				}
			}

			int askCount = numContainers - numRequestedContainers.get();
			numRequestedContainers.addAndGet(askCount);

			if (askCount > 0) {
				for (int i = 0; i < askCount; ++i) {
					ContainerRequest containerAsk = setupContainerAskForRM();
					resourceManager.addContainerRequest(containerAsk);
				}
			}

			if (numCompletedContainers.get() == numContainers) {
				done = true;
			}
		}

		public void onContainersAllocated(List<Container> allocatedContainers) {
			LOG.info("Got response from RM for container ask, allocatedCnt="
					+ allocatedContainers.size());
			numAllocatedContainers.addAndGet(allocatedContainers.size());
			for (Container allocatedContainer : allocatedContainers) {
				LOG.info("Launching shell command on a new container."
						+ ", containerId=" + allocatedContainer.getId()
						+ ", containerNode="
						+ allocatedContainer.getNodeId().getHost() + ":"
						+ allocatedContainer.getNodeId().getPort()
						+ ", containerNodeURI="
						+ allocatedContainer.getNodeHttpAddress()
						+ ", containerResourceMemory"
						+ allocatedContainer.getResource().getMemory());

				LaunchContainerRunnable runnableLaunchContainer = new LaunchContainerRunnable(
						allocatedContainer, containerListener);
				Thread launchThread = new Thread(runnableLaunchContainer);

				launchThreads.add(launchThread);
				launchThread.start();
			}
		}

		public void onShutdownRequest() {
			done = true;
		}

		public void onNodesUpdated(List<NodeReport> updatedNodes) {
		}

		public float getProgress() {
			float progress = (float) numCompletedContainers.get()
					/ numContainers;
			return progress;
		}

		public void onError(Throwable e) {
			done = true;
			resourceManager.stop();
		}
	}

	private class NMCallbackHandler implements NMClientAsync.CallbackHandler {

		private ConcurrentMap<ContainerId, Container> containers = new ConcurrentHashMap<ContainerId, Container>();

		public void addContainer(ContainerId containerId, Container container) {
			containers.putIfAbsent(containerId, container);
			LOG.info("Callback container id : " + containerId.toString());

			if (containers.size() == 1) {
				domainController = container.getNodeId().getHost();
			}
		}

		public void onContainerStopped(ContainerId containerId) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Succeeded to stop Container " + containerId);
			}
			containers.remove(containerId);
		}

		public void onContainerStatusReceived(ContainerId containerId,
				ContainerStatus containerStatus) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Container Status: id=" + containerId + ", status="
						+ containerStatus);
			}
		}

		public void onContainerStarted(ContainerId containerId,
				Map<String, ByteBuffer> allServiceResponse) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("Succeeded to start Container " + containerId);
			}
			Container container = containers.get(containerId);
			if (container != null) {
				nmClientAsync.getContainerStatusAsync(containerId,
						container.getNodeId());
			}
		}

		public void onStartContainerError(ContainerId containerId, Throwable t) {
			LOG.log(Level.SEVERE, "Failed to start Container " + containerId, t);
			containers.remove(containerId);
		}

		public void onGetContainerStatusError(ContainerId containerId,
				Throwable t) {
			LOG.log(Level.SEVERE, "Failed to query the status of Container "
					+ containerId, t);
		}

		public void onStopContainerError(ContainerId containerId, Throwable t) {
			LOG.log(Level.SEVERE, "Failed to stop Container " + containerId, t);
			containers.remove(containerId);
		}

		public int getContainerCount() {
			return containers.size();
		}
	}

	/**
	 * Thread to connect to the {@link ContainerManagementProtocol} and launch
	 * the container that will execute the shell command.
	 */
	private class LaunchContainerRunnable implements Runnable {

		Container container;

		NMCallbackHandler containerListener;

		/**
		 * @param lcontainer
		 *            Allocated container
		 * @param containerListener
		 *            Callback handler of the container
		 */
		public LaunchContainerRunnable(Container lcontainer,
				NMCallbackHandler containerListener) {
			this.container = lcontainer;
			this.containerListener = containerListener;
		}

		/**
		 * Connects to CM, sets up container launch context for shell command
		 * and eventually dispatches the container start request to the CM.
		 */
		public void run() {

			String containerId = container.getId().toString();

			LOG.info("Setting up container launch container for containerid="
					+ container.getId());
			ContainerLaunchContext ctx = Records
					.newRecord(ContainerLaunchContext.class);

			ctx.setEnvironment(shellEnv);

			Map<String, LocalResource> localResources = new HashMap<String, LocalResource>();

			String applicationId = container.getId().getApplicationAttemptId()
					.getApplicationId().toString();

			try {
				FileSystem fs = FileSystem.get(conf);
				String host = container.getNodeId().getHost();

				LocalResource jbossDistResource = Records
						.newRecord(LocalResource.class);
				jbossDistResource.setType(LocalResourceType.ARCHIVE);
				jbossDistResource
						.setVisibility(LocalResourceVisibility.APPLICATION);

				String jbossDistUri = conf
						.get(DovetailConfiguration.JBOSS_DIST_HDFS_DIR)
						+ Path.SEPARATOR
						+ System.getProperty(DovetailConfiguration.JBOSS_DIST_FILE);
				LOG.info(String.format("Setting %s as local resource on %s",
						jbossDistUri, host));

				Path jbossDistPath = new Path(new URI(jbossDistUri));
				jbossDistResource.setResource(ConverterUtils
						.getYarnUrlFromPath(jbossDistPath));

				jbossDistResource.setTimestamp(fs.getFileStatus(jbossDistPath)
						.getModificationTime());
				jbossDistResource.setSize(fs.getFileStatus(jbossDistPath)
						.getLen());
				localResources.put(
						conf.get(DovetailConfiguration.DOVETAIL_SYMLINK),
						jbossDistResource);

				LocalResource jbossConfResource = Records
						.newRecord(LocalResource.class);
				jbossConfResource.setType(LocalResourceType.FILE);
				jbossConfResource
						.setVisibility(LocalResourceVisibility.APPLICATION);

				LOG.info(String.format("Setting %s as local resource on %s",
						amJar, host));
				Path jbossConfPath = new Path(new URI(amJar));
				jbossConfResource.setResource(ConverterUtils
						.getYarnUrlFromPath(jbossConfPath));

				jbossConfResource.setTimestamp(fs.getFileStatus(jbossConfPath)
						.getModificationTime());
				jbossConfResource.setSize(fs.getFileStatus(jbossConfPath)
						.getLen());
				localResources.put(
						conf.get(DovetailConfiguration.DOVETAIL_AM_JAR),
						jbossConfResource);

				String containerScriptUri = conf
						.get(DovetailConfiguration.DOVETAIL_BIN_HDFS_DIR)
						+ Path.SEPARATOR
						+ conf.get(DovetailConfiguration.DOVETAIL_CONTAINER_SCRIPT);
				LOG.info(String.format("Setting %s as local resource on %s",
						containerScriptUri, host));

				Path containerScriptPath = new Path(new URI(containerScriptUri));
				FileStatus containerScriptStatus = fs
						.getFileStatus(containerScriptPath);
				LocalResource containerScriptResource = Records
						.newRecord(LocalResource.class);

				containerScriptResource.setType(LocalResourceType.FILE);
				containerScriptResource
						.setVisibility(LocalResourceVisibility.APPLICATION);
				containerScriptResource.setResource(ConverterUtils
						.getYarnUrlFromPath(containerScriptPath));
				containerScriptResource.setTimestamp(containerScriptStatus
						.getModificationTime());
				containerScriptResource.setSize(containerScriptStatus.getLen());
				localResources.put(conf
						.get(DovetailConfiguration.DOVETAIL_CONTAINER_SCRIPT),
						containerScriptResource);

				ctx.setLocalResources(localResources);

				List<String> commands = new ArrayList<String>();

				String containerHome = conf
						.get(DovetailConfiguration.NM_LOCAL_DIRS)
						+ File.separator
						+ ContainerLocalizer.USERCACHE
						+ File.separator
						+ System.getenv().get(Environment.USER.toString())
						+ File.separator
						+ ContainerLocalizer.APPCACHE
						+ File.separator
						+ applicationId
						+ File.separator
						+ containerId;

				String jbossHomeParent = containerHome + File.separator
						+ conf.get(DovetailConfiguration.DOVETAIL_SYMLINK);

				int portOffset = 0;
				int containerCount = containerListener.getContainerCount();
				if (containerCount > 1) {
					portOffset = containerCount * 150;
				}

				Map<String, String> env = new HashMap<String, String>();
				env.put("JBOSS_HOME_PARENT_LINK", jbossHomeParent);
				env.put("JBOSS_DOMAIN_CONTROLLER",
						domainController == null ? host : domainController);
				env.put("JBOSS_HOST", host);
				env.put("JBOSS_PORT_OFFSET", String.valueOf(portOffset));
				env.put("DOVETAIL_APP_ID", applicationId);
				env.put("DOVETAIL_ADMIN_USER",
						conf.get(DovetailConfiguration.DOVETAIL_ADMIN_USER));
				env.put("DOVETAIL_APP", containerHome + File.separatorChar
						+ conf.get(DovetailConfiguration.DOVETAIL_AM_JAR));
				env.put("JAVA_OPTS",
						conf.get(DovetailConfiguration.DOVETAIL_CONTAINER_JAVA_OPTS));
				ctx.setEnvironment(env);

				commands.add(containerHome
						+ File.separator
						+ conf.get(DovetailConfiguration.DOVETAIL_CONTAINER_SCRIPT));
				ctx.setCommands(commands);

				containerListener.addContainer(container.getId(), container);
				nmClientAsync.startContainerAsync(container, ctx);

			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Problem setting local resources", e);
				numCompletedContainers.incrementAndGet();
				numFailedContainers.incrementAndGet();
				return;
			}
		}
	}

	/**
	 * Setup the request that will be sent to the RM for the container ask.
	 * 
	 * @param numContainers
	 *            Containers to ask for from RM
	 * @return the setup ResourceRequest to be sent to RM
	 */
	private ContainerRequest setupContainerAskForRM() {
		Priority pri = Records.newRecord(Priority.class);
		pri.setPriority(containerPriority);

		Resource capability = Records.newRecord(Resource.class);
		capability.setMemory(containerMemory);
		capability.setVirtualCores(2);

		ContainerRequest request = new ContainerRequest(capability, null, null,
				pri);
		LOG.info("Requested container ask: " + request.toString());
		return request;
	}
}