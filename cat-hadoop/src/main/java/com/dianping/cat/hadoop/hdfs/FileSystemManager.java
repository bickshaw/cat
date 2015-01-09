package com.dianping.cat.hadoop.hdfs;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.unidal.lookup.annotation.Inject;

import com.dianping.cat.Cat;
import com.dianping.cat.configuration.ServerConfigManager;

public class FileSystemManager implements Initializable {
	@Inject
	private ServerConfigManager m_configManager;

	private String m_defaultBaseDir;

	private Map<String, FileSystem> m_fileSystems = new HashMap<String, FileSystem>();

	private Configuration m_config;

	public long getFileMaxSize(String id) {
		return m_configManager.getHdfsFileMaxSize(id);
	}

	public FileSystem getFileSystem(String id, StringBuilder basePath) throws IOException {
		String serverUri = m_configManager.getHdfsServerUri(id);
		String baseDir = m_configManager.getHdfsBaseDir(id);
		FileSystem fs = m_fileSystems.get(id);

		if (serverUri == null || !serverUri.startsWith("hdfs:")) {
			// no config found, use local HDFS
			if (fs == null) {
				fs = FileSystem.getLocal(m_config);
				m_fileSystems.put(id, fs);
			}

			basePath.append(m_defaultBaseDir).append("/");

			if (baseDir == null) {
				basePath.append(id);
			} else {
				basePath.append(baseDir);
			}
		} else {
			if (fs == null) {
				URI uri = URI.create(serverUri);
				fs = FileSystem.get(uri, m_config);
				m_fileSystems.put(id, fs);
			}

			if (baseDir == null) {
				basePath.append(id);
			} else {
				basePath.append(baseDir);
			}
		}

		return fs;
	}

	// prepare file /etc/krb5.conf
	// prepare file /data/appdatas/cat/cat.keytab
	// prepare mapping [host] => [ip] at /etc/hosts
	// put core-site.xml at / of classpath
	// use "hdfs://dev80.hadoop:9000/user/cat" as example. Notes: host name can't
	// be an ip address
	private Configuration getHdfsConfiguration() throws IOException {
		Configuration config = new Configuration();
		Map<String, String> properties = m_configManager.getHdfsProperties();

		config.setInt("io.file.buffer.size", 8192);

		if (m_configManager.isOffline()) {
			config.setInt("dfs.replication", 1);
		}

		for (Map.Entry<String, String> property : properties.entrySet()) {
			config.set(property.getKey(), property.getValue());
		}

		return config;
	}

	private String getValue(Map<String, String> properties, String name, String defaultValue) {
		String value = properties.get(name);

		if (value != null) {
			return value;
		} else {
			return defaultValue;
		}
	}

	@Override
	public void initialize() throws InitializationException {
		m_defaultBaseDir = m_configManager.getHdfsLocalBaseDir("hdfs");

		if (m_configManager.isHdfsOn() && !m_configManager.isLocalMode()) {
			try {
				m_config = getHdfsConfiguration();
//				SecurityUtil.login(m_config, "dfs.cat.keytab.file", "dfs.cat.kerberos.principal");
			} catch (IOException e) {
				Cat.logError(e);
			}
		} else {
			m_config = new Configuration();
		}
	}
}
