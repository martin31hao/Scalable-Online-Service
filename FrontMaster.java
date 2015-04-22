import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/*
 * FrontMaster:
 * Front master does the following things:
 * 		1. Collects requests from other frontier servers
 * 		2. Assign roles to all the incoming servers, _getRole
 * 		3. Scale out on the front servers, scaleOut the app servers
 * 	
 * Note: This server is also the master server for all
 */
public class FrontMaster extends UnicastRemoteObject implements FrontMasterIf{
	/**
	 * 
	 */
	private static final long serialVersionUID = -2685316105835292212L;

	private final int FRONTSERVER = 0;
	
	// Queue of types of servers that will be assigned roles
	private LinkedList<Integer> serverQueue;
	// List of uid of frontier servers besides master
	private List<Integer> frontServers;
	// List of uid of app servers
	private ArrayList<Integer> appServers;
	
	private ServerLib SL;
	
	private static int uid = 1000; // uid for assigning to other servers
	
	// List of available app servers to accept new requests
	private LinkedList<Integer> readyAppServers;
	
	// Request queue from all frontier servers
	private LinkedList<RequestPacket> requests;
	
	private long initTime = 0;
	
	public FrontMaster(ServerLib SL, String addr, int port) throws RemoteException {
		this.SL = SL;
		serverQueue = new LinkedList<Integer>();
		frontServers = new ArrayList<Integer>();
		appServers = new ArrayList<Integer>();
		
		requests = new LinkedList<RequestPacket>();
		readyAppServers = new LinkedList<Integer>();
		
		// running checking scale out in another thread
		new Thread(new CheckScaleApp()).start();
		new Thread(new GetReq()).start();
		new Thread(new CheckScaleFront()).start();
		
		// register with load balancer so requests are sent to this server
        this.SL.register_frontend();
        
        initTime = System.currentTimeMillis();
	}
	
