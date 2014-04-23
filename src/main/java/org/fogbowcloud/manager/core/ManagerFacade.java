package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.jamppa.component.PacketSender;
import org.xmpp.packet.IQ;

public class ManagerFacade {

	private static final Logger LOGGER = Logger.getLogger(ManagerFacade.class);
	public static final long SCHEDULER_PERIOD = 30000;

	private boolean scheduled = false;
	private final Timer timer = new Timer();
	
	private List<FederationMember> members = new LinkedList<FederationMember>();
	private RequestRepository requests = new RequestRepository();
	private FederationMemberPicker memberPicker = new RoundRobinMemberPicker();

	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private Properties properties;
	private PacketSender packetSender;

	public ManagerFacade(Properties properties) {
		this.properties = properties;
		if (properties == null) {
			throw new IllegalArgumentException();
		}
	}
	
	public void setComputePlugin(ComputePlugin computePlugin) {
		this.computePlugin = computePlugin;
	}
	
	public void setIdentityPlugin(IdentityPlugin identityPlugin) {
		this.identityPlugin = identityPlugin;
	}

	public void updateMembers(List<FederationMember> members) {
		if (members == null) {
			throw new IllegalArgumentException();
		}
		this.members = members;
	}

	public List<FederationMember> getMembers() {
		return members;
	}

	public ResourcesInfo getResourcesInfo() {
		String token = identityPlugin.getToken(properties.getProperty("federation.user.name"),
				properties.getProperty("federation.user.password"));
		ResourcesInfo resourcesInfo = computePlugin.getResourcesInfo(token);
		resourcesInfo.setId(properties.getProperty("xmpp_jid"));
		return resourcesInfo;
	}

	public String getUser(String authToken) {
		return identityPlugin.getUser(authToken);
	}

	public List<Request> getRequestsFromUser(String authToken) {
		String user = getUser(authToken);
		return requests.getByUser(user);
	}

	public void removeAllRequests(String authToken) {
		String user = getUser(authToken);
		requests.removeByUser(user);
	}

	public void removeRequest(String authToken, String requestId) {
		checkRequestId(authToken, requestId);
		requests.remove(requestId);
	}

	private void checkRequestId(String authToken, String requestId) {
		String user = getUser(authToken);
		if (requests.get(user, requestId) == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	public List<Instance> getInstances(String authToken) {
		// TODO check other manager

		return this.computePlugin.getInstances(authToken);
	}

	public Instance getInstance(String authToken, String instanceId) {
		// TODO check other manager
		checkInstanceId(authToken, instanceId);
		return this.computePlugin.getInstance(authToken, instanceId);
	}

	public void removeInstances(String authToken) {
		// TODO check other manager

		this.computePlugin.removeInstances(authToken);
	}

	public void removeInstance(String authToken, String instanceId) {
		// TODO check other manager

		checkInstanceId(authToken, instanceId);
		this.computePlugin.removeInstance(authToken, instanceId);
	}

	private void checkInstanceId(String authToken, String instanceId) {
		String user = getUser(authToken);
		List<Request> userRequests = requests.getByUser(user);
		for (Request request : userRequests) {
			if (instanceId.equals(request.getInstanceId())) {
				return;
			}
		}
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}

	public Request getRequest(String authToken, String requestId) {
		checkRequestId(authToken, requestId);
		return requests.get(requestId);
	}

	public List<Request> createRequests(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String user = getUser(authToken);

		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT
				.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, authToken, "", RequestState.OPEN, categories,
					xOCCIAtt);
			currentRequests.add(request);
			requests.addRequest(user, request);
		}
		if (!scheduled) {
			scheduleRequests();
		}
		
		return currentRequests;
	}

	private boolean submitRemoteRequest(Request request) {
		FederationMember member = memberPicker.pick(getMembers());
		IQ iq = new IQ();
		iq.setTo(member.getResourcesInfo().getId());
		Element queryEl = iq.getElement().addElement("query", 
				ManagerXmppComponent.REQUEST_NAMESPACE);
		for (Category category : request.getCategories()) {
			Element categoryEl = queryEl.addElement("category");
			categoryEl.addElement("class").setText(category.getCatClass());
			categoryEl.addElement("term").setText(category.getTerm());
			categoryEl.addElement("scheme").setText(category.getScheme());
		}
		for (Entry<String, String> xOCCIEntry : request.getxOCCIAtt().entrySet()) {
			Element attributeEl = queryEl.addElement("attribute");
			attributeEl.addAttribute("var", xOCCIEntry.getKey());
			attributeEl.addElement("value").setText(xOCCIEntry.getValue());
		}
		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			return false;
		}
		request.setState(RequestState.FULFILLED);
		request.setInstanceId(response.getElement()
				.element("query")
				.element("instance")
				.elementText("id"));
		return true;
	}

	private boolean submitLocalRequest(Request request) {
		Map<String, String> xOCCIAtt = request.getxOCCIAtt();
		for (String keyAttributes : RequestAttribute.getValues()) {
			xOCCIAtt.remove(keyAttributes);
		}
		
		String instanceLocation = null;
		try {
			instanceLocation = computePlugin.requestInstance(request.getAuthToken(),
					request.getCategories(), xOCCIAtt);
		} catch (OCCIException e) {
			if (e.getStatus().equals(ErrorType.BAD_REQUEST)
					&& e.getMessage().contains(ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
				return false;
			} else {
				//TODO Think this through...
				request.setState(RequestState.FAILED);
				LOGGER.warn("Request failed locally for an unknown reason.", e);
				return true;
			}
		}
		
		instanceLocation = instanceLocation.replace(HeaderUtils.X_OCCI_LOCATION, "").trim();
		request.setInstanceId(instanceLocation);
		request.setState(RequestState.FULFILLED);
		return true;
	}

	private void scheduleRequests() {
		scheduled = true;
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkAndSubmitOpenRequests();
			}
		}, 0, SCHEDULER_PERIOD);
	}

	private void checkAndSubmitOpenRequests() {
		boolean allFulfilled = true;
		for (Request request : requests.get(RequestState.OPEN)) {
			allFulfilled &= submitLocalRequest(request) || submitRemoteRequest(request);
		}
		if (allFulfilled) {
			timer.cancel();
			scheduled = false;
		}
	}

	public void setPacketSender(PacketSender packetSender) {
		this.packetSender = packetSender;
	}
}
