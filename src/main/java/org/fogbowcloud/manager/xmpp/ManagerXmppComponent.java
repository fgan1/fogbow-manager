package org.fogbowcloud.manager.xmpp;

import java.util.Timer;
import java.util.TimerTask;

import org.dom4j.Element;
import org.fogbowcloud.manager.xmpp.core.ManagerFacade;
import org.fogbowcloud.manager.xmpp.core.ManagerModel;
import org.jamppa.component.XMPPComponent;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;

public class ManagerXmppComponent extends XMPPComponent {

	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";
	private static long PERIOD = 100;
	private ManagerFacade managerFacade;
	private final Timer timer = new Timer();
	private String rendezvousAdress;

	public ManagerXmppComponent(String jid, String password, String server,
			int port) {
		super(jid, password, server, port);
		managerFacade = new ManagerFacade(new ManagerModel());
	}

	@Override
	public void connect() throws ComponentException {
		super.connect();
	}

	public void init() {
		callIamAlive();
	}
	
	//TODO complete with calls from openstack
	public void iAmAlive() {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAdress);
		iq.setFrom(getJID());
		Element statusEl = iq.getElement()
				.addElement("query", IAMALIVE_NAMESPACE).addElement("status");
		statusEl.addElement("cpu-idle").setText("value1");
		statusEl.addElement("cpu-inuse").setText("value2");
		statusEl.addElement("mem-idle").setText("value3");
		statusEl.addElement("mem-inuse").setText("value4");
		this.syncSendPacket(iq);
	}

	public void whoIsalive() {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAdress);
		
		iq.getElement().addElement("query", WHOISALIVE_NAMESPACE);
		IQ response = (IQ) this.syncSendPacket(iq);
		managerFacade.getItemsFromIQ(response);
	}

	private void callIamAlive() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				iAmAlive();
				whoIsalive();
			}
		}, 0, PERIOD);
	}

	public void setRendezvousAdress(String adress) {
		rendezvousAdress = adress;
	}

	public ManagerFacade getManagerFacade() {
		return managerFacade;
	}

}