	/*
	 * Main thread to notify available app servers when there's new request
	 */
	public void run() {
        while (true) {
        	// If there is any request
        	if (requests.size() > 0) {
				if (readyAppServers.size() > 0) {
					Integer integer = null;
					try {
						synchronized (readyAppServers) {
							integer = readyAppServers.remove();
						}
					} catch (Exception e) {
						if (integer == null)
							continue;
					}
					synchronized (integer) {
						integer.notify();
					}
				} 
			} else {
				try {
					Thread.sleep(0);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
        }
	}
	
	/*
	 * RMI call for frontier server and app server to be 
	 * registered to the master frontier server
	 */
	@Override
	public void _addServer(int type) throws RemoteException {
		synchronized (serverQueue) {
			serverQueue.add(type);
		}
	}
	
	/*
	 * Function for frontier server and app server to be 
	 * registered to the master front server
	 */
	public void addServer(int type) throws RemoteException {
		synchronized (serverQueue) {
			serverQueue.add(type);
		}
	}
	
	/*
	 * RMI call for new server to get role as well as uid of the new server
	 */
	@Override
	public ServerRole _getRoleUID() throws RemoteException {
		synchronized (serverQueue) {
			if (serverQueue.size() > 0) {
				if (serverQueue.peek() == FRONTSERVER) {
					frontServers.add(uid);
				} else {
					appServers.add(uid);
				}
				ServerRole role = new ServerRole(uid, serverQueue.poll());
				uid++;
				return role;
			} else {
				return null;
			}
		}
	}
	
	
	public class CheckScaleFront implements Runnable{
		
		private int scaleThresh = 8;
		private int scaleInc = 4;
		
		@Override
		public void run() {
			int cnt = 0;
			int serverCnt = 0;
			while (true) {
				try {
					Thread.sleep(100);
					cnt++;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				if (cnt == 10) {
					serverCnt += appServers.size();
					serverCnt /= 10;
					
					if (serverCnt > scaleThresh) {
						scaleOutFront();
						scaleThresh += scaleInc;
					}
					serverCnt = 0;
					cnt = 0;
				} else {
					serverCnt += appServers.size();
				}
			}

		}
		
		/*
		 * Start a new frontier server
		 */
		public void scaleOutFront() {
			SL.startVM();
			try {
				addServer(Server.FRONTSERVER);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		
	}

	public class CheckScaleApp implements Runnable{

		private int sampleAvg = 0;
		private int reqTime = 50; // request queue length in every 100 ms
		
		@Override
		public void run() {
			int cnt = 0;
			while (true) {
				try {
					Thread.sleep(reqTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				// Take sample in 40 times in evey 50 ms
				if (cnt < 40) {
					sampleAvg += requests.size();
					cnt++;
					continue;
				} else {
					sampleAvg /= 40;
					cnt = 0;
				}
				
				// Scale up according to thershold
				if (sampleAvg > 10) { // start 2 new VMs in this case
					startServer(2);
					
				} else if (sampleAvg > 5) { // start 1 new VM in this case
					startServer(1);
				}
				sampleAvg = 0;
			}
		}
		
		/*
		 * Start a new app server
		 */
		public void scaleOutApp() {
			SL.startVM();
			try {
				addServer(Server.APPSERVER);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		
		
		public void startServer(int scaleCnt) {
			while (scaleCnt > 0) {
				scaleOutApp();
				scaleCnt--;
			}
		}
	}
	
	/*
	 * get frontier server instances from RMI
	 */
	public FrontServerIf getFServerInstance(String addr, int port, int suid) {
	    String url = String.format("//%s:%d/%d", addr, port, suid);
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
	
	/*
	 * get app server instances from RMI
	 */
	public BServerIf getBServerInstance(String addr, int port, int suid) {
	    String url = String.format("//%s:%d/%d", addr, port, suid);
	    try {
	      return (BServerIf) Naming.lookup(url);
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
	 *  App server with uid claim to be available to get request
	 */
	@Override
	public RequestPacket _appAvailable(Integer uid) throws RemoteException {
			 
		Integer integer = uid;
		synchronized (readyAppServers) {
			readyAppServers.add(integer);
		}
		synchronized (integer) {
			try {
				integer.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		synchronized (requests) {
			if (requests.size() > 0)
				return requests.removeFirst();
			else
				return null;
		}
	}

	/*
	 * RMI for front end server to send request to front master
	 */
	@Override
	public void _sendRequest(RequestPacket rp) throws RemoteException {
		synchronized (requests) {
	    	requests.add(rp);
		}
    	
	} 
	
	/*
	 *  A separate thread to continuously get request from load balancer
	 */
	public class GetReq implements Runnable{
		@Override
		public void run() {
			
			// Drop all requests in first 5.5 seconds.
			while (true) {
				Cloud.FrontEndOps.Request r = SL.getNextRequest();
				if (r != null) {
					if (System.currentTimeMillis() - initTime < 5500) {
						SL.drop(r);
					} else break;
				}
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
	        
			// After 5.5 seconds, starts to dispatch requests to app servers
	        while (true) {
	        	Cloud.FrontEndOps.Request r = SL.getNextRequest();
	        	
	        	if (r != null) {
					RequestPacket rp = new RequestPacket(r);
					synchronized (requests) {
						requests.add(rp);
					}
	        	} else {
	        		try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
	        	}
	        }
			
		}
		
	}

	/*
	 * RMI for app server to scale in itself
	 */
	@Override
	public boolean _scaleInApp(int uid) throws RemoteException {
		// remain at least 3 app servers
		synchronized (appServers) {
			if (appServers.size() > 2) {
				for (int i = 0; i < appServers.size(); i++) {
					if (appServers.get(i).intValue() == uid) {
						appServers.remove(i);
						return true;
					}
				}
				return true;
			} else {
				return false;
			}
		}
	}

	/*
	 * RMI for frontier server to scale in itself
	 */
	@Override
	public boolean _scaleInFront(int uid) throws RemoteException {
		// TODO Auto-generated method stub
		synchronized (frontServers) {
			for (int i = 0; i < frontServers.size(); i++) {
				if (frontServers.get(i).intValue() == uid) {
					frontServers.remove(i);
					return true;
				}
			}
		}
		return false;
	}
}
