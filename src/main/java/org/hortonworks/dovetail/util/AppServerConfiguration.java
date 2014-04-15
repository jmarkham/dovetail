package org.hortonworks.dovetail.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AppServerConfiguration {

	private String jbossHome;
	private String serverGroupName;
	private String serverName;
	private String domainController;
	private String hostName;
	private int portOffset;
	private String adminUser;

	private static final Logger LOG = Logger
			.getLogger(AppServerConfiguration.class.getName());

	/**
	 * Constructor that creates the configuration
	 */
	public AppServerConfiguration() {
		this.jbossHome = System.getenv("JBOSS_HOME");
		LOG.info(String.format("Configuring JBoss at %s", jbossHome));

		this.domainController = System.getenv("JBOSS_DOMAIN_CONTROLLER");
		this.hostName = System.getenv("JBOSS_HOST");
		this.portOffset = Integer.parseInt(System.getenv("JBOSS_PORT_OFFSET"));
		this.serverGroupName = System.getenv("DOVETAIL_APP_ID");
		this.adminUser = System.getenv("DOVETAIL_ADMIN_USER");

		// set by YARN
		this.serverName = System.getenv("CONTAINER_ID");
	}

	public static void main(String[] args) {
		AppServerConfiguration config = new AppServerConfiguration();
		config.configure();
	}

	private void configure() {

		try {
			DovetailConfiguration conf = new DovetailConfiguration();
			conf.set(
					DovetailConfiguration.DOVETAIL_JBOSS_DOMAIN_MASTER_PASSWORD,
					"master");
			conf.set(
					DovetailConfiguration.DOVETAIL_JBOSS_DOMAIN_SLAVE_PASSWORD,
					"slave");

			Util util = new Util(conf);

			util.addManagementUser(jbossHome, adminUser, "test");

			util.addDomainServerGroup(jbossHome, serverGroupName);
			util.addDomainServer(jbossHome, serverGroupName, serverName,
					portOffset);
			util.addDomainController(jbossHome, domainController, hostName,
					portOffset);

		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Problem configuring JBoss AS", e);
		}
	}
}