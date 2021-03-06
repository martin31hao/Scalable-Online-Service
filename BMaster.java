import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;

/*
 * BMaster:
 * Browse master does the following things:
 * 		1. Tell server to start another BServer when needed
 * 		2. Get registered with a new BServer
 * 		3. Kill a BServer when necessary
 * 		4. Maintain a browse request queue received from front server
 * 		5. Decide when to scale out and scale in based on the queue
 * 	
 */
class BMaster extends UnicastRemoteObject implements BMasterIf {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2171551605351261999L;
	private ServerLib SL;
	private LinkedList<RequestPacket> requestQueue;
	private LinkedList<BServerIf> BServerList;
	
	private int timeout;
	
	private double scaleOutThreshold = 1;
	private double scaleInThreshold = 1;
	
	private FrontMasterIf fmif = null;
	
	private int serverType;

	private String addr;
	private int port;
	
	private long timeNow = -1;
	private long timeLast = -1;
	private long timeMean = -1;
	
	private int serverSize = 1;
	
	public BMaster(ServerLib SL, String addr, int port, int serverType) 
		throws RemoteException {
		this.SL = SL;
		requestQueue = new LinkedList<RequestPacket>();
		BServerList = new LinkedList<BServerIf>();
		fmif = getFrontMasterInstance(addr, port);
		this.serverType = serverType;
		this.addr = addr;
		this.port = port;
		
		if (serverType == Server.BROWSESERVER) {
			timeout = 1000;
			scaleOutThreshold = 1.5;
			scaleInThreshold = 1;
		} else {
			timeout = 2000;
			scaleOutThreshold = 3;
		}
		
		// running checking scale out in another thread
		//new Thread(new CheckScaleOut()).start();
		//new Thread(new CheckScaleIn()).start();
	}
	
	/*
	 * Process the request as queue is not empty
	 */
	public void run() {
		while (true) {
			Cloud.FrontEndOps.Request r = getRequest();
			if (r != null) {
				SL.processRequest(r);
			} else {
				while (requestQueue.size() == 0) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	public synchronized void dropQueue(int size) {
		//int size = Math.max(0, requestQueue.size() - BServerList.size() - 1);
		//if (size <= BServerList.size() / 2)	return;
		//if (half)
		//	size -= BServerList.size() / 2;
		while (size-- > 0) {
			RequestPacket rp = requestQueue.get(0);
			System.out.println("Ready to drop " + requestQueue.size() + " request");
			SL.drop(rp.r);
			requestQueue.remove(0);
			/*try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
		}
	}
	
	/*
	 * Decide to scale out or scale in
	 */
	public synchronized void scaleOut() {
		System.out.println("Request size: " + requestQueue.size());
		
		if (toScaleOut()) {
			System.out.println("Ready to scale out with " + requestQueue.size() + " requests");
			System.out.println("Ready to scale out with " + BServerList.size() + " servers");
			//int scaleSize = Math.max(1, requestQueue.size() - BServerList.size());
			//System.out.println("Scale " + scaleSize + " machines!");
			startBServer();//Math.max(2, scaleSize));
			this.serverSize++;
			//serverSize += scaleSize;
			dropQueue(Math.max(0, requestQueue.size() - BServerList.size()));
			return;
		}
		
		//if (requestQueue.size() > BServerList.size() + 1) {// && serverSize > BServerList.size() + 1) {
		//	dropQueue(Math.max(0, requestQueue.size() - BServerList.size() - 1 - ((serverSize - BServerList.size() - 1) / 2)));
		//}
		//if (BServerList.size() > 4 && requestQueue.size() > Math.max(2, this.BServerList.size() / 3)) {
		//	dropQueue(false);
		//}
	}
	
	public void scaleIn() {
		if (toScaleIn()) {
			shutBServer();
		}
	}
	
	/*
	 * Scale out when there happens that it meets requirement for two consecutive times
	 */
	public boolean toScaleOut() {
		int tryScale = 1;
		while (tryScale-- > 0) {
			//double thresh = scaleOutThreshold * (BServerList.size() + 1);
			//thresh = Math.max(2, thresh);
			if (requestQueue.size() > BServerList.size() + 3) {//serverSize) {//>= Math.max(3, serverSize)/*Math.min(3.99, thresh)*/) {//Math.max(2, thresh - 15)) {
				return true;
			} else {
				return false;
			}
		}
		return true;
	}
	
	/*
	 * Scale in when there happens it meets requirement for three consecutive times
	 */
	public synchronized boolean toScaleIn() {
		// If the list size is 0, no need to scale in
		if (BServerList.size() < 1)	return false;
		
		int tryScale = 3;
		while (tryScale-- > 0) {
			if (requestQueue.size() < BServerList.size() + 1) {//scaleInThreshold * (BServerList.size() + 1)) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				return false;
			}
		}
		return true;
	}
	
	/*
	 * Start front server when needed
	 */
	public void startBServer() {
		//for (int i = 0; i < num; i++) {
			SL.startVM();
			try {
				fmif._addServer(serverType);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		//}
	}
	
	/*
	 * Shut down front server when needed
	 */
	public synchronized void shutBServer() {
		// kill server of the first element in the list 
		try {
			if (BServerList.size() > 0) {
				BServerList.get(0)._kill();
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (BServerList.size() > 0)
			BServerList.remove(0);
	}
	
	/*
	 * Local call to get next request
	 */
	public synchronized Cloud.FrontEndOps.Request getRequest() {
		if (requestQueue.size() > 0) {
			return requestQueue.poll().r;
		}
		return  null;
	}
	
	/*
	 * RMI call to get request from BMServer
	 */
	@Override
	public synchronized Cloud.FrontEndOps.Request _getRequest() throws RemoteException {
		if (requestQueue.size() > 0) {
			return requestQueue.poll().r;
		}
		return null;
	}
	
	/*
	 * RMI call to get request from front server and add it to queue
	 */
	@Override
	public synchronized void _sendRequest(RequestPacket rp) throws RemoteException {
		// record time spec of first coming ranges, and decide how many to scale 
		requestQueue.add(rp);
		
		if (rp.r.isPurchase)	return;
		
		scaleOut();
		//scaleIn();
	}

	/*
	 * RMI call for BServer to get registered to BMaster
	 */
	@Override
	public synchronized void _getBServer(String regName, String addr, int port) throws RemoteException {
		String url = String.format("//%s:%d/%s", addr, port, regName);
	    try {
	       BServerIf bm = (BServerIf) Naming.lookup(url);
	       if (bm != null) {
	    	   synchronized (BMaster.class) {
	    		   BServerList.add(bm);
	    	   }
	       }
	    } catch (MalformedURLException e) {
	      //you probably want to do logging more properly
	      System.err.println("Bad URL" + e);
	    } catch (RemoteException e) {
	      System.err.println("Remote connection refused to url "+ url + " " + e);
	    } catch (NotBoundException e) {
	      System.err.println("Not bound " + e);
	    }
	}	
	
	// Get front master instance
	public FrontMasterIf getFrontMasterInstance(String addr, int port) {
	    String url = String.format("//%s:%d/ServerService", addr, port);
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
	
	// Get front master instance
	public FrontServerIf getFrontServerInstance(String addr, int port, int id) {
	    String url = String.format("//%s:%d/%d", addr, port, id);
	    try {
	      return (FrontServerIf) Naming.lookup(url);
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
