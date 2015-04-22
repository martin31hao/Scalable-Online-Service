import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;


public class Cache extends UnicastRemoteObject implements Cloud.DatabaseOps, java.io.Serializable{

	// In-memory cache
	public HashMap<String, String> cache;
	
	// Default database
	private Cloud.DatabaseOps db;
	
	public Cache(Cloud.DatabaseOps database) throws RemoteException {
		cache = new HashMap<String, String>();
		System.out.println("Cache initialized");
		this.db = database;
	}
	
	/*
	 * Update cache when a transaction has been made
	 */
	public void updateCache(String item, float price, int qty) {
		
		// update item qty if it is in in-memory cache
		StringBuilder sb = new StringBuilder(item.trim());
		sb.append("_qty");
		if (cache.containsKey(sb.toString())) {
			synchronized (cache) {
				int newQty = Integer.parseInt(cache.get(sb.toString())) - qty;
				cache.put(sb.toString(), String.valueOf(newQty));
			}
		}
	}
	
	/*
	 * Pass through the function directly to database if local cache doesn't contain the item
	 * Otherwise, retrieve the value from local cache
	 */
	@Override
	public String get(String key) throws RemoteException {
		String value = cache.get(key);
		if (value != null) {
			return value;
		} else {
			String val = db.get(key);
			cache.put(key, val);
			return val;
		}
	}

	/*
	 * Pass through the function directly to database
	 * If transaction has been made, then update cache.
	 */
	@Override
	public boolean transaction(String item, float price, int qty)
			throws RemoteException {
		// TODO Auto-generated method stub
		boolean transacSuccess = db.transaction(item, price, qty);
		if (transacSuccess) {
			updateCache(item, price, qty);
		}
		return transacSuccess;
	}

	/*
	 * Pass through the function directly to database
	 */
	@Override
	public boolean set(String arg0, String arg1, String arg2)
			throws RemoteException {
		// TODO Auto-generated method stub
		return db.set(arg0, arg1, arg2);
	}

}
