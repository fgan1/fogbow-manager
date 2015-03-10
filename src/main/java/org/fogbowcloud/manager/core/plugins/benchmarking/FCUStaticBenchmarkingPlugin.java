package org.fogbowcloud.manager.core.plugins.benchmarking;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.instance.Instance;

public class FCUStaticBenchmarkingPlugin implements BenchmarkingPlugin {
	
	Map<String, Double> instanceToPower = new HashMap<String, Double>();
	
	private static final Logger LOGGER = Logger.getLogger(FCUStaticBenchmarkingPlugin.class);
	
	public FCUStaticBenchmarkingPlugin(Properties properties) {
	}

	@Override
	public void run(Instance instance) {
		LOGGER.info("Running benchmarking on instance: " + instance);
		if (instance == null) {
			throw new IllegalArgumentException("Instance must not be null.");
		}

		Double power = UNDEFINED_POWER;

		try {
			double vcpu = Double.parseDouble(instance.getAttributes().get("occi.compute.core"));
			double memory = Double.parseDouble(instance.getAttributes().get("occi.compute.memory"));

			power = ((vcpu / 8d) + (memory / 16d)) / 2;
		} catch (Exception e) {
			LOGGER.error("Error while parsing attribute values to double.", e);
		}
		instanceToPower.put(instance.getId(), power);
	}

	@Override
	public double getPower(String instanceId) {
		if (instanceToPower.get(instanceId) == null) {
			return UNDEFINED_POWER;
		}
		return instanceToPower.get(instanceId);
	}
}
