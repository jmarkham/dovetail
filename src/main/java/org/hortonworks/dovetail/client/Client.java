package org.hortonworks.dovetail.client;

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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.QueueACL;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.QueueUserACLInfo;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.api.records.YarnClusterMetrics;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.hortonworks.dovetail.am.AppMasterCommand;
import org.hortonworks.dovetail.util.DovetailConfiguration;

/**
 * Client for Dovetail Application Master submission to YARN
 * 
 */
public class Client {

	private static final Logger LOG = Logger.getLogger(Client.class.getName());

	private DovetailConfiguration conf;
	private YarnClient yarnClient;

	private String appName;

	private String amQueue;
	private int amPriority;

	private int amMemory;
	private int containerMemory;
	private int numContainers;

	boolean debugFlag = false;

	/**
	 * @param args
	 *            Command line arguments
	 */
	public static void main(String[] args) {
		boolean result = false;
		try {
			Client client = new Client();
			LOG.info("Initializing Client");
			try {
				client.init();
				if (!client.isValidConfiguration()) {
					System.exit(0);
				}
			} catch (IllegalArgumentException e) {
				LOG.log(Level.SEVERE, "Error running Dovetail Client", e);
				System.exit(-1);
			}
			result = client.run();
		} catch (Throwable t) {
			LOG.log(Level.SEVERE, "Error running Dovetail Client", t);
			System.exit(1);
		}
		if (result) {
			LOG.info("Application completed successfully");
			System.exit(0);
		}
		LOG.log(Level.SEVERE, "Application failed to complete successfully");
		System.exit(2);
	}

	/**
   */
	public Client(DovetailConfiguration conf) throws Exception {

		this.conf = conf;
		yarnClient = YarnClient.createYarnClient();
		yarnClient.init(conf);
	}

	/**
   */
	public Client() throws Exception {
		this(new DovetailConfiguration());
	}

	/**
	 * Parse command line options
	 * 
	 * @param args
	 *            Parsed command line options
	 * @return Whether the init was successful to run the client
	 */
	private void init() {

		appName = conf.get(DovetailConfiguration.DOVETAIL_APP_NAME);

		conf.set(DovetailConfiguration.DOVETAIL_HOME,
				System.getProperty(DovetailConfiguration.DOVETAIL_HOME));

		File amFileDir = new File(
				conf.get(DovetailConfiguration.DOVETAIL_AM_LOCAL_DIR));

		for (File file : amFileDir.listFiles()) {
			if (file.isFile() && file.getName().startsWith("dovetail")) {
				conf.set(DovetailConfiguration.DOVETAIL_AM_JAR, file.getName());
				break;
			}
		}

		File jbossDistFileDir = new File(
				conf.get(DovetailConfiguration.JBOSS_DIST_LOCAL_DIR));

		for (File file : jbossDistFileDir.listFiles()) {
			if (file.isFile()) {
				conf.set(DovetailConfiguration.JBOSS_DIST_FILE, file.getName());
				break;
			}
		}

		amQueue = conf.get(DovetailConfiguration.DOVETAIL_AM_QUEUE);
		amPriority = conf.getInt(DovetailConfiguration.DOVETAIL_AM_PRIORITY,
				DovetailConfiguration.DEFAULT_DOVETAIL_AM_PRIORITY);
		amMemory = conf.getInt(DovetailConfiguration.DOVETAIL_AM_MEMORY,
				DovetailConfiguration.DEFAULT_DOVETAIL_AM_MEMORY);

		containerMemory = conf.getInt(
				DovetailConfiguration.DOVETAIL_CONTAINER_MEMORY,
				DovetailConfiguration.DEFAULT_CONTAINER_MEMORY);
		numContainers = conf.getInt(
				DovetailConfiguration.DOVETAIL_CONTAINER_COUNT,
				DovetailConfiguration.DEFAULT_CONTAINER_COUNT);

		LOG.info(String.format("Using %s for log file directory",
				System.getProperty(DovetailConfiguration.DOVETAIL_LOG_DIR)));
		conf.set(DovetailConfiguration.DOVETAIL_LOG_DIR,
				System.getProperty(DovetailConfiguration.DOVETAIL_LOG_DIR));
	}

