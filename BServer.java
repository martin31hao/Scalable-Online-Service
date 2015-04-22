import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;


/*
 * BServer:
 * App Server does the following things:
 * 		1. Pull request from Front master
 * 		2. Kill itself by RMI, if receive yes from server, then scale in
 * 	
 */
public class BServer extends UnicastRemoteObject implements BServerIf{
	/**
	 * 
	 */
	private static final long serialVersionUID = -7293142569312979890L;

	private ServerLib SL;
	
	private RequestPacket nowReq = null;
	
	private boolean toKill = false;
	
	private FrontMasterIf FM = null;
	
	private int uid;
	
	private Cloud.DatabaseOps cache = null;
	
	public BServer(ServerLib SL, String addr, int port, int id, int serverType)
		throws RemoteException {
		this.SL = SL;
		this.uid = id;
		cache = getCacheInstance(addr, port);
		FM = getFMInstance(addr, port);
		this.nowReq = null;
	}
	
	/*
	 * The main thread to pull new requests from front master 
	 */
	public void run() {
		
		System.out.println("VM " + uid + " Register success");
		Integer UID = new Integer(uid);
		
		/*
		 *  A list containing consecutive 5 waiting time to get a new request,
		 *  which is used to decide whether scale in itself
		 */
		LinkedList<Long> waitTime = new LinkedList<Long>();
		long totalWaitTime = 0;
		int cnt = 5;
		
		while (toKill == false) {
			try {
				nowReq = FM._appAvailable(UID);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (this.nowReq != null) {
				
				long curTime = System.currentTimeMillis();
				if (waitTime.size() < cnt) {
					waitTime.add(curTime);
				} else {
					for (int i = 1; i < cnt; i++) {
						totalWaitTime = waitTime.get(i) - waitTime.get(i-1);
					}
					totalWaitTime += curTime - waitTime.get(cnt-1);
					waitTime.removeFirst();
					waitTime.addLast(curTime);
					// If 5 consecutive waiting time exceeds 3000ms, then try scale in
					if (totalWaitTime > 3000) { 
						try {
							if (FM._scaleInApp(uid)) {
								break;
							} else {
								waitTime.clear();
							}
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}

				if (nowReq.r.isPurchase) {
					// If the purchase request has been issued more than 1400ms, drop it 
					if (curTime - nowReq.getTime > 1400) {
						SL.drop(nowReq.r);
					} else {
						SL.processRequest(nowReq.r, cache);
					}
				} else {
					// If the browse request has been issued more than 400ms, drop it
					if (curTime - nowReq.getTime > 400) {
						SL.drop(nowReq.r);
					} else {
						SL.processRequest(nowReq.r, cache);
					}
				}
				
			} else {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		System.out.println("VM " + uid + " Killed");
		try {
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
			e.printStackTrace();
		}
		SL.shutDown();
	}
	
	/*
	 * RMI call for BMaster to kill the server
	 */
	@Override
	public void _kill() throws RemoteException{
		toKill = true;
	}
	
	/*
	 * Get front master instance 
	 */
	public FrontMasterIf getFMInstance(String addr, int port) {
		String url = null;
		url = String.format("//%s:%d/ServerService", addr, port);
	    try {
	      return (FrontMasterIf) Naming.lookup(url);
	    } catch (MalformedURLException e) {
	      //you probably want to do logging more properly
	      System.err.println("Bad URL" + e);
	    } catch (RemoteException e) {
	      System.err.println("Remote connection refused to url "+ url + " " + e);
	    } catch (NotBoundException e) {
	      System.err.println("Not bound " + e);
	    }
	    return null;
  	}
	
	/*
	 *  Get cache instance
	 */
	public Cloud.DatabaseOps getCacheInstance(String addr, int port) {
	    String url = String.format("//%s:%d/CacheService", addr, port);
	    try {
	      return (Cloud.DatabaseOps) Naming.lookup(url);
	    } catch (MalformedURLException e) {
	      //you probably want to do logging more properly
	      System.err.println("Bad URL" + e);
	    } catch (RemoteException e) {
	      System.err.println("Remote connection refused to url "+ url + " " + e);
	    } catch (NotBoundException e) {
	      System.err.println("Not bound " + e);
	    }
	    return null;
  	}
	
}
