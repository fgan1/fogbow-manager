package org.fogbowcloud.manager.occi.storage;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetStorage {

	private static final String FAKE_POST_INSTANCE_HOST = "102.102.02.01";
	private static final String FAKE_POST_INSTANCE_PORT = "9001";
	private static final String INSTANCE_1_ID = "testOne";
	private static final String INSTANCE_2_ID = "testTwo";
	private static final String INSTANCE_3_ID_WITHOUT_USER = "testThree";
	private static final String USER_WITHOUT_ORDERS = "withoutInstances";
	private static final String ACCESS_TOKEN = "access_token";

	private StoragePlugin storagePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private OCCITestHelper helper;
	private MapperPlugin mapperPlugin;
	private ManagerController managerControler;

	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();
		
		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		Instance instance1 = new Instance(INSTANCE_1_ID, list, map, null, InstanceState.PENDING);

		Map<String, String> postMap = new HashMap<String, String>();
		postMap.put(Instance.SSH_PUBLIC_ADDRESS_ATT, FAKE_POST_INSTANCE_HOST+":"+FAKE_POST_INSTANCE_PORT);
		postMap.put("test", "test");

		
		storagePlugin = Mockito.mock(StoragePlugin.class);
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_1_ID)))
				.thenReturn(instance1);		
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_1_ID)))
				.thenReturn(instance1);
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_2_ID)))
				.thenReturn(new Instance(INSTANCE_2_ID));
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_3_ID_WITHOUT_USER)))
				.thenReturn(instance1);		

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(new Token("id", new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(), new HashMap<String, String>()));
		
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN))
		.thenReturn(new Token("id_two", new Token.User(USER_WITHOUT_ORDERS, ""), new Date(), new HashMap<String, String>()));		
		
		
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");

		List<Order> ordersA = new LinkedList<Order>();
		
		Token token = new Token(OCCITestHelper.ACCESS_TOKEN, new Token.User(OCCITestHelper.USER_MOCK, 
				OCCITestHelper.USER_MOCK), DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.STORAGE_TERM);
		Order orderOne = new Order("1", token, null, xOCCIAtt, true, "");
		orderOne.setInstanceId(INSTANCE_1_ID);
		orderOne.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(orderOne);
		Order orderTwo = new Order("2", token, null, xOCCIAtt, true, "");
		orderTwo.setInstanceId(INSTANCE_2_ID);
		orderTwo.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(orderTwo);
		Order orderThreeDiferentUser = new Order("3", new Token("token", new Token.User("userDiferent", ""), 
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, xOCCIAtt, true, "");
		orderThreeDiferentUser.setInstanceId(INSTANCE_3_ID_WITHOUT_USER);
		orderThreeDiferentUser.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(orderThreeDiferentUser);

		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(new HashMap<String, String>());

		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, ordersA);
		
		ordersToAdd.put(USER_WITHOUT_ORDERS, new ArrayList<Order>());		
		
		managerControler = this.helper.initializeComponentCompute(null, storagePlugin, identityPlugin, authorizationPlugin, null,
				Mockito.mock(AccountingPlugin.class), Mockito.mock(BenchmarkingPlugin.class), ordersToAdd,
				mapperPlugin);

	}

	@After
	public void tearDown() throws Exception {
		TestDataStorageHelper.clearManagerDataStore(
				this.managerControler.getManagerDataStoreController().getManagerDatabase());
		this.helper.stopComponent();
	}

	@Test
	public void testGetStorageOk() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		int amountUsersMock = 2;
		Assert.assertEquals(amountUsersMock, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetComputeOkAcceptURIList() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		int amountUsersMock = 2;
		Assert.assertEquals(amountUsersMock, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}

	@Test
	public void testEmptyGetComputeWithAcceptURIList() throws Exception {
		List<Order> orders = new LinkedList<Order>();
		
		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, orders);		

		// test
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}

	@Test
	public void testGetSpecificInstanceFound() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_1_ID + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceFoundWithWrongAccept() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_1_ID + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceNotFound() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE + "wrong@member");
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceOtherUser() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_3_ID_WITHOUT_USER
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDifferentContentType() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, "any");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testNotAllowedAcceptContent() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, "invalid-content");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testAccessToken() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
//		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
//				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
//		Assert.assertEquals(ResponseConstants.UNAUTHORIZED, EntityUtils.toString(response.getEntity()));
	}

	@Test
	public void testWithoutAccessToken() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
//		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue().startsWith("text/plain"));
//		Assert.assertEquals(ResponseConstants.UNAUTHORIZED, EntityUtils.toString(response.getEntity()));
	}

}