	private boolean isValidConfiguration() {

		if (amMemory < 0) {
			throw new IllegalArgumentException(
					String.format(
							"Configured memory for Application Master is %n.  Memory must not be less than 0.",
							amMemory));
		}
		if (containerMemory < 0) {
			throw new IllegalArgumentException(
					String.format(
							"Configured memory for containers is %n.  Memory must not be less than 0.",
							containerMemory));
		}

		if (numContainers < 1) {
			throw new IllegalArgumentException(
					String.format(
							"Configured number of containers is %n.  Must have 1 or more containers.",
							numContainers));
		}

		return true;
	}

	/**
	 * Main run function for the client
	 * 
	 * @return true if application completed successfully
	 * @throws IOException
	 * @throws YarnException
	 */
	private boolean run() throws IOException, YarnException {

		FileSystem fs = FileSystem.get(conf);
		Deployer deployer = new Deployer(fs, conf);
		deployer.deployArtifacts();
		Path amPath = deployer.getAppMasterPath();
		List<Path> configPaths = deployer.getConfigPaths();

		LOG.info("Running Client");
		yarnClient.start();

		YarnClusterMetrics clusterMetrics = yarnClient.getYarnClusterMetrics();
		LOG.info("Got Cluster metric info from ASM" + ", numNodeManagers="
				+ clusterMetrics.getNumNodeManagers());

		List<NodeReport> clusterNodeReports = yarnClient
				.getNodeReports(NodeState.RUNNING);
		LOG.info("Got Cluster node info from ASM");
		for (NodeReport node : clusterNodeReports) {
			LOG.info("Got node report from ASM for" + ", nodeId="
					+ node.getNodeId() + ", nodeAddress"
					+ node.getHttpAddress() + ", nodeRackName"
					+ node.getRackName() + ", nodeNumContainers"
					+ node.getNumContainers());
		}

		QueueInfo queueInfo = yarnClient.getQueueInfo(this.amQueue);
		LOG.info("Queue info" + ", queueName=" + queueInfo.getQueueName()
				+ ", queueCurrentCapacity=" + queueInfo.getCurrentCapacity()
				+ ", queueMaxCapacity=" + queueInfo.getMaximumCapacity()
				+ ", queueApplicationCount="
				+ queueInfo.getApplications().size()
				+ ", queueChildQueueCount=" + queueInfo.getChildQueues().size());

		List<QueueUserACLInfo> listAclInfo = yarnClient.getQueueAclsInfo();
		for (QueueUserACLInfo aclInfo : listAclInfo) {
			for (QueueACL userAcl : aclInfo.getUserAcls()) {
				LOG.info("User ACL Info for Queue" + ", queueName="
						+ aclInfo.getQueueName() + ", userAcl="
						+ userAcl.name());
			}
		}

		YarnClientApplication app = yarnClient.createApplication();
		GetNewApplicationResponse appResponse = app.getNewApplicationResponse();
		int maxMem = appResponse.getMaximumResourceCapability().getMemory();
		LOG.info("Max mem capabililty of resources in this cluster " + maxMem);

		if (amMemory > maxMem) {
			LOG.info("AM memory specified above max threshold of cluster. Using max value."
					+ ", specified=" + amMemory + ", max=" + maxMem);
			amMemory = maxMem;
		}

		ApplicationSubmissionContext appContext = app
				.getApplicationSubmissionContext();
		ApplicationId appId = appContext.getApplicationId();
		appContext.setApplicationName(appName);

		ContainerLaunchContext amContainer = Records
				.newRecord(ContainerLaunchContext.class);

		Map<String, LocalResource> localResources = new HashMap<String, LocalResource>();

		FileStatus amStatus = fs.getFileStatus(amPath);
		LocalResource amJarFileResource = Records
				.newRecord(LocalResource.class);

		amJarFileResource.setType(LocalResourceType.FILE);
		amJarFileResource.setVisibility(LocalResourceVisibility.APPLICATION);
		amJarFileResource
				.setResource(ConverterUtils.getYarnUrlFromPath(amPath));
		amJarFileResource.setTimestamp(amStatus.getModificationTime());
		amJarFileResource.setSize(amStatus.getLen());
		localResources.put(conf.get(DovetailConfiguration.DOVETAIL_AM_JAR),
				amJarFileResource);

		for (Path path : configPaths) {

			FileStatus configFileStatus = fs.getFileStatus(path);
			LocalResource configFileResource = Records
					.newRecord(LocalResource.class);
			configFileResource.setType(LocalResourceType.FILE);
			configFileResource
					.setVisibility(LocalResourceVisibility.APPLICATION);
			configFileResource.setResource(ConverterUtils
					.getYarnUrlFromURI(path.toUri()));
			configFileResource.setTimestamp(configFileStatus
					.getModificationTime());
			configFileResource.setSize(configFileStatus.getLen());

			localResources.put(path.getName(), configFileResource);
		}

		fs.close();

		amContainer.setLocalResources(localResources);

		LOG.info("Set the environment for the application master");
		Map<String, String> env = new HashMap<String, String>();

		StringBuilder classPathEnv = new StringBuilder(
				Environment.CLASSPATH.$()).append(File.pathSeparatorChar)
				.append("./*");
		for (String c : conf.getStrings(
				YarnConfiguration.YARN_APPLICATION_CLASSPATH,
				YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH)) {
			classPathEnv.append(File.pathSeparatorChar);
			classPathEnv.append(c.trim());
		}

		if (conf.getBoolean(YarnConfiguration.IS_MINI_YARN_CLUSTER, false)) {
			classPathEnv.append(File.pathSeparatorChar);
			classPathEnv.append(System.getProperty("java.class.path"));
		}

		env.put("CLASSPATH", classPathEnv.toString());

		env.put("JAVA_HOME", System.getProperty("dovetail.java.home"));
		LOG.info("JAVA_HOME=" + System.getenv("JAVA_HOME"));
		amContainer.setEnvironment(env);

		amContainer.setCommands(AppMasterCommand.getCommands(conf));

		Resource capability = Records.newRecord(Resource.class);
		capability.setMemory(amMemory);
		appContext.setResource(capability);

		appContext.setAMContainerSpec(amContainer);

		Priority pri = Records.newRecord(Priority.class);
		pri.setPriority(amPriority);
		appContext.setPriority(pri);

		appContext.setQueue(amQueue);

		LOG.info("Submitting the application to ASM");

		yarnClient.submitApplication(appContext);

		return monitorApplication(appId);
	}

