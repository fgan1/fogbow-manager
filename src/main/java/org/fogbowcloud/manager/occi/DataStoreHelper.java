package org.fogbowcloud.manager.occi;

import java.io.File;


public class DataStoreHelper {
	
	protected static String dataStoreFolderExecution = null; 
	public static final String DATASTORES_FOLDER = "/datastores";
	protected static final String DATASTORES_TEST_FOLDER = "/datastores_test";
	protected static final String PREFIX_DATASTORE_URL = "jdbc:sqlite:";

	/**
	 * @param dataStoreUrl coming from manager properties.
	 * @param dataStoreName coming from each DataStore class.
	 */
	public static String getDataStoreUrl(String dataStoreUrl, String dataStoreName) {
		if (dataStoreUrl == null || dataStoreUrl.isEmpty()) {
			String pathFolderDatastores = getPathFolderDataStoresFogbowManager();
			File folderDataStores = new File(pathFolderDatastores);
			if (!folderDataStores.exists()) {
				folderDataStores.mkdir();
			}
			dataStoreUrl = PREFIX_DATASTORE_URL + pathFolderDatastores + "/" + dataStoreName;
		}		
		
		return dataStoreUrl;
	}

	/**
	 * DataStoreHelper.dataStoreFolderExecution is only determined in the Main.class.
	 * For main execution will used folder DATASTORES_FOLDER.
	 * For tests will used the folder DATASTORES_TEST_FOLDER.
	 */
	protected static String getPathFolderDataStoresFogbowManager() {
		String pathFogbowManager = System.getProperty("user.dir");
		String dataStoreFolderExecution = DATASTORES_TEST_FOLDER;
		if (DataStoreHelper.dataStoreFolderExecution != null) {
			dataStoreFolderExecution = DataStoreHelper.dataStoreFolderExecution;
		}		
		return pathFogbowManager + dataStoreFolderExecution;
	}
	
	public static void setDataStoreFolderExecution(String dataStoreFolderExecution) {
		if (DataStoreHelper.dataStoreFolderExecution == null) {
			DataStoreHelper.dataStoreFolderExecution = dataStoreFolderExecution;			
		}
	}
	
}
