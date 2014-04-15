package org.hortonworks.dovetail.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

public class DovetailConfiguration extends YarnConfiguration {

	private static final String DOVETAIL_DEFAULT_XML_FILE = "dovetail-default.xml";
	private static final String DOVETAIL_SITE_XML_FILE = "dovetail-site.xml";

	static {

		DovetailConfiguration.addDefaultResource(DOVETAIL_DEFAULT_XML_FILE);
		DovetailConfiguration.addDefaultResource(DOVETAIL_SITE_XML_FILE);

	}

	public DovetailConfiguration() {
		super();
	}

	public DovetailConfiguration(Configuration conf) {
		super(conf);
		if (!(conf instanceof DovetailConfiguration)) {
			this.reloadConfiguration();
		}
	}

	public static final String DOVETAIL_PREFIX = "dovetail.";

	public static final String DOVETAIL_APP_NAME = DOVETAIL_PREFIX + "app.name";

	public static final String DOVETAIL_ADMIN_USER = DOVETAIL_PREFIX
			+ "admin.user";

	public static final String DOVETAIL_HOME = DOVETAIL_PREFIX + "home";

	public static final String DOVETAIL_LOG_DIR = DOVETAIL_PREFIX + "log.dir";

	public static final String DOVETAIL_AM_HDFS_DIR = DOVETAIL_PREFIX
			+ "am.hdfs.dir";

	public static final String DOVETAIL_AM_LOCAL_DIR = DOVETAIL_PREFIX
			+ "am.local.dir";

	public static final String DOVETAIL_AM_JAR = DOVETAIL_PREFIX + "am.jar";

	public static final String JBOSS_DIST_HDFS_DIR = DOVETAIL_PREFIX
			+ "jboss.dist.hdfs.dir";

	public static final String JBOSS_DIST_LOCAL_DIR = DOVETAIL_PREFIX
			+ "jboss.dist.local.dir";

	public static final String JBOSS_DIST_FILE = DOVETAIL_PREFIX
			+ "jboss.dist.file";

	public static final String DOVETAIL_CONF_LOCAL_DIR = DOVETAIL_PREFIX
			+ "conf.local.dir";

	public static final String DOVETAIL_CONF_HDFS_DIR = DOVETAIL_PREFIX
			+ "conf.hdfs.dir";

	public static final String DOVETAIL_BIN_LOCAL_DIR = DOVETAIL_PREFIX
			+ "bin.local.dir";

	public static final String DOVETAIL_BIN_HDFS_DIR = DOVETAIL_PREFIX
			+ "bin.hdfs.dir";

	public static final String DOVETAIL_CONTAINER_SCRIPT = DOVETAIL_PREFIX
			+ "container.script";

	public static final String DOVETAIL_SYMLINK = DOVETAIL_PREFIX + "symlink";

	public static final String DOVETAIL_JBOSS_HOME = DOVETAIL_PREFIX
			+ "jboss.home";

	public static final String DOVETAIL_JBOSS_MANAGEMENT_REALM = DOVETAIL_PREFIX
			+ "jboss.management.realm";

	public static final String DOVETAIL_JBOSS_DOMAIN_MASTER_PASSWORD = DOVETAIL_PREFIX
			+ "jboss.domain.master.password";

	public static final String DOVETAIL_JBOSS_DOMAIN_SLAVE_PASSWORD = DOVETAIL_PREFIX
			+ "jboss.domain.slave.password";

	public static final String DOVETAIL_AM_QUEUE = DOVETAIL_PREFIX + "am.queue";

	public static final String DOVETAIL_AM_PRIORITY = DOVETAIL_PREFIX
			+ "am.priority";

	public static final int DEFAULT_DOVETAIL_AM_PRIORITY = 0;

	public static final String DOVETAIL_AM_MEMORY = DOVETAIL_PREFIX
			+ "am.memory-mb";

	public static final int DEFAULT_DOVETAIL_AM_MEMORY = 256;

	public static final String DOVETAIL_CONTAINER_MEMORY = DOVETAIL_PREFIX
			+ "container.memory-mb";
	
	public static final String DOVETAIL_CONTAINER_JAVA_OPTS = DOVETAIL_PREFIX
			+ "container.java.opts";

	public static final int DEFAULT_CONTAINER_MEMORY = 1024;

	public static final String DOVETAIL_CONTAINER_PRIORITY = DOVETAIL_PREFIX
			+ "container.priority";

	public static final int DEFAULT_DOVETAIL_CONTAINER_PRIORITY = 0;

	public static final String DOVETAIL_CONTAINER_COUNT = DOVETAIL_PREFIX
			+ "container.count";

	public static final int DEFAULT_CONTAINER_COUNT = 2;

}