package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.MainHelper;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.common.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.storage.StorageAttribute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;

public class CloudStackComputePlugin implements ComputePlugin {
	
	private static final Logger LOGGER = Logger.getLogger(CloudStackComputePlugin.class);
	
	private static final String JSON_JOB_ID = "jobid";
	protected static final String JSON_QUERY_ASYNC_JOB_RESULT_RESPONSE = 
			"queryasyncjobresultresponse";
	protected static final String JSON_JOB_STATUS = "jobstatus";	
	public static final String COMPUTE_CLOUDSTACK_DEFAULT_NETWORKID = 
			"compute_cloudstack_default_networkid";
	protected static final String DEFAULT_NETWORK_ID_IS_EMPTY = "Default network id is empty. " 
			+ COMPUTE_CLOUDSTACK_DEFAULT_NETWORKID + " is required.";
	protected static final String LIST_VMS_COMMAND = "listVirtualMachines";
	protected static final String DEPLOY_VM_COMMAND = "deployVirtualMachine";
	protected static final String LIST_RESOURCE_LIMITS_COMMAND = "listResourceLimits";
	protected static final String DESTROY_VM_COMMAND = "destroyVirtualMachine";
	protected static final String LIST_SERVICE_OFFERINGS_COMMAND = "listServiceOfferings";
	protected static final String LIST_DISK_OFFERINGS_COMMAND = "listDiskOfferings";
	protected static final String LIST_TEMPLATES_COMMAND = "listTemplates";
	protected static final String REGISTER_TEMPLATE_COMMAND = "registerTemplate";
	protected static final String LIST_OS_TYPES_COMMAND = "listOsTypes";
	protected static final String ATTACH_VOLUME_COMMAND = "attachVolume";
	protected static final String DETACH_VOLUME_COMMAND = "detachVolume";
	protected static final String QUERY_ASYNC_JOB_RESULT = "queryAsyncJobResult";
	
	protected static final String COMMAND = "command";
	protected static final String TEMPLATE_ID = "templateid";
	protected static final String SERVICE_OFFERING_ID = "serviceofferingid";
	protected static final String DISK_OFFERING_ID = "diskofferingid";	
	protected static final String ZONE_ID = "zoneid";
	protected static final String VM_ID = "id";
	protected static final String VM_EXPUNGE = "expunge";
	protected static final String TEMPLATE_FILTER = "templatefilter";
	protected static final String IS_PUBLIC = "ispublic";
	protected static final String URL = "url";
	protected static final String OS_TYPE_ID = "ostypeid";
	protected static final String NAME = "name";
	protected static final String HYPERVISOR = "hypervisor";
	protected static final String FORMAT = "format";
	protected static final String DISPLAY_TEXT = "displaytext";
	protected static final String USERDATA = "userdata";
	protected static final String ATTACH_VOLUME_ID = "id";
	protected static final String ATTACH_VM_ID = "virtualmachineid";
	protected static final String ATTACH_DEVICE_ID = "deviceid";
	protected static final String JOB_ID = JSON_JOB_ID;
	protected static final String NETWORK_IDS = "networkids";
	
	private static final int LIMIT_TYPE_INSTANCES = 0;
	private static final int LIMIT_TYPE_MEMORY = 9;
	private static final int LIMIT_TYPE_CPU = 8;
	protected static final String ZERO_DYNAMIC_DISK_OFFERING = "0";

	private static final String DEFAULT_HYPERVISOR = "KVM";
	private static final String DEFAULT_OS_TYPE_NAME = "Other (64-bit)";
	private static final String DEFAULT_EXPUNGE_ON_DESTROY = "true";
	
	private Properties properties;
	private HttpClientWrapper httpClient;
	private String endpoint;
	private String zoneId;
	private String imageDownloadBaseUrl;
	private String imageDownloadBasePath;
	private String hypervisor;
	private String osTypeId;
	private String expungeOnDestroy;
	private String defaultNetworkId;

