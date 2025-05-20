package org.cloudfoundry.samples.petclinic.config;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import org.springframework.cloud.bindings.Bindings;
import org.springframework.cloud.bindings.Binding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpringApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final Log logger = LogFactory.getLog(SpringApplicationContextInitializer.class);

	private static final Map<String, List<String>> profileNameToServiceTags = new HashMap<>();

	private static final Map<String, String> serviceTypesToProfileName = new HashMap<>();

	static {
		profileNameToServiceTags.put("postgres", Collections.singletonList("postgres"));
		profileNameToServiceTags.put("mysql", Collections.singletonList("mysql"));

		serviceTypesToProfileName.put("mysql", "mysql");
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		ConfigurableEnvironment appEnvironment = applicationContext.getEnvironment();

		validateActiveProfiles(appEnvironment);

		addCloudProfile(appEnvironment);
	}

	private void addCloudProfile(ConfigurableEnvironment appEnvironment) {
		CfEnv cfEnv = new CfEnv();

		List<String> profiles = new ArrayList<>();

		List<CfService> services = cfEnv.findAllServices();
		List<String> serviceNames = services.stream().map(CfService::getName).collect(Collectors.toList());

		logger.info("Found services " + StringUtils.collectionToCommaDelimitedString(serviceNames));

		for (CfService service : services) {
			for (String profileKey : profileNameToServiceTags.keySet()) {
				if (service.getTags().containsAll(profileNameToServiceTags.get(profileKey))) {
					profiles.add(profileKey);
				}
			}
		}

		if (profiles.size() > 1) {
			throw new IllegalStateException("Only one service of the following types may be bound to this application: "
					+ profileNameToServiceTags.values().toString() + ". "
					+ "These services are bound to the application: ["
					+ StringUtils.collectionToCommaDelimitedString(profiles) + "]");
		}

		List<CfService> llmServices = cfEnv.findServicesByTag("llm");
		if (!llmServices.isEmpty()) {
			logger.info("Setting service profile llm");
			appEnvironment.addActiveProfile("llm");

		}

		if (profiles.size() > 0) {
			logger.info("Setting service profile " + profiles.get(0));
			appEnvironment.addActiveProfile(profiles.get(0));
		}
	}

	private void validateActiveProfiles(ConfigurableEnvironment appEnvironment) {
		Set<String> validLocalProfiles = profileNameToServiceTags.keySet();

		List<String> serviceProfiles = Stream.of(appEnvironment.getActiveProfiles())
			.filter(validLocalProfiles::contains)
			.collect(Collectors.toList());

		if (serviceProfiles.size() > 1) {
			throw new IllegalStateException("Only one active Spring profile may be set among the following: "
					+ validLocalProfiles.toString() + ". " + "These profiles are active: ["
					+ StringUtils.collectionToCommaDelimitedString(serviceProfiles) + "]");
		}
	}

}