	/**
	 * Monitor the submitted application for completion. Kill application if
	 * time expires.
	 * 
	 * @param appId
	 *            Application Id of application to be monitored
	 * @return true if application completed successfully
	 * @throws YarnException
	 * @throws IOException
	 */
	private boolean monitorApplication(ApplicationId appId)
			throws YarnException, IOException {

		while (true) {

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				LOG.finest("Thread sleep in monitoring loop interrupted");
			}

			ApplicationReport report = yarnClient.getApplicationReport(appId);

			LOG.info("Got application report from ASM for" + ", appId="
					+ appId.getId() + ", clientToAMToken="
					+ report.getClientToAMToken() + ", appDiagnostics="
					+ report.getDiagnostics() + ", appMasterHost="
					+ report.getHost() + ", appQueue=" + report.getQueue()
					+ ", appMasterRpcPort=" + report.getRpcPort()
					+ ", appStartTime=" + report.getStartTime()
					+ ", yarnAppState="
					+ report.getYarnApplicationState().toString()
					+ ", distributedFinalState="
					+ report.getFinalApplicationStatus().toString()
					+ ", appTrackingUrl=" + report.getTrackingUrl()
					+ ", appUser=" + report.getUser());

			YarnApplicationState state = report.getYarnApplicationState();
			FinalApplicationStatus dovetailStatus = report
					.getFinalApplicationStatus();
			if (YarnApplicationState.FINISHED == state) {
				if (FinalApplicationStatus.SUCCEEDED == dovetailStatus) {
					LOG.info("Application has completed successfully. Breaking monitoring loop");
					return true;
				} else {
					LOG.info("Application did finished unsuccessfully."
							+ " YarnState=" + state.toString()
							+ ", DovetailFinalStatus="
							+ dovetailStatus.toString()
							+ ". Breaking monitoring loop");
					return false;
				}
			} else if (YarnApplicationState.KILLED == state
					|| YarnApplicationState.FAILED == state) {
				LOG.info("Application did not finish." + " YarnState="
						+ state.toString() + ", DovetailFinalStatus="
						+ dovetailStatus.toString()
						+ ". Breaking monitoring loop");
				return false;
			}
		}
	}
}