	public CloudStackComputePlugin(Properties properties) {
		this(properties, new HttpClientWrapper());
	}
	
	public CloudStackComputePlugin(Properties properties, HttpClientWrapper httpClient) {
		this.properties = properties;
		this.httpClient = httpClient;
		this.endpoint = this.properties.getProperty("compute_cloudstack_api_url");
		this.zoneId = this.properties.getProperty("compute_cloudstack_zone_id");
		this.imageDownloadBaseUrl = this.properties.getProperty("compute_cloudstack_image_download_base_url");
		this.imageDownloadBasePath = this.properties.getProperty("compute_cloudstack_image_download_base_path");
		String hypervisorType = this.properties.getProperty("compute_cloudstack_hypervisor");
		this.hypervisor = hypervisorType == null ? DEFAULT_HYPERVISOR : hypervisorType;
		this.osTypeId = this.properties.getProperty("compute_cloudstack_image_download_os_type_id");
		this.expungeOnDestroy = this.properties.getProperty(
				"compute_cloudstack_expunge_on_destroy", DEFAULT_EXPUNGE_ON_DESTROY);
		this.defaultNetworkId = this.properties.getProperty(COMPUTE_CLOUDSTACK_DEFAULT_NETWORKID);
	}
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		
		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt);

		if (imageId == null) {
			LOGGER.error("Local image id must be specified.");
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		if (zoneId == null) {
			LOGGER.error("Default zone id must be specified.");
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		categories.remove(new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS));
		
		URIBuilder uriBuilder = createURIBuilder(endpoint, DEPLOY_VM_COMMAND);
		uriBuilder.addParameter(TEMPLATE_ID, imageId);
		uriBuilder.addParameter(ZONE_ID, zoneId);
		
		String requirements = xOCCIAtt.get(OrderAttribute.REQUIREMENTS.getValue());
		Flavor serviceOffering = getServiceOffering(token, requirements);
		String serviceOfferingId = null;
		if (serviceOffering != null) {
			serviceOfferingId = serviceOffering.getId();
		}
				
		uriBuilder.addParameter(SERVICE_OFFERING_ID, serviceOfferingId);
		String userdata = xOCCIAtt.get(OrderAttribute.USER_DATA_ATT.getValue());
		if (userdata != null) {
			uriBuilder.addParameter(USERDATA, userdata);
		}
		
		if (requirements!= null && requirements.contains(RequirementsHelper.GLUE_DISK_TERM)) {
			Flavor diskOffering = getDiskOfferingId(token, requirements);
			String diskOfferingId = diskOffering != null ? diskOffering.getId() : null;		
			boolean isDynamicDiskOffering = diskOffering.getDisk().equals(ZERO_DYNAMIC_DISK_OFFERING);
			if (diskOfferingId != null && !diskOfferingId.isEmpty() && !isDynamicDiskOffering) { 
				uriBuilder.addParameter(DISK_OFFERING_ID, diskOfferingId);
			}			
		}
		
		String networId = xOCCIAtt.get(OrderAttribute.NETWORK_ID.getValue());		
		if(networId == null || networId.isEmpty()){
			if (this.defaultNetworkId == null || this.defaultNetworkId.isEmpty()) {
				throw new OCCIException(ErrorType.BAD_REQUEST, DEFAULT_NETWORK_ID_IS_EMPTY);
			}
			networId = defaultNetworkId;
		}
		uriBuilder.addParameter(NETWORK_IDS, networId);
		
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doPost(uriBuilder.toString());
		checkStatusResponse(response);
		try {
			JSONObject vm = new JSONObject(response.getContent()).optJSONObject(
					"deployvirtualmachineresponse");
			return vm.optString("id");
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	private Flavor getServiceOffering(Token token, String requirements) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_SERVICE_OFFERINGS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response);
		List<Flavor> flavours = new LinkedList<Flavor>();
		try {
			JSONArray jsonOfferings = new JSONObject(response.getContent()).optJSONObject(
					"listserviceofferingsresponse").optJSONArray("serviceoffering");
			for (int i = 0; jsonOfferings != null && i < jsonOfferings.length(); i++) {
				JSONObject jsonOffering = jsonOfferings.optJSONObject(i);
				flavours.add(new Flavor(jsonOffering.optString(NAME),
						jsonOffering.optString("id"),
						jsonOffering.optString("cpunumber"), 
						jsonOffering.optString("memory"), 
						RequirementsHelper.VALUE_IGNORED));
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		return RequirementsHelper.findSmallestFlavor(flavours, requirements);
	}
	
	private Flavor getDiskOfferingId(Token token, String requirements) {
		URIBuilder uriBuilder = createURIBuilder(this.endpoint, LIST_DISK_OFFERINGS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = this.httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response);
		List<Flavor> flavours = new LinkedList<Flavor>();
		try {
			JSONArray jsonOfferings = new JSONObject(response.getContent()).optJSONObject(
					"listdiskofferingsresponse").optJSONArray("diskoffering");
			for (int i = 0; jsonOfferings != null && i < jsonOfferings.length(); i++) {
				JSONObject jsonOffering = jsonOfferings.optJSONObject(i);
				flavours.add(new Flavor(jsonOffering.optString(NAME), 
						jsonOffering.optString("id"), RequirementsHelper.VALUE_IGNORED,
						RequirementsHelper.VALUE_IGNORED, jsonOffering.optString("disksize")));
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		return RequirementsHelper.findSmallestFlavor(flavours, requirements);
	}
	
	@Override
	public List<Instance> getInstances(Token token) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_VMS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response);
		List<Instance> instances = new LinkedList<Instance>();
		try {
			JSONArray jsonVms = new JSONObject(response.getContent()).optJSONObject(
					"listvirtualmachinesresponse").optJSONArray("virtualmachine");			
			for (int i = 0; jsonVms != null && i < jsonVms.length(); i++) {
				JSONObject instanceJson = jsonVms.optJSONObject(i);
				instances.add(mountInstance(instanceJson));
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_VMS_COMMAND);
		uriBuilder.addParameter(VM_ID, instanceId);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response);
		JSONObject instanceJson = null;
		try {
			JSONArray instancesJson = new JSONObject(response.getContent()).optJSONObject(
					"listvirtualmachinesresponse").optJSONArray("virtualmachine");
			if (instancesJson == null || instancesJson.length() == 0) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			instanceJson = instancesJson.optJSONObject(0);
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		return mountInstance(instanceJson);
	}

	private Instance mountInstance(JSONObject instanceJson) {
		Map<String, String> attributes = new HashMap<String, String>();
		
		InstanceState state = getInstanceState(instanceJson.optString("state"));
		attributes.put("occi.compute.state", state.getOcciState());
		attributes.put("occi.compute.speed", instanceJson.optString("cpuspeed"));
		attributes.put("occi.compute.architecture", "Not defined");
		attributes.put("occi.compute.memory", String.valueOf(instanceJson.optDouble("memory") / 1024)); // Gb
		attributes.put("occi.compute.cores", instanceJson.optString("cpunumber"));
		attributes.put("occi.compute.hostname", instanceJson.optString("hostname"));
		
		String id = instanceJson.optString(VM_ID);
		attributes.put("occi.core.id", id);
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		
		String serviceOfferingName = instanceJson.optString("serviceofferingname");
		resources.add(ResourceRepository.generateFlavorResource(serviceOfferingName));
		
		return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), state);
	}

	private InstanceState getInstanceState(String vmState) {
		if ("Running".equalsIgnoreCase(vmState)) {
			return InstanceState.RUNNING;
		}
		if ("Shutdowned".equalsIgnoreCase(vmState)) {
			return InstanceState.SUSPENDED;
		}
		if ("Error".equalsIgnoreCase(vmState)) {
			return InstanceState.FAILED;
		}
		return InstanceState.PENDING;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, DESTROY_VM_COMMAND);
		uriBuilder.addParameter(VM_ID, instanceId);
		uriBuilder.addParameter(VM_EXPUNGE, expungeOnDestroy);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		httpClient.doPost(uriBuilder.toString());
	}

	@Override
	public void removeInstances(Token token) {
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			try {
				removeInstance(token, instance.getId());				
			} catch (Exception e) {
				LOGGER.warn("Does not possible delete compute instance: " + instance.getId() , e);
			}
		}
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_RESOURCE_LIMITS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response);
		
		int instancesQuota = 0;
		int cpuQuota = 0;
		int memQuota = 0;
		
		try {
			JSONArray limitsJson = new JSONObject(response.getContent()).optJSONObject(
					"listresourcelimitsresponse").optJSONArray("resourcelimit");
			for (int i = 0; limitsJson != null && i < limitsJson.length(); i++) {
				JSONObject limit = limitsJson.optJSONObject(i);
				int max = limit.optInt("max") < 0 ? Integer.MAX_VALUE : limit.optInt("max");
				int capacityType = limit.optInt("resourcetype");
				switch (capacityType) {
				case LIMIT_TYPE_INSTANCES:
					instancesQuota = max;
					break;
				case LIMIT_TYPE_CPU:
					cpuQuota = max;
					break;
				case LIMIT_TYPE_MEMORY:
					memQuota = max;
					break;
				default:
					break;
				}
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		List<Instance> instances = getInstances(token);
		Integer cpuInUse = 0;
		Integer memInUse = 0;
		Integer instancesInUse = instances.size();
		for (Instance instance : instances) {
			cpuInUse += Integer.valueOf(
					instance.getAttributes().get("occi.compute.cores"));
			memInUse += (int) (Double.valueOf(
					instance.getAttributes().get("occi.compute.memory")) * 1024);
		}
		
		ResourcesInfo resInfo = new ResourcesInfo(
				String.valueOf(cpuQuota - cpuInUse), cpuInUse.toString(), 
				String.valueOf(memQuota - memInUse), memInUse.toString(), 
				String.valueOf(instancesQuota - instancesInUse), instancesInUse.toString());
		return resInfo;
	}

	@Override
	public void bypass(Request request, Response response) {
		response.setStatus(new Status(HttpStatus.SC_BAD_REQUEST),
				ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName,
			String diskFormat) {
		if (imagePath.indexOf(imageDownloadBasePath) != 0) {
			LOGGER.warn("Image path is not relative to the base path.");
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		String imageURL = imagePath.replace(imageDownloadBasePath, imageDownloadBaseUrl + "/");
		URIBuilder uriBuilder = createURIBuilder(endpoint, REGISTER_TEMPLATE_COMMAND);
		uriBuilder.addParameter(DISPLAY_TEXT, imageName);
		uriBuilder.addParameter(FORMAT, diskFormat.toUpperCase());
		uriBuilder.addParameter(HYPERVISOR, hypervisor);
		uriBuilder.addParameter(NAME, imageName);
		uriBuilder.addParameter(OS_TYPE_ID, getOSTypeId(token));
		uriBuilder.addParameter(ZONE_ID, zoneId);
		uriBuilder.addParameter(URL, imageURL);
		uriBuilder.addParameter(IS_PUBLIC, Boolean.TRUE.toString());
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doPost(uriBuilder.toString());
		checkStatusResponse(response);
	}

	@Override
	public String getImageId(Token token, String imageName) {
		JSONObject template = getTemplateByName(token, imageName);
		return template == null ? null : template.optString("id");
	}
	
	@Override
	public ImageState getImageState(Token token, String imageName) {
		JSONObject template = getTemplateByName(token, imageName);
		if (template == null) {
			return null;
		}
		boolean isReady = template.optBoolean("isready");
		if (isReady) {
			return ImageState.ACTIVE;
		}
		return ImageState.PENDING;
	}

	private JSONObject getTemplateByName(Token token, String templateName) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_TEMPLATES_COMMAND);
		uriBuilder.addParameter(TEMPLATE_FILTER, "executable");
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());	
		try {
			JSONArray jsonTemplates = new JSONObject(response.getContent()).optJSONObject(
					"listtemplatesresponse").optJSONArray("template");
			for (int i = 0; jsonTemplates != null && i < jsonTemplates.length(); i++) {
				JSONObject template = jsonTemplates.optJSONObject(i);
				if (template.optString(NAME).equals(templateName)) {
					return template;
				}
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		return null;
	}
	
	private String getOSTypeId(Token token) {
		if (osTypeId != null) {
			return osTypeId;
		}
		
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_OS_TYPES_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response);
		try {
			JSONArray jsonOsTypes = new JSONObject(response.getContent()).optJSONObject(
					"listostypesresponse").optJSONArray("ostype");
			for (int i = 0; jsonOsTypes != null && i < jsonOsTypes.length(); i++) {
				JSONObject osType = jsonOsTypes.optJSONObject(i);
				if (osType.optString("description").equals(DEFAULT_OS_TYPE_NAME)) {
					return osType.optString("id");
				}
			}
			if (jsonOsTypes != null) {
				return jsonOsTypes.optJSONObject(0).optString("id");
			}
			
			LOGGER.warn("No OS type has been defined and default value "
					+ DEFAULT_OS_TYPE_NAME + " wansn't foud.");
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}
	
	protected static URIBuilder createURIBuilder(String endpoint, String command) {
		try {
			URIBuilder uriBuilder = new URIBuilder(endpoint);
			uriBuilder.addParameter(COMMAND, command);
			return uriBuilder;
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}
	
	private static final int SC_PARAM_ERROR = 431;
    private static final int SC_INSUFFICIENT_CAPACITY_ERROR = 533;
	private static final int SC_RESOURCE_UNAVAILABLE_ERROR = 534;
	private static final int SC_RESOURCE_ALLOCATION_ERROR = 535;

	protected void checkStatusResponse(HttpResponseWrapper response) {
		StatusLine statusLine = response.getStatusLine();
		String content = response.getContent();
		if (statusLine.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			LOGGER.error(content);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (statusLine.getStatusCode() == SC_PARAM_ERROR) {
			LOGGER.error(content);
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} else if (statusLine.getStatusCode() == SC_INSUFFICIENT_CAPACITY_ERROR || 
				statusLine.getStatusCode() == SC_RESOURCE_UNAVAILABLE_ERROR) {
			LOGGER.error(content);
			throw new OCCIException(ErrorType.NO_VALID_HOST_FOUND, ResponseConstants.NO_VALID_HOST_FOUND);
		} else if (statusLine.getStatusCode() == SC_RESOURCE_ALLOCATION_ERROR) {
			LOGGER.error(content);
			throw new OCCIException(ErrorType.QUOTA_EXCEEDED, 
					ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
		} else if (statusLine.getStatusCode() > 204) {
			LOGGER.error(content);
			throw new OCCIException(ErrorType.BAD_REQUEST, statusLine.getReasonPhrase());
		}
	}

	@Override
	public String attach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String storageId = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		
		URIBuilder uriBuilder = createURIBuilder(endpoint, ATTACH_VOLUME_COMMAND);
		uriBuilder.addParameter(ATTACH_VOLUME_ID, storageId);
		uriBuilder.addParameter(ATTACH_VM_ID, instanceId);
		
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response);
		try {
			JSONObject responseJson = new JSONObject(response.getContent())
				.optJSONObject("attachvolumeresponse");
			JSONObject asyncJobStatus = getAsyncJobStatus(responseJson, token);
			int jobStatus = asyncJobStatus.optInt(JSON_JOB_STATUS);
			if (jobStatus == 1) {
				String deviceId = asyncJobStatus.optJSONObject("jobresult").optJSONObject("volume").optString("deviceid");
				return UUID.randomUUID().toString() + "-device-" + deviceId;
			}
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Could not attach disk. CloudStack job status: " + jobStatus);
		} catch (JSONException e) {
			LOGGER.debug("Could not attach volume " + storageId + ". " + response.getContent());
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
	}
	
	// FIXME This solution is temporary. We need investigate the behavior of the cloudstack. Implement other solution.
	protected JSONObject getAsyncJobStatus(JSONObject responseJson, Token token) {
		if (responseJson.has(JSON_JOB_ID)) {
			JSONObject asyncJobResponse = null;
			HttpResponseWrapper response = null;			
			final int statusComplete = 1;
			final int statusFailure = 2;			
			URIBuilder uriBuilder = createURIBuilder(endpoint, QUERY_ASYNC_JOB_RESULT);
			uriBuilder.addParameter(JOB_ID, responseJson.optString(JSON_JOB_ID));
			CloudStackHelper.sign(uriBuilder, token.getAccessId());
			
			long elapsedTime = 0;
			final long asyncJobStatusDelay = getAsyncJobStatusDelay();			
			LOGGER.debug("Trying get CloudStack asyn job status within " + asyncJobStatusDelay + " milliseconds.");
			final long startCount = System.currentTimeMillis();
			while(elapsedTime <= asyncJobStatusDelay) {
				
				response = httpClient.doGet(uriBuilder.toString());	
				try {
					checkStatusResponse(response);
					// jobstatus 0 = still processing, 1 complete, 2 failure
					asyncJobResponse = new JSONObject(response.getContent())
							.optJSONObject(JSON_QUERY_ASYNC_JOB_RESULT_RESPONSE);
					int jobStatus = asyncJobResponse.optInt(JSON_JOB_STATUS);
					if (jobStatus == statusComplete || jobStatus == statusFailure) {
						LOGGER.debug("CloudStack asyn job status: " + asyncJobResponse.toString());
						return asyncJobResponse;
					}
				} catch (Exception e) {
					LOGGER.debug("Could not get async job status.", e);
				}
				
				// Wait 1 second
				try { Thread.sleep(1000); } catch (InterruptedException e1) {}
				elapsedTime = System.currentTimeMillis() - startCount;
			}
			return asyncJobResponse;
		}
		return null;
	}

	protected long getAsyncJobStatusDelay() {
		long xmppTimeout = MainHelper.getXMPPTimeout(this.properties);
		// to prevent timeout
		int decreaseTime = 2000;
		return xmppTimeout - decreaseTime;
	}

	@Override
	public void dettach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String storageId = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		
		URIBuilder uriBuilder = createURIBuilder(endpoint, DETACH_VOLUME_COMMAND);
		uriBuilder.addParameter(ATTACH_VOLUME_ID, storageId);
		
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response);
		try {
			JSONObject responseJson = new JSONObject(
					response.getContent()).optJSONObject("detachvolumeresponse");
			JSONObject asyncJobStatus = getAsyncJobStatus(responseJson, token);
			int jobStatus = asyncJobStatus.optInt(JSON_JOB_STATUS);
			if (jobStatus != 1) {
				String errorText = "Async job is not complete yet.";
				if (jobStatus == 2) {
					errorText = asyncJobStatus.optJSONObject("jobresult").optString("errortext");
				}
				throw new OCCIException(ErrorType.BAD_REQUEST, 
						"Could not detach disk. " + errorText);
			}
		} catch (JSONException e) {
			LOGGER.debug("Could not detach volume " + storageId + ". " + e.getMessage());
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getContent());
		}
	}
	
}
