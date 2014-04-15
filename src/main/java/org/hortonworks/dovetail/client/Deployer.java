package org.hortonworks.dovetail.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.hortonworks.dovetail.util.DovetailConfiguration;

public class Deployer {

	private DovetailConfiguration conf;
	private FileSystem fs;
	private Path appMasterPath;
	private Path distPath;
	private Path containerScriptPath;
	private List<Path> configPaths;

	private static final Logger LOG = Logger
			.getLogger(Deployer.class.getName());

	public Deployer(FileSystem fs, DovetailConfiguration conf)
			throws IOException {
		this.conf = conf;
		this.fs = fs;
	}

	public void deployArtifacts() throws IOException {
		appMasterPath = deployAppMaster();
		distPath = deployDistribution();
		configPaths = deployConfiguration();
		containerScriptPath = deployContainerScript();
	}

	private Path deployContainerScript() throws IOException {

		LOG.info(String.format(
				"Deploying Dovetail container script from %s%s%s",
				conf.get(DovetailConfiguration.DOVETAIL_AM_LOCAL_DIR),
				Path.SEPARATOR, conf.get(DovetailConfiguration.DOVETAIL_AM_JAR)));

		Path src = new Path(
				conf.get(DovetailConfiguration.DOVETAIL_BIN_LOCAL_DIR)
						+ Path.SEPARATOR
						+ conf.get(DovetailConfiguration.DOVETAIL_CONTAINER_SCRIPT));

		Path dst = new Path(
				conf.get(DovetailConfiguration.DOVETAIL_BIN_HDFS_DIR)
						+ Path.SEPARATOR
						+ conf.get(DovetailConfiguration.DOVETAIL_CONTAINER_SCRIPT));

		LOG.info(String.format("Deploying Dovetail container script %s to %s",
				src.toUri().toString(), dst.toUri().toString()));

		fs.copyFromLocalFile(false, true, src, dst);
		return dst;
	}

	private Path deployAppMaster() throws IOException {

		LOG.info(String.format(
				"Deploying Dovetail Application Master from %s%s%s",
				conf.get(DovetailConfiguration.DOVETAIL_AM_LOCAL_DIR),
				Path.SEPARATOR, conf.get(DovetailConfiguration.DOVETAIL_AM_JAR)));

		Path src = new Path(
				conf.get(DovetailConfiguration.DOVETAIL_AM_LOCAL_DIR)
						+ Path.SEPARATOR
						+ conf.get(DovetailConfiguration.DOVETAIL_AM_JAR));

		Path dst = new Path(
				conf.get(DovetailConfiguration.DOVETAIL_AM_HDFS_DIR)
						+ Path.SEPARATOR
						+ conf.get(DovetailConfiguration.DOVETAIL_AM_JAR));

		LOG.info(String.format(
				"Deploying Dovetail Application Master file %s to %s", src
						.toUri().toString(), dst.toUri().toString()));

		fs.copyFromLocalFile(false, true, src, dst);
		return dst;
	}

	private Path deployDistribution() throws IOException {

		Path dst = new Path(conf.get(DovetailConfiguration.JBOSS_DIST_HDFS_DIR)
				+ Path.SEPARATOR
				+ conf.get(DovetailConfiguration.JBOSS_DIST_FILE));

		if (!isDistributionDeployed()) {
			Path src = new Path(
					conf.get(DovetailConfiguration.JBOSS_DIST_LOCAL_DIR)
							+ Path.SEPARATOR
							+ conf.get(DovetailConfiguration.JBOSS_DIST_FILE));

			LOG.info(String
					.format("Deploying JBoss Application Server distribution file %s to %s",
							src.toUri().toString(), dst.toUri().toString()));

			fs.copyFromLocalFile(false, false, src, dst);
		}
		return dst;
	}

	private List<Path> deployConfiguration() throws IOException {

		List<Path> configPaths = new ArrayList<Path>();
		File configFileDir = new File(
				conf.get(DovetailConfiguration.DOVETAIL_CONF_LOCAL_DIR));

		for (String fileName : configFileDir.list()) {

			Path src = new Path(
					conf.get(DovetailConfiguration.DOVETAIL_CONF_LOCAL_DIR)
							+ Path.SEPARATOR + fileName);

			Path dst = new Path(
					conf.get(DovetailConfiguration.DOVETAIL_CONF_HDFS_DIR)
							+ Path.SEPARATOR + fileName);

			LOG.info(String.format(
					"Deploying Dovetail configuration file %s to %s", src
							.toUri().toString(), dst.toUri().toString()));

			fs.copyFromLocalFile(false, true, src, dst);
		}
		return configPaths;
	}

	private boolean isDistributionDeployed() throws IOException {

		Path hdfsDistDir = new Path(
				conf.get(DovetailConfiguration.JBOSS_DIST_HDFS_DIR));
		RemoteIterator<LocatedFileStatus> files = fs.listFiles(hdfsDistDir,
				false);

		String jbossDistFile = conf.get(DovetailConfiguration.JBOSS_DIST_FILE);
		while (files.hasNext()) {
			LocatedFileStatus file = files.next();
			if (file.getPath().getName().equals(jbossDistFile)) {

				LOG.info(String
						.format("Found JBoss Application Server distribution file %s at %s",
								jbossDistFile, hdfsDistDir));

				return true;
			}
		}

		return false;
	}

	public Path getAppMasterPath() {
		return appMasterPath;
	}

	public Path getDistPath() {
		return distPath;
	}

	public List<Path> getConfigPaths() {
		return configPaths;
	}
	
	public Path getContainerScriptPath() {
		return containerScriptPath;
	}
}